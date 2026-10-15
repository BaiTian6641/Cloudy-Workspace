package dev.cloudy.qualification.rdp

internal data class RdpRelativeStep(val dx: Int, val dy: Int)

/** One captured event is admitted atomically; rejected samples never leave a partial batch. */
internal class RdpRelativeMotion {
    private var remainderX = 0.0
    private var remainderY = 0.0

    fun reset() { remainderX = 0.0; remainderY = 0.0 }

    fun append(samples: List<Pair<Double, Double>>): List<RdpRelativeStep>? {
        if (samples.size > MAX_SAMPLES) return null
        var fractionX = remainderX
        var fractionY = remainderY
        val result = mutableListOf<RdpRelativeStep>()
        for ((sampleX, sampleY) in samples) {
            if (!sampleX.isFinite() || !sampleY.isFinite()) return null
            val totalX = fractionX + sampleX
            val totalY = fractionY + sampleY
            if (totalX !in -MAX_TOTAL..MAX_TOTAL || totalY !in -MAX_TOTAL..MAX_TOTAL) return null
            var x = totalX.toLong()
            var y = totalY.toLong()
            fractionX = totalX - x
            fractionY = totalY - y
            while (x != 0L || y != 0L) {
                if (result.size == MAX_STEPS) return null
                val dx = x.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toInt()
                val dy = y.coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()).toInt()
                result.add(RdpRelativeStep(dx, dy))
                x -= dx; y -= dy
            }
        }
        remainderX = fractionX; remainderY = fractionY
        return result.toList()
    }

    companion object {
        const val MAX_STEPS = 128
        const val MAX_SAMPLES = 256
        private const val MAX_TOTAL = 32767.0 * MAX_STEPS
    }
}

/** Modifier prefixes are held locally until a remote action disambiguates the release chord. */
internal class RdpPendingModifiers {
    private val pending = linkedSetOf<Int>()
    fun press(code: Int) { pending.add(code) }
    fun release(code: Int) = pending.remove(code)
    fun drain(): List<Int> = pending.toList().also { pending.clear() }
    fun clear() { pending.clear() }
}
