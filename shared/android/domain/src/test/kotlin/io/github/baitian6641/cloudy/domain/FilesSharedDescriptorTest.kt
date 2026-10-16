package io.github.baitian6641.cloudy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class FilesSharedDescriptorTest {
    @Test fun exactPublicProjectionHasValueEqualityAndRedactedString() {
        val reference = FilesResourceReference.createNew()
        val item = FilesSharedDescriptor.create(reference, "共享目录 α")
        assertEquals(item, FilesSharedDescriptor.create(reference, item.label))
        assertEquals(item.hashCode(), FilesSharedDescriptor.create(reference, item.label).hashCode())
        assertFalse(item.toString().contains(item.label))
        assertFalse(item.toString().contains(reference.resourceId))
    }
    @Test fun labelRetainsExistingBoundsAndRejectsSilentCanonicalization() {
        FilesSharedDescriptor.create(FilesResourceReference.createNew(), "A".repeat(80))
        FilesSharedDescriptor.create(FilesResourceReference.createNew(), "\uD801\uDC00".repeat(80))
        listOf("", " ", " label", "label ", "a\n", "a\u2028b", "\uD800", "A".repeat(81), "x".repeat(161)).forEach { label ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                FilesSharedDescriptor.create(FilesResourceReference.createNew(), label)
            }
            assertEquals("INVALID_LABEL", error.message)
        }
    }
}
