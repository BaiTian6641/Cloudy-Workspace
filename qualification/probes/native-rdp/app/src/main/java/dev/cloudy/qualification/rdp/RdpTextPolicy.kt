package dev.cloudy.qualification.rdp

internal object RdpTextPolicy {
    fun accepts(value: String): Boolean {
        if (value.isEmpty() || value.length > 256 || '\u0000' in value) return false
        var index = 0
        while (index < value.length) {
            val unit = value[index++]
            if (Character.isHighSurrogate(unit)) {
                if (index == value.length || !Character.isLowSurrogate(value[index])) return false
                index++
            } else if (Character.isLowSurrogate(unit)) return false
        }
        return true
    }
}
