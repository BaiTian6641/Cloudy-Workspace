package dev.cloudy.qualification.baseline

/** Strict bounded journal parser: rejects duplicate fields, relaxed JSON and fractional counters. */
internal object TerminalJson {
    fun parse(text: String): Map<String, Any?> = Reader(text).read()

    private class Reader(private val text: String) {
        private var position = 0
        fun read(): Map<String, Any?> {
            require(text.length <= 128 * 1024) { "TerminalJsonLimit" }
            val result = value(0)
            whitespace(); require(position == text.length && result is Map<*, *>) { "TerminalJsonRoot" }
            return result.entries.associate { it.key as String to it.value }
        }
        private fun value(depth: Int): Any? {
            require(depth <= 12) { "TerminalJsonDepth" }; whitespace()
            require(position < text.length) { "TerminalJsonEnd" }
            return when (text[position]) {
                '{' -> {
                    position++; whitespace(); val result = linkedMapOf<String, Any?>()
                    if (!consume('}')) {
                        do {
                            whitespace(); val key = string(); require(key !in result) { "TerminalJsonDuplicate" }
                            whitespace(); require(consume(':'))
                            result[key] = value(depth + 1); whitespace()
                        } while (consume(','))
                        require(consume('}')) { "TerminalJsonObject" }
                    }
                    result
                }
                '[' -> {
                    position++; whitespace(); val result = mutableListOf<Any?>()
                    if (!consume(']')) {
                        do { result.add(value(depth + 1)); whitespace() } while (consume(','))
                        require(consume(']')) { "TerminalJsonArray" }
                    }
                    result
                }
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> {
                    val start = position
                    if (consume('-')) require(position < text.length)
                    require(position < text.length && text[position] in '0'..'9')
                    if (consume('0')) require(position == text.length || text[position] !in '0'..'9')
                    else while (position < text.length && text[position] in '0'..'9') position++
                    text.substring(start, position).toLongOrNull()
                        ?: throw IllegalArgumentException("TerminalJsonInteger")
                }
            }
        }
        private fun literal(token: String, result: Any?): Any? {
            require(text.startsWith(token, position)); position += token.length; return result
        }
        private fun string(): String {
            require(consume('"')) { "TerminalJsonString" }
            val result = StringBuilder()
            while (position < text.length) {
                val c = text[position++]
                if (c == '"') return result.toString()
                require(c.code >= 32) { "TerminalJsonControl" }
                if (c != '\\') result.append(c) else {
                    require(position < text.length)
                    result.append(when (val escaped = text[position++]) {
                        '"', '\\', '/' -> escaped
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            require(position + 4 <= text.length)
                            val digits = text.substring(position, position + 4)
                            require(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                            position += 4; digits.toInt(16).toChar()
                        }
                        else -> throw IllegalArgumentException("TerminalJsonEscape")
                    })
                }
                require(result.length <= 4096) { "TerminalJsonStringLimit" }
            }
            throw IllegalArgumentException("TerminalJsonUnclosedString")
        }
        private fun whitespace() { while (position < text.length && text[position] in " \r\n\t") position++ }
        private fun consume(c: Char): Boolean = if (position < text.length && text[position] == c) {
            position++; true
        } else false
    }
}
