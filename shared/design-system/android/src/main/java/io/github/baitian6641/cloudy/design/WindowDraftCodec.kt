package io.github.baitian6641.cloudy.design

/** Durable editing metadata is accepted only for the parent's validated window. */
internal data class WindowDraft(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val tooLong: Boolean = false,
)

internal object WindowDraftCodec {
    private const val Schema = 1
    const val MaximumUnits = 512

    fun initial(currentName: String): WindowDraft =
        if (currentName.length <= MaximumUnits) WindowDraft(currentName, currentName.length, currentName.length)
        else WindowDraft("", 0, 0, tooLong = true)

    fun save(windowId: String, draft: WindowDraft): List<Any> = listOf(
        Schema, windowId, draft.text, draft.selectionStart, draft.selectionEnd, draft.tooLong,
    )

    fun restore(windowId: String, value: Any): WindowDraft? {
        val fields = value as? List<*> ?: return null
        if (fields.size != 6 || fields[0] !is Int || fields[0] != Schema ||
            fields[1] !is String || fields[1] != windowId) return null
        val text = fields[2] as? String ?: return null
        val start = fields[3] as? Int ?: return null
        val end = fields[4] as? Int ?: return null
        val tooLong = fields[5] as? Boolean ?: return null
        if (text.length > MaximumUnits || start !in 0..text.length || end !in 0..text.length) return null
        return WindowDraft(text, start, end, tooLong)
    }
}
