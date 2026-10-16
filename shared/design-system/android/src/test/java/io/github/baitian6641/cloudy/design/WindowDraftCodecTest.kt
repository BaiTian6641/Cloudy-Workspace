package io.github.baitian6641.cloudy.design

import org.junit.Assert.*
import org.junit.Test

class WindowDraftCodecTest {
    @Test fun restoresOnlyMatchingWindowAndPreservesReverseSelection() {
        val draft = WindowDraft("draft", 5, 1, true)
        val payload = WindowDraftCodec.save("accepted", draft)
        assertEquals(draft, WindowDraftCodec.restore("accepted", payload))
        assertNull(WindowDraftCodec.restore("replacement", payload))
    }

    @Test fun rejectsOversizeAndOutOfRangeSelectionsInsteadOfClamping() {
        assertNull(WindowDraftCodec.restore("id", listOf(1, "id", "x".repeat(513), 0, 0, false)))
        assertNull(WindowDraftCodec.restore("id", listOf(1, "id", "ok", -1, 0, false)))
        assertNull(WindowDraftCodec.restore("id", listOf(1, "id", "ok", 0, 3, false)))
        assertNotNull(WindowDraftCodec.restore("id", listOf(1, "id", "x".repeat(512), 0, 512, false)))
    }

    @Test fun rejectsMalformedSchemaTypesAndArity() {
        val valid = listOf<Any>(1, "id", "ok", 0, 2, false)
        for ((index, replacement) in listOf(0 to 2, 0 to 1L, 1 to 7, 2 to false,
            3 to 0L, 4 to "2", 5 to "false")) {
            val bad = valid.toMutableList()
            bad[index] = replacement
            assertNull(WindowDraftCodec.restore("id", bad))
        }
        assertNull(WindowDraftCodec.restore("id", valid.dropLast(1)))
        assertNull(WindowDraftCodec.restore("id", valid + true))
        assertNull(WindowDraftCodec.restore("id", "not a list"))
    }

    @Test fun initializerIsBoundedAndKeepsNormalizedCommittedName() {
        assertEquals(WindowDraft("trimmed", 7, 7), WindowDraftCodec.initial("trimmed"))
        assertEquals(WindowDraft("", 0, 0, true), WindowDraftCodec.initial("x".repeat(513)))
        // A valid unfinished draft survives even if the domain normalized its
        // earlier committed name. No saved-success flag or error is serialized.
        val draft = WindowDraft("  trimmed  ", 11, 11)
        assertEquals(draft, WindowDraftCodec.restore("id", WindowDraftCodec.save("id", draft)))
    }
}
