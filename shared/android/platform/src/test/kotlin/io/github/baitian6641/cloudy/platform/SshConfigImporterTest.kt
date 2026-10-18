package io.github.baitian6641.cloudy.platform

import io.github.baitian6641.cloudy.domain.WindowOwner
import org.junit.Assert.*
import org.junit.Test

class SshConfigImporterTest {
    private fun parse(text: String, existing: Set<String> = emptySet()) =
        SshConfigImporter.parse(text, WindowOwner.CONSOLE, existing)

    @Test fun parsesHostBlocksHostnamePortAndQuotes() {
        val preview = parse("""
            # workstation file
            Host work
              HostName server.example
              Port 2222
            Host "home lab" home-alias
              HostName 10.0.0.5
        """.trimIndent())
        assertEquals(3, preview.entries.size)
        assertEquals("work", preview.entries[0].alias)
        assertEquals("server.example", preview.entries[0].host)
        assertEquals(2222, preview.entries[0].port)
        assertEquals(listOf("home lab", "home-alias"), preview.entries.drop(1).map { it.alias })
        assertEquals(listOf("10.0.0.5", "10.0.0.5"), preview.entries.drop(1).map { it.host })
        assertTrue(preview.entries.drop(1).all { it.port == 22 })
        assertFalse(preview.truncated)
        assertTrue(preview.issues.isEmpty())
    }

    @Test fun hostWithoutHostnameUsesAliasAndCommentsAreStripped() {
        val preview = parse("""
            Host plain # this is a comment
              Port 2200 # inline
            Host "hash#name"
              HostName example.org
        """.trimIndent())
        assertEquals(listOf("plain", "hash#name"), preview.entries.map { it.alias })
        assertEquals("plain", preview.entries[0].host)
        assertEquals(2200, preview.entries[0].port)
        assertEquals("example.org", preview.entries[1].host)
    }

    @Test fun wildcardExistingAndDuplicateAliasesAreReportedNotImported() {
        val preview = parse("""
            Host *
              HostName wild.example
            Host same
              HostName first.example
            Host same
              HostName second.example
            Host taken
              HostName taken.example
        """.trimIndent(), setOf("taken"))
        assertEquals(listOf("same"), preview.entries.map { it.alias })
        val reasons = preview.issues.map { it.reason }
        assertTrue(reasons.contains("UNSUPPORTED_PATTERN"))
        assertTrue(reasons.contains("DUPLICATE_ALIAS"))
        assertTrue(reasons.contains("EXISTS"))
    }

    @Test fun userAndIdentityFileNeverEnterEntriesOrIssueValues() {
        val preview = parse("""
            Host private
              HostName private.example
              User alice
              IdentityFile ~/.ssh/secret_private_key
              ProxyJump bastion
              LocalForward 8080 service:80
        """.trimIndent())
        assertEquals(1, preview.entries.size)
        assertEquals("private", preview.entries.single().alias)
        val text = preview.issues.joinToString(" ") { "${it.line} ${it.directive} ${it.reason}" }
        assertFalse(text.contains("alice"))
        assertFalse(text.contains("secret_private_key"))
        assertTrue(preview.issues.any { it.directive == "user" && it.reason == "NOT_IMPORTED_NON_PROFILE" })
        assertTrue(preview.issues.any { it.directive == "identityfile" })
        assertTrue(preview.issues.any { it.directive == "proxyjump" })
    }

    @Test fun malformedPortHostAndOutOfHostDirectivesDoNotBlockValidEntries() {
        val preview = parse("""
            HostName ignored.example
            Host badport
              HostName good.example
              Port 70000
            Host badhost
              HostName "https://not-a-host"
            Host good
              HostName ok.example
              Port 22
        """.trimIndent())
        assertEquals(listOf("badport", "good"), preview.entries.map { it.alias })
        assertEquals(22, preview.entries[0].port)
        assertTrue(preview.issues.any { it.directive == "HostName" && it.reason == "OUTSIDE_HOST" })
        assertTrue(preview.issues.any { it.reason == "INVALID_PORT" })
        assertTrue(preview.issues.any { it.reason == "INVALID_PROFILE" })
    }

    @Test fun entryLimitAndOversizeInputAreReported() {
        val many = buildString {
            for (index in 1..70) append("Host h$index\n  HostName 10.0.0.$index\n")
        }
        val preview = parse(many)
        assertEquals(SshConfigImporter.MAX_ENTRIES, preview.entries.size)
        assertTrue(preview.truncated)
        val oversize = parse("Host h\n  HostName example.org\n" + "#".repeat(SshConfigImporter.MAX_TEXT_UNITS))
        assertTrue(oversize.truncated)
    }
}
