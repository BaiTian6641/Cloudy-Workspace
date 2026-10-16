package io.github.baitian6641.cloudy.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Pure grammar/evidence checks; no Android private-file or real lifecycle claim. */
class FilesPositiveObservationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val run = "12345678-1234-4123-8123-123456789abc"
    private fun config(role: String = "SENDER", deadline: String = "1000") =
        """{"schema":1,"run":"$run","role":"$role","operation":"OPEN_FILES","processNonce":"$run","deadlineElapsedMillis":$deadline}"""
    private fun parsed(role: String = "SENDER") = FilesPositiveConfig.parse(config(role).toByteArray(), 100)
    private fun reject(text: String, now: Long = 100) {
        try { FilesPositiveConfig.parse(text.toByteArray(), now); throw AssertionError("ACCEPTED_INVALID_CONFIG") }
        catch (_: IllegalArgumentException) { /* Expected strict rejection. */ }
    }
    @Test fun configStrictness() {
        assertEquals(1000L, parsed().deadline)
        reject(config().replace("\"schema\":1", "\"schema\":1,\"schema\":1"))
        reject(config().replace("\"schema\":1", "\"schema\":1,\"extra\":1"))
        reject(config().replace("OPEN_FILES", "REPLAY"))
        reject(config("OTHER"))
        reject(config().replace(run, run.uppercase()))
        reject(config().replace("\"schema\":1", "\"schema\":\"1\""))
        reject(config().replace("}", ",}"))
        reject(config() + "x")
        reject(" ".repeat(2049))
    }
    @Test fun deadlineBounds() {
        reject(config(deadline = "100"))
        reject(config(deadline = "300101"))
        reject(config(deadline = "0"))
        reject(config(deadline = "01000"))
        assertEquals(300100L, FilesPositiveConfig.parse(config(deadline = "300100").toByteArray(), 100).deadline)
    }
    @Test fun exactJsonWhitespaceOnly() {
        val normal = " \t\r\n" + config().replace("{", "{ \t\r\n").replace(",", ", \t\r\n") + " \t\r\n"
        assertEquals(1000L, FilesPositiveConfig.parse(normal.toByteArray(), 100).deadline)
        for (invalid in listOf('\u000b', '\u000c')) {
            reject(invalid + config())
            reject(config().replace("{", "{$invalid"))
            reject(config().replace(",", ",$invalid"))
            reject(config() + invalid)
        }
    }
    @Test fun publicationChecksAllStagesBeforeFinalRename() {
        val events = mutableListOf<String>()
        val directory = temporary.newFolder()
        val stage = directory.resolve("stage")
        val destination = directory.resolve("receipt")
        val sealed = "{\"sealed\":true}".toByteArray()
        commitFilesPositiveSnapshot(sealed, open = {
            events.add("open")
            object : FileOutputStream(stage) {
                override fun write(bytes: ByteArray) { events.add("write"); super.write(bytes) }
                override fun close() { events.add("close"); super.close() }
            }
        }, prepare = { events.add("permission") }, sync = {
            events.add("sync"); it.fd.sync()
        }, publish = {
            events.add("rename")
            Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        })
        assertEquals(listOf("open", "permission", "write", "sync", "close", "rename"), events)
        assertEquals(sealed.toString(Charsets.UTF_8), destination.readText())
        assertFalse(stage.exists())
    }
    @Test fun failedFinalStagePreservesUnsealedOutputAndUncertainStaging() {
        // Test-only file primitives inject failures into the actual shared sequence. This does not
        // exercise Android Os modes/exclusive open/rename or claim device durability.
        for (failure in listOf("open", "permission", "write", "sync", "close", "rename")) {
            val directory = temporary.newFolder()
            val stage = directory.resolve("stage")
            val destination = directory.resolve("receipt")
            val partial = "{\"sealed\":false}"
            destination.writeText(partial)
            val events = mutableListOf<String>()
            fun failAt(name: String) { events.add(name); if (failure == name) throw IOException("TEST_ONLY_FAILURE") }
            try {
                commitFilesPositiveSnapshot("{\"sealed\":true}".toByteArray(), open = {
                    failAt("open")
                    object : FileOutputStream(stage) {
                        override fun write(bytes: ByteArray) {
                            if (failure == "write") super.write(bytes, 0, 1)
                            failAt("write"); super.write(bytes)
                        }
                        override fun close() { super.close(); failAt("close") }
                    }
                }, prepare = { failAt("permission") }, sync = {
                    it.fd.sync(); failAt("sync")
                }, publish = {
                    failAt("rename")
                    Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING)
                })
                throw AssertionError("ACCEPTED_FAILED_PUBLICATION")
            } catch (_: IOException) { /* Expected checked stage failure; no cleanup/publish follows. */ }
            assertEquals(partial, destination.readText())
            assertEquals(failure != "open", stage.exists())
            assertEquals(failure == "rename", "rename" in events)
        }
    }
    private fun ledger(role: String, vararg events: Triple<String, Int, Int>): FilesPositiveLedger {
        val ledger = FilesPositiveLedger(parsed(role))
        events.forEachIndexed { index, event -> ledger.add(FilesPositiveRecord(index + 1,
            110L + index, event.first, event.second, event.third)) }
        return ledger
    }
    private val sender = arrayOf(Triple("SENDER_CREATED", 1, 0), Triple("SUBMIT_ACCEPTED", 1, 2),
        Triple("CALL_FINISHED", 2, 0), Triple("CALL_RETIRED", 2, 0), Triple("SENDER_DESTROYED", 1, 0))
    private val receiver = arrayOf(Triple("DISPATCH_STARTED", 1, 0), Triple("DISPATCH_RETIRED", 1, 0),
        Triple("RECEIVER_CREATED", 2, 3), Triple("SESSION_CLAIMED", 3, 4),
        Triple("TRANSPORT_ESTABLISHED", 4, 0), Triple("TRANSPORT_RETIRED", 4, 0),
        Triple("SAME_SESSION_IDLE", 3, 4), Triple("RECEIVER_DESTROYED", 2, 3))
    @Test fun senderActualChainAndFailures() {
        assertTrue(ledger("SENDER", *sender).originalChainComplete())
        assertFalse(ledger("SENDER", *sender.filter { it.first != "CALL_FINISHED" }.toTypedArray()).originalChainComplete())
        assertFalse(ledger("SENDER", *sender.map { if (it.first == "CALL_RETIRED") it.copy(second = 3) else it }.toTypedArray()).originalChainComplete())
        assertFalse(ledger("SENDER", sender[0], sender[1], sender[3], sender[2], sender[4]).originalChainComplete())
        assertFalse(ledger("SENDER", *sender, Triple("SENDER_CREATED", 3, 0)).originalChainComplete())
        assertFalse(ledger("SENDER", *sender.map { if (it.first == "SENDER_DESTROYED") it.copy(second = 3) else it }.toTypedArray()).originalChainComplete())
    }
    @Test fun receiverSameSessionAndReplacementFailures() {
        assertTrue(ledger("RECEIVER", *receiver).originalChainComplete())
        assertFalse(ledger("RECEIVER", *receiver.filter { it.first != "TRANSPORT_ESTABLISHED" }.toTypedArray()).originalChainComplete())
        assertFalse(ledger("RECEIVER", *receiver.map { if (it.first == "TRANSPORT_RETIRED") it.copy(first = "RETIREMENT_UNCERTAIN") else it }.toTypedArray()).originalChainComplete())
        assertFalse(ledger("RECEIVER", *receiver.map { if (it.first == "SAME_SESSION_IDLE") it.copy(third = 5) else it }.toTypedArray()).originalChainComplete())
        assertFalse(ledger("RECEIVER", *receiver, Triple("RECEIVER_CREATED", 5, 3)).originalChainComplete())
        assertFalse(ledger("RECEIVER", *receiver, Triple("MODEL_CLEARED", 3, 0)).originalChainComplete())
        assertFalse(ledger("RECEIVER", *receiver.map { if (it.first == "DISPATCH_RETIRED") it.copy(second = 5) else it }.toTypedArray()).originalChainComplete())
    }
    @Test fun ledgerRejectsBoundsWrongRoleAndOutOfOrderTime() {
        val ledger = ledger("SENDER", sender[0])
        ledger.add(FilesPositiveRecord(3, 111, "SUBMIT_ACCEPTED", 1, 2)); assertTrue(ledger.invalid)
        assertTrue(ledger("SENDER", receiver[0]).invalid)
        assertTrue(ledger("SENDER", Triple("SENDER_CREATED", 13, 0)).invalid)
        val time = ledger("SENDER", sender[0]); time.add(FilesPositiveRecord(2, 109, "SUBMIT_ACCEPTED", 1, 2))
        assertTrue(time.invalid)
        val many = FilesPositiveLedger(parsed())
        repeat(33) { many.add(FilesPositiveRecord(it + 1, 110L + it, "SENDER_CREATED", 1, 0)) }
        assertTrue(many.invalid)
    }
    @Test fun receiptIsBoundedTypedAndSealedSeparatelyFromChain() {
        val ledger = ledger("SENDER", *sender)
        val receipt = ledger.encode(123, 50, run, sealed = true).toString(Charsets.UTF_8)
        assertTrue(receipt.length < 16_384)
        assertTrue(receipt.contains("\"sealed\":true"))
        assertTrue(receipt.contains("\"originalOperationChainComplete\":true"))
        assertFalse(ledger.encode(123, 50, run).toString(Charsets.UTF_8).contains("\"sealed\":true"))
        ledger.invalidate(); assertFalse(ledger.originalChainComplete())
    }
}
