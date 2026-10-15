package dev.cloudy.qualification.rdp

/** Single ARGB8888 bitmap allocation budget, not a native/codec/process memory limit. */
internal object RdpFrameGeometry {
    const val MAX_EDGE = 4096
    const val BITMAP_BUDGET_BYTES = 20L * 1024 * 1024
    private const val BYTES_PER_PIXEL = 4L

    fun accepts(width: Int, height: Int): Boolean {
        if (width !in 1..MAX_EDGE || height !in 1..MAX_EDGE) return false
        return width.toLong() * height.toLong() <= BITMAP_BUDGET_BYTES / BYTES_PER_PIXEL
    }

    fun acceptsAllocation(bytes: Int): Boolean = bytes > 0 && bytes.toLong() <= BITMAP_BUDGET_BYTES
}
