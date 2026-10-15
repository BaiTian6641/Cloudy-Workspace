package dev.cloudy.qualification.rdp

/** Explicit presses acquire ownership; physical buttonState alone never rearms a drag. */
internal class RdpPointerButtons {
    private var owned = 0

    fun press(button: Int, physical: Int): List<Pair<Int, Boolean>> {
        val fresh = button and physical and SUPPORTED and owned.inv()
        owned = owned or fresh
        return transitions(fresh, true)
    }

    fun release(physical: Int): List<Pair<Int, Boolean>> {
        val released = owned and physical.inv()
        owned = owned and physical
        return transitions(released, false)
    }

    fun clear() { owned = 0 }
    fun hasOwnedButtons() = owned != 0

    private fun transitions(bits: Int, down: Boolean): List<Pair<Int, Boolean>> = BUTTONS
        .filter { bits and it != 0 }.map { it to down }

    companion object {
        const val PRIMARY = 1
        const val SECONDARY = 2
        const val TERTIARY = 4
        private const val SUPPORTED = PRIMARY or SECONDARY or TERTIARY
        private val BUTTONS = listOf(PRIMARY, SECONDARY, TERTIARY)
    }
}
