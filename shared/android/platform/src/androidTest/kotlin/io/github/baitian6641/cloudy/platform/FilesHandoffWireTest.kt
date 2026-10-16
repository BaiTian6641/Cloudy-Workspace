package io.github.baitian6641.cloudy.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.FilesResourceReference
import io.github.baitian6641.cloudy.domain.FilesSharedDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android JsonReader/Writer boundaries, not an IPC/authorization test. */
@RunWith(AndroidJUnit4::class)
class FilesHandoffWireTest {
    private fun item(index: Int, label: String = "Folder"): FilesSharedDescriptor = FilesSharedDescriptor.create(
        FilesResourceReference.restore("10000000-0000-4000-8000-000000000000", "20000000-0000-4000-8000-000000000000",
            "30000000-0000-4000-8000-000000000000", "40000000-0000-4000-8000-${index.toString().padStart(12, '0')}"), label)
    private fun failDiscovery(document: String): FilesHandoffWireException = assertThrows(FilesHandoffWireException::class.java) {
        FilesHandoffWire.decodeDiscovery(document.toByteArray())
    }
    private fun failSubmit(document: String): FilesHandoffWireException = assertThrows(FilesHandoffWireException::class.java) {
        FilesHandoffWire.decodeSubmit(document.toByteArray())
    }

    @Test fun allStatusesRoundTripWithExactlyTheirAllowedConsumption() {
        FilesSubmitStatus.entries.forEach { status ->
            val expected = when (status) {
                FilesSubmitStatus.DISPATCH_REQUESTED, FilesSubmitStatus.ADMITTED_NOT_DISPATCHED,
                FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED -> FilesConsumption.RESERVED
                FilesSubmitStatus.ADMISSION_UNCONFIRMED -> FilesConsumption.UNKNOWN
                else -> FilesConsumption.NONE
            }
            FilesConsumption.entries.forEach { consumption ->
                if (consumption == expected) {
                    val reply = FilesHandoffWire.decodeSubmit(FilesHandoffWire.encodeSubmit(FilesSubmitReply.create(status, consumption)))
                    assertEquals(status, reply.status); assertEquals(consumption, reply.consumption)
                } else assertThrows(FilesHandoffWireException::class.java) { FilesSubmitReply.create(status, consumption) }
            }
        }
        assertEquals(FilesConsumption.NONE, FilesHandoffWire.decodeSubmit("""{"version":1,"status":7,"consumption":0}""".toByteArray()).consumption)
    }

    @Test fun emptyFailureAndFullUnicodePagesRoundTripDefensively() {
        assertTrue(FilesHandoffWire.decodeDiscovery(FilesHandoffWire.encodeDiscovery(FilesDiscoveryReply.success(emptyList()))).locations.isEmpty())
        FilesDiscoveryStatus.entries.filter { it != FilesDiscoveryStatus.OK }.forEach { status ->
            assertEquals(status, FilesHandoffWire.decodeDiscovery(FilesHandoffWire.encodeDiscovery(FilesDiscoveryReply.failure(status))).status)
        }
        val source = (1..32).map { item(it, "\"\\\uD801\uDC00".repeat(20)) }.toMutableList()
        val expected = source.toList()
        val reply = FilesDiscoveryReply.success(source, source.last().reference.resourceId)
        source.clear()
        val bytes = FilesHandoffWire.encodeDiscovery(reply)
        assertTrue(bytes.size <= FilesHandoffWire.MAX_DISCOVERY_BYTES)
        val decoded = FilesHandoffWire.decodeDiscovery(bytes, item(0).reference.resourceId)
        assertEquals(expected, decoded.locations)
        assertEquals(expected.last().reference.resourceId, decoded.nextAfterResourceId)
        assertThrows(UnsupportedOperationException::class.java) { (decoded.locations as MutableList<*>).clear() }
        assertFalse(decoded.toString().contains(expected.first().label))
    }

    @Test fun canonicalSortingCursorAndCountAreEnforced() {
        assertThrows(FilesHandoffWireException::class.java) { FilesDiscoveryReply.success(listOf(item(2), item(1))) }
        assertThrows(FilesHandoffWireException::class.java) { FilesDiscoveryReply.success(listOf(item(1), item(1))) }
        assertThrows(FilesHandoffWireException::class.java) { FilesDiscoveryReply.success((1..33).map { item(it) }) }
        assertThrows(FilesHandoffWireException::class.java) { FilesDiscoveryReply.success(listOf(item(1)), item(1).reference.resourceId) }
        assertThrows(FilesHandoffWireException::class.java) { FilesDiscoveryReply.success((1..32).map { item(it) }, item(33).reference.resourceId) }
        val bytes = FilesHandoffWire.encodeDiscovery(FilesDiscoveryReply.success(listOf(item(1))))
        assertThrows(FilesHandoffWireException::class.java) { FilesHandoffWire.decodeDiscovery(bytes, item(1).reference.resourceId) }
        listOf("x", "40000000-0000-1000-8000-000000000001", "40000000-0000-4000-C000-000000000001", "a".repeat(1024)).forEach {
            assertThrows(FilesHandoffWireException::class.java) { FilesHandoffWire.validateAfterResourceId(it) }
        }
    }

    @Test fun decodedPagesRejectOversizeOrderingAndForgedContinuation() {
        val encoded = String(FilesHandoffWire.encodeDiscovery(FilesDiscoveryReply.success(listOf(item(1)))))
        val descriptor = encoded.substringAfter("\"locations\":[").substringBefore("],\"nextAfterResourceId\"")
        failDiscovery(encoded.replace(descriptor, List(33) { descriptor }.joinToString(",")))
        failDiscovery(encoded.replace(descriptor, "$descriptor,$descriptor"))
        failDiscovery(encoded.replace("\"nextAfterResourceId\":\"\"", "\"nextAfterResourceId\":\"${item(1).reference.resourceId}\""))
        val pair = String(FilesHandoffWire.encodeDiscovery(FilesDiscoveryReply.success(listOf(item(1), item(2)))))
        // Swap IDs without letting a validated local factory conceal decoder ordering defects.
        failDiscovery(pair.replace(item(1).reference.resourceId, "SWAP")
            .replace(item(2).reference.resourceId, item(1).reference.resourceId).replace("SWAP", item(2).reference.resourceId))
        assertEquals(FilesDiscoveryStatus.OK, FilesHandoffWire.decodeDiscovery(
            encoded.padEnd(FilesHandoffWire.MAX_DISCOVERY_BYTES, ' ').toByteArray()).status)
    }

    @Test fun exactShapesRejectDuplicateUnknownMissingAndWrongTypes() {
        listOf(
            """{"version":1,"status":4,"status":4}""", """{"version":1,"status":4,"private":true}""",
            """{"status":4}""", """{"version":"1","status":4}""", """{"version":1,"status":0}""",
            """{"version":1,"status":4,"locations":[],"nextAfterResourceId":""}""",
            """{"version":1,"status":0,"locations":null,"nextAfterResourceId":""}""",
            """{"version":1,"status":0,"locations":[],"nextAfterResourceId":0}""",
        ).forEach { failDiscovery(it) }
        listOf(
            """{"version":1,"status":0,"consumption":1,"consumption":1}""",
            """{"version":1,"status":0}""", """{"version":1,"status":0,"consumption":"1"}""",
            """{"version":1,"status":0,"consumption":0}""", """{"version":1,"status":11,"consumption":1}""",
            """{"version":1,"status":99,"consumption":0}""", """{"version":1,"status":1,"consumption":9}""",
            """{"version":1,"status":1,"consumption":0,"path":"secret"}""",
        ).forEach { failSubmit(it) }
    }

    @Test fun invalidDescriptorsCannotImportPrivateFieldsOrNormalizeLabels() {
        val original = String(FilesHandoffWire.encodeDiscovery(FilesDiscoveryReply.success(listOf(item(1)))))
        listOf(
            original.replace("\"label\":\"Folder\"", "\"label\":\" Folder\""),
            original.replace("\"label\":\"Folder\"", "\"label\":\"Folder\",\"accountName\":\"private\""),
            original.replace("\"label\":\"Folder\"", "\"label\":null"),
            original.replace("\"label\":\"Folder\"", "\"label\":\"\\ud800\""),
            original.replace(item(1).reference.resourceId, "40000000-0000-1000-8000-000000000001"),
            original.replace(item(1).reference.resourceId, item(1).reference.hostId),
        ).forEach { failDiscovery(it) }
    }

    @Test fun strictJsonUtf8AndByteBoundsRejectMalformedInput() {
        val valid = """{"version":1,"status":1,"consumption":0}"""
        listOf("\uFEFF$valid", "$valid {}", valid.replace("1,", "1.0,"), valid.replace("1,", "1e0,"),
            valid.replace("1,", "01,"), valid.replace("\"status\"", "'status'"), "/*comment*/$valid",
            valid.replace("\"version\"", "\"ver\\qson\""), valid.replace("version", "ver\nsion"), "[]", "null").forEach { failSubmit(it) }
        assertEquals(FilesHandoffWireCode.UNKNOWN_VERSION, failSubmit(valid.replace("version\":1", "version\":2")).code)
        assertThrows(FilesHandoffWireException::class.java) { FilesHandoffWire.decodeSubmit(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertEquals(FilesHandoffWireCode.TOO_LARGE, assertThrows(FilesHandoffWireException::class.java) {
            FilesHandoffWire.decodeDiscovery(ByteArray(FilesHandoffWire.MAX_DISCOVERY_BYTES + 1))
        }.code)
        assertEquals(FilesHandoffWireCode.TOO_LARGE, assertThrows(FilesHandoffWireException::class.java) {
            FilesHandoffWire.decodeSubmit(ByteArray(FilesHandoffWire.MAX_SUBMIT_BYTES + 1))
        }.code)
        assertEquals(FilesSubmitStatus.DENIED, FilesHandoffWire.decodeSubmit(valid.padEnd(FilesHandoffWire.MAX_SUBMIT_BYTES, ' ').toByteArray()).status)
        assertEquals(FilesHandoffWireCode.INVALID_JSON, failSubmit("{\"version\":1,\"status\":1,\"consumption\":0,}").code)
    }
}
