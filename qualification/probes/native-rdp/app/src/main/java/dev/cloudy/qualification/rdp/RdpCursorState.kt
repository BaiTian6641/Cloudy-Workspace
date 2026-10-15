package dev.cloudy.qualification.rdp

internal enum class RdpCursorMode { DEFAULT, CUSTOM, HIDDEN }
internal enum class RdpCursorSource(val journalName: String) {
    INITIAL("initial-estimate"), ABSOLUTE("absolute-input"), SERVER("server-position"), PREDICTED("predicted"),
}

/** Presentation only. No operation here can enqueue input, warp the host or prove delivery. */
internal class RdpCursorState {
    var width = 0; private set
    var height = 0; private set
    var x = 0; private set
    var y = 0; private set
    var mode = RdpCursorMode.DEFAULT; private set
    var source = RdpCursorSource.INITIAL; private set
    var captured = false; private set
    var revision = 0L; private set
    private var anchor: Pair<Int, Int>? = null
    private var anchorSource = RdpCursorSource.INITIAL

    fun resize(width: Int, height: Int) {
        require(width > 0 && height > 0)
        if (this.width == width && this.height == height) return
        this.width = width; this.height = height
        revision++
        // Coordinates from the previous geometry cannot seed the new framebuffer.
        anchor = null; anchorSource = RdpCursorSource.INITIAL
        restoreAnchor()
    }

    fun shape(mode: RdpCursorMode) { this.mode = mode }

    fun absolute(x: Int, y: Int) = position(x, y, RdpCursorSource.ABSOLUTE)
    fun server(x: Int, y: Int) = position(x, y, RdpCursorSource.SERVER)

    private fun position(x: Int, y: Int, source: RdpCursorSource): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        this.x = x; this.y = y; this.source = source
        anchor = x to y; anchorSource = source
        revision++
        return true
    }

    fun capture(value: Boolean) {
        if (captured == value) return
        captured = value
        revision++
        // A new ownership interval starts from known feedback, or an explicit
        // center estimate. Predictions from a retired interval never accumulate.
        restoreAnchor()
    }

    fun admittedRelative(dx: Int, dy: Int, expectedRevision: Long = revision): Boolean {
        if (expectedRevision != revision || !captured || width <= 0 || height <= 0) return false
        x = (x.toLong() + dx).coerceIn(0, width.toLong() - 1).toInt()
        y = (y.toLong() + dy).coerceIn(0, height.toLong() - 1).toInt()
        source = RdpCursorSource.PREDICTED
        return true
    }

    private fun restoreAnchor() {
        x = anchor?.first ?: (width / 2)
        y = anchor?.second ?: (height / 2)
        source = anchorSource
    }

    fun softwareVisible() = mode == RdpCursorMode.CUSTOM || (mode == RdpCursorMode.DEFAULT && captured)

    fun clear() {
        width = 0; height = 0; x = 0; y = 0; captured = false
        mode = RdpCursorMode.DEFAULT; source = RdpCursorSource.INITIAL
        anchor = null; anchorSource = RdpCursorSource.INITIAL
        revision++
    }
}
