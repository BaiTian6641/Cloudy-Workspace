package dev.cloudy.qualification.baseline

/** Bounds recursive JSONObject parsing of the already byte-limited private journal. */
internal object SshJournalGuard {
    const val MAX_DEPTH = 16
    private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    private const val WHITESPACE = " \t\r\n"

    fun check(text: String) {
        var depth = 0
        var position = 0
        while (position < text.length) {
            when (text[position]) {
                in WHITESPACE -> position++
                '{', '[' -> {
                    depth++
                    require(depth <= MAX_DEPTH) { "SshJournalNesting" }
                    position++
                }
                '}', ']' -> {
                    depth--
                    require(depth >= 0) { "SshJournalNesting" }
                    position++
                }
                ':', ',' -> position++
                '"' -> { position = stringEnd(text, position); separator(text, position) }
                else -> {
                    val begin = position
                    // Quotes are not a delimiter here: an unquoted token cannot hide one in its body.
                    while (position < text.length && text[position] !in "$WHITESPACE{}[]:,") position++
                    val token = text.substring(begin, position)
                    require(token == "true" || token == "false" || token == "null" || number.matches(token)) {
                        "SshJournalLexicalForm"
                    }
                    separator(text, position)
                }
            }
        }
        require(depth == 0) { "SshJournalNesting" }
        // Iterative lexical admission only. JSONObject and model validation still own object grammar/types.
    }

    private fun stringEnd(text: String, start: Int): Int {
        var position = start + 1
        while (position < text.length) {
            val character = text[position++]
            if (character == '"') return position
            require(character.code >= 32) { "SshJournalString" }
            if (character == '\\') {
                require(position < text.length) { "SshJournalString" }
                val escape = text[position++]
                if (escape == 'u') {
                    require(position + 4 <= text.length && text.substring(position, position + 4)
                        .all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "SshJournalString" }
                    position += 4
                } else require(escape in "\"\\/bfnrt") { "SshJournalString" }
            }
        }
        throw IllegalArgumentException("SshJournalString")
    }

    private fun separator(text: String, position: Int) {
        require(position == text.length || text[position] in "$WHITESPACE:,]}") { "SshJournalLexicalForm" }
    }
}
