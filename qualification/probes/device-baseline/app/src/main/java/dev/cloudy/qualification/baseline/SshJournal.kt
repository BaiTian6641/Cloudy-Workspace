package dev.cloudy.qualification.baseline

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Instant

/** One IO owner serializes evidence and explicit trust; no endpoint or key material is stored. */
internal class SshJournal(directory: File) {
    private val file = AtomicFile(File(directory, "ssh-journal.json"))

    fun read(): SshJournalData {
        checkFiles()
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return SshJournalData(emptyList(), emptyMap())
        }
        val bytes = file.openRead().use { it.readNBytes(LIMIT + 1) }
        require(bytes.size <= LIMIT) { "SshJournalLimit" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        SshJournalGuard.check(text)
        val root = JSONObject(text)
        return SshJournalCodec.decode(root.keys().asSequence().associateWith { decoded(root.get(it)) })
    }

    fun encode(processId: String, revision: Long, state: SshState, trust: Map<String, String>): JSONObject =
        JSONObject(SshJournalCodec.encode(processId, revision, BuildConfig.VERSION_NAME,
            Instant.now().toString(), state, trust))

    // The lexical guard bounds nesting before JSONObject and this adapter recurse.
    // Keep framework parsing separate from the pure, tested schema/migration policy.
    private fun decoded(value: Any?): Any? = when {
        value === JSONObject.NULL -> null
        value is JSONObject -> value.keys().asSequence().associateWith { decoded(value.get(it)) }
        value is JSONArray -> (0 until value.length()).map { decoded(value.get(it)) }
        else -> value
    }

    fun write(value: JSONObject) {
        checkFiles()
        val bytes = value.toString(2).toByteArray(Charsets.UTF_8)
        if (bytes.size > LIMIT) throw IOException("SshJournalLimit")
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        if (!file.openRead().use { it.readNBytes(bytes.size + 1) }.contentEquals(bytes)) {
            throw IOException("SshJournalReadback")
        }
    }

    private fun checkFiles() {
        // Private fixture paths may be provisioned by run-as. Refuse special files before open;
        // the byte cap remains necessary for normal files that grow after this check.
        listOf(file.baseFile, File(file.baseFile.path + ".bak"), File(file.baseFile.path + ".new")).forEach {
            val path = it.toPath()
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= LIMIT) {
                    "SshJournalFileTypeOrSize"
                }
            }
        }
    }

    companion object { const val LIMIT = 128 * 1024 }
}
