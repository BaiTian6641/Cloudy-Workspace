package dev.cloudy.qualification.rdp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RectF
import com.freerdp.freerdpcore.services.LibFreeRDP

/** Native callback copies synchronously while GDI is valid. Canvas shares this lock. */
internal class RdpFrameBuffer {
    private var bitmap: Bitmap? = null
    private var cursor: Bitmap? = null
    private var hotX = 0
    private var hotY = 0
    private val position = RdpCursorState()
    private val arrow = Path().apply {
        moveTo(0f, 0f); lineTo(0f, 23f); lineTo(6f, 17f); lineTo(11f, 27f)
        lineTo(16f, 24f); lineTo(11f, 15f); lineTo(20f, 15f); close()
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint().apply { isFilterBitmap = true }

    @Synchronized fun resize(width: Int, height: Int) {
        require(RdpFrameGeometry.accepts(width, height))
        if (bitmap?.width == width && bitmap?.height == height) return
        val replacement = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        require(RdpFrameGeometry.acceptsAllocation(replacement.allocationByteCount))
        // Publish only a checked allocation. Old/new bitmaps may overlap in lifetime;
        // native GDI and codec buffers are additional memory. Do not force recycle a
        // bitmap that Canvas/rendering may still reference after replacement.
        bitmap = replacement
        position.resize(width, height)
    }
    @Synchronized fun update(instance: Long, x: Int, y: Int, width: Int, height: Int): Boolean {
        val target = bitmap ?: return false
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || x.toLong() + width > target.width || y.toLong() + height > target.height) return false
        return LibFreeRDP.freerdp_update_graphics(instance, target, x, y, width, height)
    }
    @Synchronized fun pointer(pixels: IntArray, width: Int, height: Int, hx: Int, hy: Int) {
        require(width in 1..256 && height in 1..256 && pixels.size == width * height && hx in 0 until width && hy in 0 until height)
        cursor = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        hotX = hx; hotY = hy
        position.shape(RdpCursorMode.CUSTOM)
    }
    @Synchronized fun defaultPointer(hidden: Boolean = false) {
        cursor = null
        position.shape(if (hidden) RdpCursorMode.HIDDEN else RdpCursorMode.DEFAULT)
    }
    @Synchronized fun move(x: Int, y: Int) { position.absolute(x, y) }
    @Synchronized fun serverPosition(x: Int, y: Int) = position.server(x, y)
    @Synchronized fun positionRevision() = position.revision
    @Synchronized fun admittedRelative(dx: Int, dy: Int, revision: Long) = position.admittedRelative(dx, dy, revision)
    @Synchronized fun capture(value: Boolean) { position.capture(value) }
    @Synchronized fun presentation() = position.source.journalName
    @Synchronized fun draw(canvas: Canvas, destination: RectF): Boolean {
        val frame = bitmap ?: return false
        canvas.drawBitmap(frame, null, destination, paint)
        if (position.softwareVisible()) {
            val scaleX = destination.width() / frame.width
            val scaleY = destination.height() / frame.height
            val save = canvas.save()
            canvas.clipRect(destination)
            val shape = cursor
            if (position.mode == RdpCursorMode.CUSTOM && shape != null) {
                val left = destination.left + (position.x - hotX) * scaleX
                val top = destination.top + (position.y - hotY) * scaleY
                canvas.drawBitmap(shape, null, RectF(left, top, left + shape.width * scaleX, top + shape.height * scaleY), paint)
            } else if (position.mode == RdpCursorMode.DEFAULT) {
                // Android hides its system arrow during capture. This local default
                // shape is presentation only; the server-hidden state never draws it.
                canvas.translate(destination.left + position.x * scaleX, destination.top + position.y * scaleY)
                canvas.scale(scaleX, scaleY)
                arrowPaint.style = Paint.Style.FILL; arrowPaint.color = Color.BLACK
                canvas.drawPath(arrow, arrowPaint)
                arrowPaint.style = Paint.Style.STROKE; arrowPaint.strokeWidth = 1.5f; arrowPaint.color = Color.WHITE
                canvas.drawPath(arrow, arrowPaint)
            }
            canvas.restoreToCount(save)
        }
        return true
    }
    @Synchronized fun clear() { bitmap = null; cursor = null; position.clear() }
}
