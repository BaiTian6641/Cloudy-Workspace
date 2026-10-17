package io.github.baitian6641.cloudy.desktop

import kotlin.math.abs

/** Accepted operations are Java admission only, never host receipt or delivered-input proof. */
internal interface VncInputSink {
    fun pointer(x: Int, y: Int, mask: Int): Boolean
    fun key(symbol: Int, down: Boolean): Boolean
    // Caller must release all accepted remote held inputs on this original scope, or stop
    // that original owner if release cannot be admitted. This is not a host ACK callback.
    fun release()
}
internal data class VncRemotePoint(val x: Int, val y: Int)

/** Pure desktop input policy; all calls are main-thread confined by the native View. */
internal class VncInputPolicy(private val sink: VncInputSink) {
    private val keys = LinkedHashMap<Long, Int>()
    private var buttons = 0 // Android bit order: primary1, secondary2, tertiary4.
    private var vertical = 0f
    private var horizontal = 0f
    private var x = 0
    private var y = 0
    fun hasButtons() = buttons != 0
    fun ownsKey(identity: Long) = keys.containsKey(identity)
    private fun mask() = (buttons and 1) or ((buttons and 2) shl 1) or ((buttons and 4) shr 1)
    private fun pointer(mask: Int): Boolean {
        if (sink.pointer(x, y, mask)) return true
        release()
        return false
    }
    fun move(point: VncRemotePoint, physical: Int): Boolean {
        x = point.x; y = point.y
        buttons = buttons and physical and 7 // Motion never acquires an already-held button.
        return pointer(mask())
    }
    fun press(point: VncRemotePoint, actionButton: Int, physical: Int): Boolean {
        x = point.x; y = point.y
        val before = buttons
        buttons = (buttons and physical and 7) or (actionButton and physical and 7)
        if (buttons == before) return true // Suppress touch/generic duplicate notification.
        return pointer(mask())
    }
    fun buttonsUp(point: VncRemotePoint?, physical: Int): Boolean {
        if (point != null) { x = point.x; y = point.y }
        val before = buttons
        buttons = buttons and physical and 7
        return if (before != buttons) pointer(mask()) else before != 0
    }
    fun scroll(point: VncRemotePoint, v: Float, h: Float): Boolean {
        if (!v.isFinite() || !h.isFinite() || abs(v) > 16 || abs(h) > 16) return false
        x = point.x; y = point.y
        val nv = vertical + v; val nh = horizontal + h
        val vs = nv.toInt(); val hs = nh.toInt()
        vertical = nv - vs; horizontal = nh - hs
        val held = mask()
        repeat(abs(vs)) { if (!pointer(held or (if (vs > 0) 8 else 16)) || !pointer(held)) return false }
        repeat(abs(hs)) { if (!pointer(held or (if (hs > 0) 64 else 32)) || !pointer(held)) return false }
        return true
    }
    fun down(identity: Long, symbol: Int): Boolean {
        val existing = keys[identity]
        if (existing == null && keys.size >= 64) { release(); return false }
        if (!sink.key(existing ?: symbol, true)) { release(); return false }
        keys[identity] = existing ?: symbol
        return true
    }
    fun up(identity: Long): Boolean {
        val symbol = keys.remove(identity) ?: return false
        // RFB has no device identity: another physical key may still own this keysym.
        if (keys.values.none { it == symbol } && !sink.key(symbol, false)) release()
        return true // This original owned UP may never become a second local action.
    }
    fun release() {
        keys.clear(); buttons = 0; vertical = 0f; horizontal = 0f
        sink.release()
    }
    companion object {
        fun point(px: Float, py: Float, viewWidth: Int, viewHeight: Int,
            remoteWidth: Int, remoteHeight: Int, clampOwnedDrag: Boolean): VncRemotePoint? {
            if (!px.isFinite() || !py.isFinite() || viewWidth <= 0 || viewHeight <= 0 ||
                remoteWidth !in 1..4096 || remoteHeight !in 1..4096) return null
            val scale = minOf(viewWidth.toFloat() / remoteWidth, viewHeight.toFloat() / remoteHeight)
            val w = remoteWidth * scale; val h = remoteHeight * scale
            val left = (viewWidth - w) / 2; val top = (viewHeight - h) / 2
            if (!clampOwnedDrag && (px < left || py < top || px >= left + w || py >= top + h)) return null
            return VncRemotePoint(((px - left) / scale).toInt().coerceIn(0, remoteWidth - 1),
                ((py - top) / scale).toInt().coerceIn(0, remoteHeight - 1))
        }
        fun unicode(point: Int): Int? = if (point < 0x20 || point > 0x10ffff ||
            point in 0xd800..0xdfff || point in 0x7f..0x9f) null else
            if (point <= 0xff) point else 0x01000000 or point
        fun keyIdentity(device: Int, code: Int) = (device.toLong() shl 32) or (code.toLong() and 0xffffffffL)
    }
}
