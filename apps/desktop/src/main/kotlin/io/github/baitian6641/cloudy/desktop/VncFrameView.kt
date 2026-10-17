package io.github.baitian6641.cloudy.desktop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.InputDevice
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import androidx.core.graphics.createBitmap

/** Native absolute-input renderer. UI explicitly binds a retained model and its attachment token.
 * No pointer capture, credential, fixture, journal or probe Activity is imported.
 * All bitmap mutation and software-Canvas consumption is confined to main, matching the sampled
 * probe's explicit channel conversion. Only complete, independently owned RGBA frames are dropped.
 */
@SuppressLint("ViewConstructor")
internal class VncFrameView(context: Context) : View(context), Choreographer.FrameCallback {
    private var model: VncSessionModel? = null
    private var attachment: Any? = null
    private var attempt: VncAttempt? = null
    private var bitmap: Bitmap? = null
    private var row: IntArray? = null
    private var queued = false
    private var drawableFrame = false
    private var sourceIdentity: Any? = null
    private val destination = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var inputWasEnabled = false
    private var pendingAccent = 0
    private val localDeadKeys = mutableSetOf<Long>()
    private val input = VncInputPolicy(object : VncInputSink {
        override fun pointer(x: Int, y: Int, mask: Int): Boolean {
            val a = attempt ?: return false
            val t = attachment ?: return false
            return model?.pointer(t, a, x, y, mask) == true
        }
        override fun key(symbol: Int, down: Boolean): Boolean {
            val a = attempt ?: return false
            val t = attachment ?: return false
            return model?.key(t, a, symbol, down) == true
        }
        override fun release() {
            inputWasEnabled = false
            pendingAccent = 0
            localDeadKeys.clear()
            val t = attachment ?: return
            model?.releaseInput(t, attempt)
        }
    })

    init {
        // A software Canvas consumes pixels before onDraw returns; never mutate a GPU-retained
        // Bitmap asynchronously, and never acknowledge a callback as a physical display pass.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.vnc_remote_surface)
    }
    fun bind(value: VncSessionModel, token: Any) {
        mainThread()
        if (model === value && attachment === token) {
            syncInput()
            return
        }
        input.release()
        clearPixels()
        model = value
        attachment = token
        schedule()
    }
    fun unbind() {
        mainThread()
        cancelFrame()
        input.release()
        model = null
        attachment = null
        clearPixels()
        invalidate()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        schedule()
    }
    override fun onDetachedFromWindow() {
        input.release()
        cancelFrame()
        clearPixels()
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) schedule() else {
            input.release()
            cancelFrame()
            clearPixels()
            invalidate()
        }
    }
    private fun schedule() {
        if (!queued && isAttachedToWindow && windowVisibility == VISIBLE && model != null && attachment != null) {
            queued = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private fun cancelFrame() {
        if (queued) Choreographer.getInstance().removeFrameCallback(this)
        queued = false
    }
    override fun doFrame(frameTimeNanos: Long) {
        mainThread()
        queued = false
        val owner = model
        val token = attachment
        if (owner == null || token == null || !isAttachedToWindow || windowVisibility != VISIBLE) return
        syncInput()
        val current = owner.currentAttempt(token)
        if (current !== attempt) {
            clearPixels()
            attempt = current
            invalidate()
        }
        val next = owner.takeFrame(token)
        if (next != null && next.attempt === current && next.identity !== sourceIdentity) {
            val frame = next.frame
            if (!VncFramePixels.valid(frame.width, frame.height, frame.rgba.size)) {
                // Invalid transport output cannot become a presented-frame or fullscreen signal.
                owner.disconnect(token)
                clearPixels()
                invalidate()
            } else {
                try {
                    var target = bitmap
                    if (target == null || target.width != frame.width || target.height != frame.height) {
                        // Retire old storage before allocation: no pair of max-sized bitmaps on resize.
                        clearBitmap()
                        target = createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                        bitmap = target
                        row = IntArray(frame.width)
                    }
                    val pixels = checkNotNull(row)
                    for (y in 0 until frame.height) {
                        VncFramePixels.row(frame.rgba, y * frame.width * 4, pixels)
                        target.setPixels(pixels, 0, frame.width, 0, y, frame.width, 1)
                    }
                    sourceIdentity = next.identity
                    drawableFrame = true
                    invalidate()
                } catch (_: RuntimeException) {
                    owner.disconnect(token)
                    clearPixels()
                    invalidate()
                } catch (_: OutOfMemoryError) {
                    // The known frame allocation failed. Do not continue with stale pixels or reconnect.
                    owner.disconnect(token)
                    clearPixels()
                    invalidate()
                }
            }
        }
        if (drawableFrame && current != null && owner.needsPresentation(token, current)) invalidate()
        schedule()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val owner = model ?: return
        val token = attachment ?: return
        val identity = attempt ?: return
        val image = bitmap ?: return
        if (!drawableFrame || width <= 0 || height <= 0 || !isAttachedToWindow || !hasWindowFocus() ||
            owner.currentAttempt(token) !== identity) return
        val scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
        val shownWidth = image.width * scale
        val shownHeight = image.height * scale
        destination.set((width - shownWidth) / 2, (height - shownHeight) / 2,
            (width + shownWidth) / 2, (height + shownHeight) / 2)
        canvas.drawBitmap(image, null, destination, paint)
        // Exact current-attempt acknowledgment follows the actual draw. Activity must separately
        // fence first-ready fullscreen by explicit Connect intent, foreground and attachment identity.
        owner.presented(token, identity)
    }
    private fun clearBitmap() {
        input.release()
        bitmap?.let { image -> image.eraseColor(Color.TRANSPARENT); image.recycle() }
        bitmap = null
        row?.fill(0)
        row = null
        drawableFrame = false
        sourceIdentity = null
        destination.setEmpty()
    }
    private fun clearPixels() {
        input.release()
        clearBitmap()
        attempt = null
    }
    private fun syncInput() {
        val t = attachment
        val a = attempt
        val enabled = t != null && a != null && model?.inputAllowed(t, a) == true
        if (inputWasEnabled && !enabled) input.release()
        inputWasEnabled = enabled
        // Enabling is a deliberate overlay action. Subsequent hover never requests focus.
    }
    private fun readyForInput(): Boolean {
        val t = attachment ?: return false
        val a = attempt ?: return false
        return drawableFrame && isAttachedToWindow && windowVisibility == VISIBLE &&
            hasWindowFocus() && isFocused && model?.inputAllowed(t, a) == true
    }
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) input.release()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) input.release()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) input.release()
    }
    private fun point(event: MotionEvent, owned: Boolean): VncRemotePoint? {
        val image = bitmap ?: return null
        return VncInputPolicy.point(event.x, event.y, width, height, image.width, image.height, owned)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val identity = VncInputPolicy.keyIdentity(event.deviceId, event.keyCode)
        // Pair accepted DOWN with the original keysym even if modifiers changed before UP.
        if (event.action == KeyEvent.ACTION_UP && input.ownsKey(identity)) return input.up(identity)
        if (event.action == KeyEvent.ACTION_UP && localDeadKeys.remove(identity)) return true
        if (event.action != KeyEvent.ACTION_DOWN || !readyForInput() || event.isMetaPressed || event.isSystem) {
            return super.dispatchKeyEvent(event)
        }
        if (input.ownsKey(identity)) {
            input.down(identity, 0) // Existing slot supplies its accepted keysym, including repeat.
            return true
        }
        val special = VncKeyMap.special(event.keyCode)
        val meta = event.metaState and (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON).inv()
        var point = event.getUnicodeChar(meta)
        if (special == null && (point and KeyCharacterMap.COMBINING_ACCENT) != 0) {
            // Physical-layout dead key: defer only its accent, not an IME text buffer.
            if (localDeadKeys.size >= 64 && !localDeadKeys.contains(identity)) {
                input.release()
                return true
            }
            if (pendingAccent != 0) {
                val old = VncInputPolicy.unicode(pendingAccent)
                if (old != null) {
                    if (!input.down(identity, old)) return true
                    input.up(identity)
                }
            }
            pendingAccent = point and KeyCharacterMap.COMBINING_ACCENT_MASK
            localDeadKeys.add(identity)
            return true
        }
        if (special == null && pendingAccent != 0 && point > 0) {
            val accent = pendingAccent
            pendingAccent = 0
            val composed = KeyCharacterMap.getDeadChar(accent, point)
            if (composed != 0) point = composed else {
                val old = VncInputPolicy.unicode(accent)
                if (old != null) {
                    if (!input.down(identity, old)) return true
                    input.up(identity)
                }
            }
        } else if (special != null) pendingAccent = 0
        val symbol = special ?: VncInputPolicy.unicode(point)
        // Committed IME text is a distinct adapter, never fabricated from physical events.
        if (symbol == null) return super.dispatchKeyEvent(event)
        input.down(identity, symbol)
        return true
    }
    override fun performClick(): Boolean {
        super.performClick()
        // Accessibility activation focuses an explicitly enabled session. A framebuffer has
        // no semantic remote target, so never fabricate a remote click at an arbitrary point.
        val token = attachment ?: return false
        val current = attempt ?: return false
        return if (model?.inputAllowed(token, current) == true) requestFocus() else false
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return super.onTouchEvent(event)
        val handled = mouse(event)
        if (handled && event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return handled
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return if (event.isFromSource(InputDevice.SOURCE_MOUSE)) mouse(event) else super.onGenericMotionEvent(event)
    }
    private fun mouse(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_CANCEL) { input.release(); return true }
        if (!readyForInput()) return false
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE) {
            return input.buttonsUp(point(event, true), event.buttonState)
        }
        val where = point(event, input.hasButtons()) ?: return false
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> {
                val changed = if (event.actionButton == 0 && action == MotionEvent.ACTION_DOWN) MotionEvent.BUTTON_PRIMARY else event.actionButton
                input.press(where, changed, event.buttonState)
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> input.move(where, event.buttonState)
            MotionEvent.ACTION_SCROLL -> input.scroll(where, event.getAxisValue(MotionEvent.AXIS_VSCROLL), event.getAxisValue(MotionEvent.AXIS_HSCROLL))
            else -> return false
        }
        return true
    }
    /** Called only by the explicit enable button, after local Compose focus is cleared. */
    fun focusRemoteInput() {
        syncInput()
        val t = attachment ?: return
        val a = attempt ?: return
        if (model?.inputAllowed(t, a) == true) requestFocus()
    }
    private fun mainThread() { check(Looper.myLooper() == Looper.getMainLooper()) }
}

/** Canonical full RGBA -> Android ARGB color conversion; never endian-dependent buffer aliasing. */
internal object VncFramePixels {
    fun valid(width: Int, height: Int, bytes: Int) = width in 1..4096 && height in 1..4096 &&
        width.toLong() * height <= 8388608L && width.toLong() * height * 4 == bytes.toLong()
    fun row(rgba: ByteArray, offset: Int, output: IntArray) {
        require(offset >= 0 && offset.toLong() + output.size.toLong() * 4 <= rgba.size)
        for (x in output.indices) {
            val index = offset + x * 4
            output[x] = ((rgba[index + 3].toInt() and 255) shl 24) or
                ((rgba[index].toInt() and 255) shl 16) or
                ((rgba[index + 1].toInt() and 255) shl 8) or
                (rgba[index + 2].toInt() and 255)
        }
    }
}
