package dev.cloudy.qualification.baseline

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Instant

/** Separate storage from the finite SSH probe. The runtime's IO executor owns all accesses. */
internal class TerminalJournal(directory: File) {
    private val file = AtomicFile(File(directory, "terminal-journal.json"))

    fun read(): TerminalJournalData {
        checkFiles()
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return TerminalJournalData(emptyList(), emptyMap())
        }
        val bytes = file.openRead().use { it.readNBytes(LIMIT + 1) }
        require(bytes.size <= LIMIT) { "TerminalJournalLimit" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return TerminalJournalCodec.decode(TerminalJson.parse(text))
    }

    fun write(process: String, revision: Long, state: TerminalState, trust: Map<String, String>,
        providersAtStart: List<String>, providersNow: List<String>) {
        checkFiles()
        val root = JSONObject(TerminalJournalCodec.encode(process, revision, Instant.now().toString(), state, trust))
            .put("providerNamesAtRuntimeCreation", org.json.JSONArray(providersAtStart))
            .put("providerNamesNow", org.json.JSONArray(providersNow))
            .put("providerOrderUnchanged", providersAtStart == providersNow)
        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= LIMIT) { "TerminalJournalLimit" }
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
        if (!file.openRead().use { it.readNBytes(bytes.size + 1) }.contentEquals(bytes)) {
            throw IOException("TerminalJournalReadback")
        }
    }

    private fun checkFiles() {
        listOf(file.baseFile, File(file.baseFile.path + ".bak"), File(file.baseFile.path + ".new")).forEach {
            val path = it.toPath()
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= LIMIT) {
                    "TerminalJournalFileTypeOrSize"
                }
            }
        }
    }
    companion object { const val LIMIT = 128 * 1024 }
}
