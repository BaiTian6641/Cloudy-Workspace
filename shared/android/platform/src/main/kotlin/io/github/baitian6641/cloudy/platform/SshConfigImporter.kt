package io.github.baitian6641.cloudy.platform

import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProfileDraft
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner

/**
 * Declared non-secret subset of OpenSSH client configuration. It never imports User,
 * IdentityFile, Proxy*, forwards, Match or arbitrary directives; those are reported by line.
 * Parsing does not establish trust, connectivity or a usable credential.
 */
internal object SshConfigImporter {
    const val MAX_TEXT_UNITS = 65_536
    const val MAX_ENTRIES = 64
    private const val MAX_LINE_UNITS = 4_096

    class Entry internal constructor(val alias: String, val host: String, val port: Int) {
        override fun toString() = "SshConfigEntry(redacted)"
    }

    class Issue internal constructor(val line: Int, val directive: String, val reason: String) {
        override fun toString() = "SshConfigIssue($line,$directive,$reason)"
    }

    class Preview internal constructor(
        val entries: List<Entry>, val issues: List<Issue>, val truncated: Boolean,
    ) {
        override fun toString() = "SshConfigPreview(entries=${entries.size},issues=${issues.size},redacted)"
    }

    class ImportResult internal constructor(
        val created: Int, val profiles: List<ConnectionProfile>, val failure: String?,
    ) { override fun toString() = "SshConfigImportResult(created=$created,redacted)" }

    /** Creates profiles one at a time with the store's current generation; stops on a typed failure. */
    fun importProfiles(store: ConnectionProfiles, owner: WindowOwner, preview: Preview): ImportResult {
        val created = mutableListOf<ConnectionProfile>()
        var failure: String? = null
        for (entry in preview.entries) {
            val catalog = when (val loaded = store.load()) {
                is ConnectionProfilesResult.Success -> loaded.value
                is ConnectionProfilesResult.Failure -> { failure = loaded.code.name; break }
            }
            val draft = try {
                ConnectionProfileDraft.create(owner, entry.alias, ConnectionProtocol.SSH,
                    entry.host, entry.port, tags = setOf("imported"))
            } catch (_: IllegalArgumentException) { failure = "INVALID_PROFILE"; break }
            when (val result = store.create(catalog.generation, draft)) {
                is ConnectionProfilesResult.Success -> created += result.value.profile
                is ConnectionProfilesResult.Failure -> { failure = result.code.name; break }
            }
        }
        return ImportResult(created.size, created, failure)
    }

    private class Block(val aliases: MutableList<String> = mutableListOf()) {
        var host: String? = null
        var port: Int? = null
    }

    fun parse(text: String, owner: WindowOwner, existingLabels: Set<String> = emptySet()): Preview {
        val truncated = text.length > MAX_TEXT_UNITS
        val bounded = if (truncated) text.substring(0, MAX_TEXT_UNITS) else text
        val issues = mutableListOf<Issue>()
        val blocks = mutableListOf<Block>()
        var block: Block? = null
        val lines = bounded.split('\n')
        for ((index, raw) in lines.withIndex()) {
            val number = index + 1
            if (raw.length > MAX_LINE_UNITS) {
                issues += Issue(number, "line", "LINE_TOO_LONG")
                continue
            }
            val line = stripComment(raw).trim()
            if (line.isEmpty()) continue
            val split = line.indexOfFirst { it.isWhitespace() }
            if (split <= 0) {
                issues += Issue(number, line.take(32), "MISSING_VALUE")
                continue
            }
            val keyword = line.substring(0, split).lowercase()
            val value = line.substring(split).trim()
            when (keyword) {
                "host" -> {
                    val aliases = splitWords(value)
                    if (aliases.isEmpty()) {
                        issues += Issue(number, "Host", "NO_ALIAS")
                        continue
                    }
                    val next = Block()
                    for (rawAlias in aliases) {
                        val alias = unquote(rawAlias)
                        if (alias.isEmpty() || alias.length > 160 || alias.any { it in "*?!" } ||
                            alias.any { Character.isISOControl(it) } || alias != alias.trim()) {
                            issues += Issue(number, "Host", "UNSUPPORTED_PATTERN")
                        } else next.aliases += alias
                    }
                    if (next.aliases.isNotEmpty()) {
                        blocks += next
                        block = next
                    } else block = null
                }
                "hostname" -> {
                    val target = unquote(value)
                    if (block == null) issues += Issue(number, "HostName", "OUTSIDE_HOST")
                    else if (block.host == null) block.host = target
                    // OpenSSH keeps the first obtained value; later duplicates are ignored.
                }
                "port" -> {
                    val target = unquote(value).toIntOrNull()
                    if (block == null) issues += Issue(number, "Port", "OUTSIDE_HOST")
                    else if (target == null || target !in 1..65535) issues += Issue(number, "Port", "INVALID_PORT")
                    else if (block.port == null) block.port = target
                }
                "user", "identityfile" ->
                    issues += Issue(number, keyword, "NOT_IMPORTED_NON_PROFILE")
                "proxyjump", "proxycommand", "include", "match", "localforward", "remoteforward",
                "dynamicforward", "setenv", "hostkeyalias", "certificatefile", "canonicalizehostname" ->
                    issues += Issue(number, keyword, "UNSUPPORTED_DIRECTIVE")
                else -> issues += Issue(number, keyword.take(32), "UNSUPPORTED_DIRECTIVE")
            }
        }
        val seen = HashSet<String>()
        val entries = mutableListOf<Entry>()
        var limit = false
        outer@ for (current in blocks) {
            for (alias in current.aliases) {
                if (!seen.add(alias)) {
                    issues += Issue(0, alias, "DUPLICATE_ALIAS")
                    continue
                }
                if (alias in existingLabels) {
                    issues += Issue(0, alias, "EXISTS")
                    continue
                }
                val host = current.host ?: alias
                val port = current.port ?: 22
                try {
                    ConnectionProfileDraft.create(owner, alias, ConnectionProtocol.SSH, host, port)
                } catch (_: IllegalArgumentException) {
                    issues += Issue(0, alias, "INVALID_PROFILE")
                    continue
                }
                if (entries.size >= MAX_ENTRIES) { limit = true; break@outer }
                entries += Entry(alias, host, port)
            }
        }
        return Preview(entries, issues, truncated || limit)
    }

    /** Trims at an unquoted comment marker; quoted '#' stays in the value. */
    private fun stripComment(raw: String): String {
        var single = false
        var double = false
        for (index in raw.indices) {
            val char = raw[index]
            when {
                char == '\'' && !double -> single = !single
                char == '"' && !single -> double = !double
                char == '#' && !single && !double && (index == 0 || raw[index - 1].isWhitespace()) ->
                    return raw.substring(0, index)
            }
        }
        return raw
    }

    private fun splitWords(value: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        var single = false
        var double = false
        for (char in value) {
            when {
                char == '\'' && !double -> { single = !single; current.append(char) }
                char == '"' && !single -> { double = !double; current.append(char) }
                char.isWhitespace() && !single && !double -> {
                    if (current.isNotEmpty()) { words += current.toString(); current.clear() }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        return words
    }

    private fun unquote(value: String): String {
        if (value.length >= 2 && ((value.first() == '\'' && value.last() == '\'') ||
                (value.first() == '"' && value.last() == '"'))) return value.substring(1, value.length - 1)
        return value
    }
}
