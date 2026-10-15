package dev.cloudy.qualification.rdp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.freerdp.freerdpcore.services.LibFreeRDP

/** Native callback copies synchronously while GDI is valid. Canvas shares this lock. */
internal class RdpFrameBuffer {
    private var bitmap: Bitmap? = null
    private var cursor: Bitmap? = null
    private var hotX = 0
    private var hotY = 0
    private var mouseX = 0
    private var mouseY = 0
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
    }
    @Synchronized fun defaultPointer() { cursor = null }
    @Synchronized fun move(x: Int, y: Int) { mouseX = x; mouseY = y }
    @Synchronized fun draw(canvas: Canvas, destination: RectF): Boolean {
        val frame = bitmap ?: return false
        canvas.drawBitmap(frame, null, destination, paint)
        cursor?.let {
            val scaleX = destination.width() / frame.width
            val scaleY = destination.height() / frame.height
            val left = destination.left + (mouseX - hotX) * scaleX
            val top = destination.top + (mouseY - hotY) * scaleY
            val save = canvas.save()
            canvas.clipRect(destination)
            canvas.drawBitmap(it, null, RectF(left, top, left + it.width * scaleX, top + it.height * scaleY), paint)
            canvas.restoreToCount(save)
        }
        return true
    }
    @Synchronized fun clear() { bitmap = null; defaultPointer() }
}
