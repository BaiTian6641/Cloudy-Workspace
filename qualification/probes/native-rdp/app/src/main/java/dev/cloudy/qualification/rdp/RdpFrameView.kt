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
    private var mouseButtons = 0
    private var touchHeld = false
    private var lastX = 0
    private var lastY = 0

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
        val pointerChanged = value.connected != snapshot.connected || value.cursorMode != snapshot.cursorMode
        snapshot = value
        if (lostConnection) releaseInput()
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!hasFocus() || !snapshot.connected) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        // Complete remote releases even if Meta was pressed after the remote key-down.
        if (event.action == KeyEvent.ACTION_UP && forwardedKeys.remove(code)) {
            session.key(code, false)
            return true
        }
        // Meta chords belong to the OS; a keyCode-only map cannot enforce this guard.
        if (event.isMetaPressed) return super.dispatchKeyEvent(event)
        // Tab without a held remote modifier remains native keyboard focus traversal.
        if (code == KeyEvent.KEYCODE_TAB && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed &&
            code !in forwardedKeys) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (session.key(code, true)) {
                forwardedKeys.add(code)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
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
        if (!snapshot.connected) return super.onTouchEvent(event)
        if (!hasWindowFocus()) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                releaseInput()
                return true
            }
            return false
        }
        val mouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
        if (!position(event, mouseButtons != 0 || touchHeld)) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                parent?.requestDisallowInterceptTouchEvent(true)
                if (mouse) syncMouseButtons(event.buttonState) else {
                    touchHeld = true
                    session.pointer(lastX, lastY, BUTTON1 or DOWN)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mouse) syncMouseButtons(event.buttonState)
                session.pointer(lastX, lastY, MOVE)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (mouse) syncMouseButtons(event.buttonState) else if (touchHeld) {
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
        if (!hasWindowFocus() || !snapshot.connected || !event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) ||
            !position(event, mouseButtons != 0)) {
            return super.onGenericMotionEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> {
                session.pointer(lastX, lastY, MOVE)
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                requestFocus()
                syncMouseButtons(event.buttonState)
                return true
            }
            MotionEvent.ACTION_SCROLL -> {
                val steps = (event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120).roundToInt().coerceIn(-255, 255)
                if (steps == 0) return super.onGenericMotionEvent(event)
                val encoded = if (steps < 0) NEGATIVE or (steps and 0xff) else steps
                session.pointer(lastX, lastY, WHEEL or encoded)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun syncMouseButtons(next: Int) {
        for ((androidButton, remoteButton) in BUTTONS) {
            if ((mouseButtons xor next) and androidButton != 0) {
                session.pointer(lastX, lastY, remoteButton or if (next and androidButton != 0) DOWN else 0)
            }
        }
        mouseButtons = next
    }

    override fun performClick(): Boolean {
        super.performClick()
        requestFocus()
        return true
    }

    fun releaseInput() {
        session.releaseInput()
        forwardedKeys.clear()
        mouseButtons = 0
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
        private const val MOVE = 0x0800
        private const val DOWN = 0x8000
        private const val BUTTON1 = 0x1000
        private const val WHEEL = 0x0200
        private const val NEGATIVE = 0x0100
        private val BUTTONS = arrayOf(MotionEvent.BUTTON_PRIMARY to BUTTON1,
            MotionEvent.BUTTON_SECONDARY to 0x2000, MotionEvent.BUTTON_TERTIARY to 0x4000)
    }
}
