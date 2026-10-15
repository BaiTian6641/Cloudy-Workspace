package dev.cloudy.qualification.baseline

import org.junit.Assert.assertThrows
import org.junit.Test

class SshJournalGuardTest {
    @Test fun ordinaryJournalNestingAndExactDepthLimitAreAllowed() {
        SshJournalGuard.check("""{"attempts":[{"details":{},"events":[{"status":"running"}]}],"trustedHosts":{}}""")
        SshJournalGuard.check("[".repeat(SshJournalGuard.MAX_DEPTH) + "0" + "]".repeat(SshJournalGuard.MAX_DEPTH))
    }

    @Test fun recursiveArrayAndObjectInputsAreRejectedBeforeFrameworkParsing() {
        assertThrows(IllegalArgumentException::class.java) {
            SshJournalGuard.check("[".repeat(10_000) + "0" + "]".repeat(10_000))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SshJournalGuard.check("{\"nested\":".repeat(SshJournalGuard.MAX_DEPTH + 1) + "0" +
                "}".repeat(SshJournalGuard.MAX_DEPTH + 1))
        }
    }

    @Test fun bracesAndEscapedQuotesInsideStringsDoNotChangeDepth() {
        SshJournalGuard.check("""{"message":"[{}] \"quoted\" \\ \u0022 [[]] \u005c","attempts":[]}""")
        SshJournalGuard.check("{\"message\":\"" + "[{}]".repeat(10_000) + "\"}")
    }

    @Test fun escapedBackslashDoesNotHideAClosingQuoteOrFollowingNestedValue() {
        // The string contains one escaped backslash; its following quote closes the string.
        assertThrows(IllegalArgumentException::class.java) {
            SshJournalGuard.check("""{"text":"\\","nested":""" +
                "[".repeat(SshJournalGuard.MAX_DEPTH) + "0" + "]".repeat(SshJournalGuard.MAX_DEPTH) + "}")
        }
    }

    @Test fun truncatedStringsAndUnbalancedStructureAreRejected() {
        listOf("{", "}", "[0", "{\"x\":\"unfinished", "{\"x\":\"escape\\").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { SshJournalGuard.check(text) }
        }
    }

    @Test fun lenientParserCommentsAndUnquotedNamesCannotHideDeepStructure() {
        val nested = "[".repeat(100) + "0" + "]".repeat(100)
        listOf("/* \" */$nested/* \" */", "{unquoted\":$nested,other\":0}",
            "{'text':'\"',$nested,'end':'\"'}", "# \"\n$nested\n# \"").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { SshJournalGuard.check(text) }
        }
    }

    @Test fun scalarTokensMustUseCanonicalJsonLexicalForms() {
        listOf("0", "-1", "1.25", "1e2", "-0.5E-3", "true", "false", "null").forEach {
            SshJournalGuard.check("{\"value\":$it}")
        }
        listOf("+1", "01", "NaN", "Infinity", "0x10", "unquoted", "tru\"quoted\"", "1.0junk").forEach {
            assertThrows(IllegalArgumentException::class.java) { SshJournalGuard.check("{\"value\":$it}") }
        }
    }
}
