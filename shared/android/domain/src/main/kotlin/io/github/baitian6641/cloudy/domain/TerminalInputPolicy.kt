package io.github.baitian6641.cloudy.domain

/** Local secondary-click meaning while a terminal surface owns the pointer. */
enum class TerminalRightClick(val wireId: String) {
    PASTE("paste"),
    CONTEXT_MENU("context-menu"),
}

/**
 * The 2026-10-16 amended terminal interaction contract as data: MobaXterm-style defaults with a
 * persisted per-app right-click toggle. The terminal adapter consumes this model when the
 * interactive terminal lands; the toggle itself persists through the owning app's settings store.
 * This is presentation/input policy only: it carries no session, trust or credential authority.
 */
data class TerminalInputPolicy(
    val copyOnSelect: Boolean = true,
    val rightClick: TerminalRightClick = TerminalRightClick.PASTE,
    val shiftInsertPaste: Boolean = true,
    val ctrlWheelZoom: Boolean = true,
) {
    companion object {
        /** Contract defaults; the only user-configurable field today is [rightClick]. */
        val DEFAULT = TerminalInputPolicy()

        fun forRightClick(action: TerminalRightClick): TerminalInputPolicy = DEFAULT.copy(rightClick = action)
    }
}
