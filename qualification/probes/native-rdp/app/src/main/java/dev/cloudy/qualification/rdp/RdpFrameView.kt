package dev.cloudy.qualification.rdp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

/** Letterboxed real native framebuffer; coordinate conversion uses the drawn bounds. */
// Programmatic owner injection is required; XML tools must never allocate a native session.
@SuppressLint("ViewConstructor")
class RdpFrameView(context: Context, private val session: RdpSession) : View(context) {
    private var snapshot = session.snapshot()
    private val destination = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(219, 232, 236)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
    }
    private val forwardedKeys = mutableSetOf<Int>()
    private val mouseButtons = RdpPointerButtons()
    private var touchHeld = false
    private var lastX = 0
    private var lastY = 0
    private val relativeMotion = RdpRelativeMotion()
    private val pendingModifiers = RdpPendingModifiers()
    private var captureRequested = false
    private var captureConfirmed = false
    private var localReleaseKeyDown = false
    private var captureNotice = R.string.relative_disconnected
    var captureStateListener: (() -> Unit)? = null
    val isCaptureRequested: Boolean get() = captureRequested
    val isCaptureConfirmed: Boolean get() = captureConfirmed
    val captureStatusResource: Int get() = captureNotice
    private val captureDeadline = Runnable {
        if (captureRequested && !captureConfirmed) {
            captureRequested = false
            releaseRemoteInput()
            captureNotice = R.string.relative_timeout
            captureStateListener?.invoke()
        }
    }

    init {
        // Bitmap updates share the Canvas lock; software drawing consumes pixels now,
        // rather than retaining a hardware display-list reference after that lock ends.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        contentDescription = context.getString(R.string.frame_description)
    }

    fun update(value: RdpSnapshot) {
        val lostConnection = !value.connected && snapshot.connected
        val captureAvailabilityChanged = value.connected != snapshot.connected || value.relativeSupported != snapshot.relativeSupported
        val pointerChanged = value.connected != snapshot.connected || value.cursorMode != snapshot.cursorMode
        snapshot = value
        if (lostConnection || !value.relativeSupported && (captureRequested || captureConfirmed)) releaseInput()
        if (captureAvailabilityChanged && !captureRequested && !captureConfirmed) {
            captureNotice = when {
                !value.connected -> R.string.relative_disconnected
                !value.relativeSupported -> R.string.relative_unsupported
                else -> R.string.relative_ready
            }
            captureStateListener?.invoke()
        }
        if (pointerChanged) {
            // Public setPointerIcon requests framework re-resolution even for a stationary
            // mouse; onResolvePointerIcon still limits hiding to the remote frame bounds.
            pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(16, 28, 34))
        val labelHeight = paint.textSize * 2
        destination.setEmpty()
        val availableHeight = height - labelHeight
        if (snapshot.width > 0 && snapshot.height > 0 && width > 0 && availableHeight > 0) {
            val scale = min(width.toFloat() / snapshot.width, availableHeight / snapshot.height)
            val drawWidth = snapshot.width * scale
            val drawHeight = snapshot.height * scale
            destination.set((width - drawWidth) / 2, labelHeight + (availableHeight - drawHeight) / 2,
                (width + drawWidth) / 2, labelHeight + (availableHeight + drawHeight) / 2)
        }
        val drawn = !destination.isEmpty && session.drawFrame(canvas, destination)
        canvas.drawText(context.getString(if (hasFocus()) R.string.frame_focused else R.string.frame_unfocused),
            8f, paint.textSize + 4f, paint)
        if (!drawn) canvas.drawText(context.getString(R.string.frame_empty), 8f, labelHeight + paint.textSize, paint)
        if (hasFocus()) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * resources.displayMetrics.density
            canvas.drawRect(1f, 1f, width - 1f, height - 1f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    /** Called by the Activity before native fields; only a remote input owner reserves this chord. */
    fun handleLocalReleaseChord(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_R) return false
        if (event.action == KeyEvent.ACTION_UP && localReleaseKeyDown) {
            localReleaseKeyDown = false
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && event.isAltPressed && event.isShiftPressed &&
            hasWindowFocus() && (hasFocus() || captureRequested || captureConfirmed)) {
            releaseInput()
            localReleaseKeyDown = true
            return true
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleLocalReleaseChord(event)) return true
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP && forwardedKeys.remove(code)) {
            session.key(code, false)
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && pendingModifiers.release(code)) {
            // A modifier-only tap still reaches the remote application. The reserved
            // release chord instead clears its deferred prefix without sending it.
            if (hasFocus() && hasWindowFocus() && snapshot.connected && !event.isCanceled) {
                session.key(code, true)
                session.key(code, false)
            }
            return true
        }
        if (!hasFocus() || !hasWindowFocus() || !snapshot.connected) return super.dispatchKeyEvent(event)
        if (event.isMetaPressed) return super.dispatchKeyEvent(event)
        if (code == KeyEvent.KEYCODE_TAB && !event.isCtrlPressed && !event.isAltPressed &&
            code !in forwardedKeys) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (code in RELEASE_MODIFIERS && code !in forwardedKeys) {
                pendingModifiers.press(code)
                return true
            }
            flushModifiers()
            if (session.key(code, true)) {
                forwardedKeys.add(code)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun flushModifiers() {
        for (code in pendingModifiers.drain()) {
            if (session.key(code, true)) forwardedKeys.add(code)
        }
    }

    fun requestRelativeCapture() {
        if (!snapshot.connected) { captureNotice = R.string.relative_disconnected; captureStateListener?.invoke(); return }
        if (!snapshot.relativeSupported) { captureNotice = R.string.relative_unsupported; captureStateListener?.invoke(); return }
        if (!hasWindowFocus() || !requestFocus()) {
            captureNotice = R.string.relative_focus_required; captureStateListener?.invoke(); return
        }
        releaseRemoteInput()
        captureRequested = true
        captureNotice = R.string.relative_requesting
        captureStateListener?.invoke()
        requestPointerCapture()
        removeCallbacks(captureDeadline)
        postDelayed(captureDeadline, 3000)
    }

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        super.onPointerCaptureChange(hasCapture)
        removeCallbacks(captureDeadline)
        releaseRemoteInput()
        // The system callback is authoritative. A grant arriving after cancellation
        // must be released, rather than silently reacquiring remote ownership.
        if (hasCapture && captureRequested && snapshot.connected && snapshot.relativeSupported && hasWindowFocus() && hasFocus()) {
            captureConfirmed = true
            captureRequested = false
            captureNotice = R.string.relative_captured
        } else {
            captureConfirmed = false
            captureRequested = false
            if (hasCapture) releasePointerCapture()
            captureNotice = if (snapshot.connected && snapshot.relativeSupported) R.string.relative_released
                else if (snapshot.connected) R.string.relative_unsupported else R.string.relative_disconnected
        }
        captureStateListener?.invoke()
        invalidate()
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) { releaseInput(); return true }
        if (!captureConfirmed || !hasPointerCapture() || !hasFocus() || !hasWindowFocus() ||
            !snapshot.connected || !snapshot.relativeSupported) { releaseInput(); return false }
        // Captured MOVE getX/getY are deltas, not framebuffer coordinates. Button
        // and wheel callbacks can share coordinates with adjacent motion callbacks;
        // they own only their transition and must never replay those deltas.
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val samples = ArrayList<Pair<Double, Double>>()
            if (event.historySize >= RdpRelativeMotion.MAX_SAMPLES) {
                releaseInput(); session.disconnect("relative-motion-limit"); return true
            }
            for (index in 0 until event.historySize) samples.add(event.getHistoricalX(index).toDouble() to event.getHistoricalY(index).toDouble())
            samples.add(event.x.toDouble() to event.y.toDouble())
            val steps = relativeMotion.append(samples)
            if (steps == null) {
                releaseInput(); session.disconnect("relative-motion-limit"); return true
            }
            if (steps.isNotEmpty()) {
                flushModifiers()
                if (!session.relativeMotion(steps)) { releaseInput(); return true }
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                flushModifiers()
                sendButtonTransitions(mouseButtons.release(event.buttonState), relative = true)
                sendButtonTransitions(mouseButtons.press(MotionEvent.BUTTON_PRIMARY, event.buttonState), relative = true)
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                flushModifiers()
                sendButtonTransitions(mouseButtons.release(event.buttonState), relative = true)
                sendButtonTransitions(mouseButtons.press(event.actionButton, event.buttonState), relative = true)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE ->
                sendButtonTransitions(mouseButtons.release(event.buttonState), relative = true)
            MotionEvent.ACTION_SCROLL -> {
                val flags = wheelFlags(event)
                if (flags != null) { flushModifiers(); session.capturedWheel(flags) }
            }
        }
        return true
    }

    private fun position(event: MotionEvent, allowOutside: Boolean = false): Boolean {
        if (destination.isEmpty || snapshot.width <= 0 || snapshot.height <= 0) return false
        if (!allowOutside && !destination.contains(event.x, event.y)) return false
        lastX = (((event.x - destination.left) / destination.width()) * snapshot.width).toInt().coerceIn(0, snapshot.width - 1)
        lastY = (((event.y - destination.top) / destination.height()) * snapshot.height).toInt().coerceIn(0, snapshot.height - 1)
        return true
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon {
        val remoteCursor = snapshot.cursorMode == "custom" || snapshot.cursorMode == "hidden"
        val overRemote = destination.contains(event.getX(pointerIndex), event.getY(pointerIndex))
        val type = if (snapshot.connected && remoteCursor && overRemote) PointerIcon.TYPE_NULL else PointerIcon.TYPE_ARROW
        return PointerIcon.getSystemIcon(context, type)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            releaseInput()
            return true
        }
        if (captureConfirmed || hasPointerCapture()) return false
        if (!snapshot.connected) return super.onTouchEvent(event)
        if (!hasWindowFocus()) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                releaseInput()
                return true
            }
            return false
        }
        val mouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
        if (!position(event, mouseButtons.hasOwnedButtons() || touchHeld)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                flushModifiers()
                parent?.requestDisallowInterceptTouchEvent(true)
                if (mouse) {
                    releaseMouseButtons(event.buttonState)
                    // Generic BUTTON_PRESS identifies additional chord buttons. The
                    // initial touch-stream DOWN owns only the primary button.
                    pressMouseButton(MotionEvent.BUTTON_PRIMARY, event.buttonState)
                } else {
                    touchHeld = true
                    session.pointer(lastX, lastY, BUTTON1 or DOWN)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                flushModifiers()
                if (mouse) releaseMouseButtons(event.buttonState)
                session.pointer(lastX, lastY, MOVE)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (mouse) releaseMouseButtons(event.buttonState) else if (touchHeld) {
                    session.pointer(lastX, lastY, BUTTON1)
                    touchHeld = false
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL ||
            (!hasWindowFocus() && event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE)) {
            releaseInput()
            return true
        }
        if (captureConfirmed || hasPointerCapture()) return false
        if (!hasWindowFocus() || !snapshot.connected || !event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) ||
            !position(event, mouseButtons.hasOwnedButtons())) {
            return super.onGenericMotionEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> {
                flushModifiers()
                session.pointer(lastX, lastY, MOVE)
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                requestFocus()
                flushModifiers()
                releaseMouseButtons(event.buttonState)
                // actionButton distinguishes a fresh secondary press from an old
                // primary that remained physically held while focus was elsewhere.
                pressMouseButton(event.actionButton, event.buttonState)
                return true
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                releaseMouseButtons(event.buttonState)
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val flags = wheelFlags(event) ?: return super.onGenericMotionEvent(event)
                flushModifiers()
                session.pointer(lastX, lastY, flags)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun pressMouseButton(button: Int, physical: Int) {
        sendButtonTransitions(mouseButtons.press(button, physical))
    }

    private fun releaseMouseButtons(physical: Int) {
        sendButtonTransitions(mouseButtons.release(physical))
    }

    private fun wheelFlags(event: MotionEvent): Int? {
        val value = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (!value.isFinite()) return null
        val steps = (value * 120).roundToInt().coerceIn(-255, 255)
        if (steps == 0) return null
        return WHEEL or if (steps < 0) NEGATIVE or (steps and 0xff) else steps
    }

    private fun sendButtonTransitions(changes: List<Pair<Int, Boolean>>, relative: Boolean = false) {
        for ((androidButton, down) in changes) {
            val remoteButton = when (androidButton) {
                MotionEvent.BUTTON_PRIMARY -> BUTTON1
                MotionEvent.BUTTON_SECONDARY -> 0x2000
                MotionEvent.BUTTON_TERTIARY -> 0x4000
                else -> continue
            }
            val flags = remoteButton or if (down) DOWN else 0
            if (relative) {
                if (!session.relativeButton(flags)) { releaseInput(); return }
            } else session.pointer(lastX, lastY, flags)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        requestFocus()
        return true
    }

    fun releaseInput() {
        removeCallbacks(captureDeadline)
        captureRequested = false
        captureConfirmed = false
        if (hasPointerCapture()) releasePointerCapture()
        releaseRemoteInput()
        captureNotice = if (snapshot.connected && snapshot.relativeSupported) R.string.relative_released
            else if (snapshot.connected) R.string.relative_unsupported else R.string.relative_disconnected
        captureStateListener?.invoke()
    }

    private fun releaseRemoteInput() {
        session.releaseInput()
        relativeMotion.reset()
        pendingModifiers.clear()
        forwardedKeys.clear()
        mouseButtons.clear()
        touchHeld = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        if (!gainFocus) releaseInput()
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        releaseInput()
        super.onDetachedFromWindow()
    }

    companion object {
        private val RELEASE_MODIFIERS = setOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT)
        private const val MOVE = 0x0800
        private const val DOWN = 0x8000
        private const val BUTTON1 = 0x1000
        private const val WHEEL = 0x0200
        private const val NEGATIVE = 0x0100
    }
}
