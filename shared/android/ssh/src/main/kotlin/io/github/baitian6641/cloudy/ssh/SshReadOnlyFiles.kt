package io.github.baitian6641.cloudy.ssh

import com.hierynomus.sshj.sftp.RemoteResourceSelector
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import java.util.Collections
import java.util.EnumSet

sealed interface SshFilesResult<out T> {
    class Success<T>(val value: T) : SshFilesResult<T> {
        override fun toString() = "SshFilesResult.Success(redacted)"
    }
    class Failure(val code: SshTransportCode) : SshFilesResult<Nothing> {
        override fun toString() = "SshFilesResult.Failure(${code.name})"
    }
}
enum class SshFileKind { REGULAR, DIRECTORY, OTHER }
class SshFileEntry internal constructor(val name: String, val kind: SshFileKind, val size: Long?) {
    override fun toString() = "SshFileEntry(redacted)"
}
class SshFileBytes internal constructor(bytes: ByteArray) {
    private val value = bytes.copyOf()
    val size: Int get() = value.size
    fun copyBytes(): ByteArray = value.copyOf()
    override fun toString() = "SshFileBytes(redacted)"
}
internal class SshFileBoundException(val code: SshTransportCode) : RuntimeException(code.name)

/** Untrusted remote names are leaf values, never trusted local destinations. No mutation API. */
class SshReadOnlyFiles internal constructor(private val transport: SshAuthenticatedTransport) {
    fun list(path: String, maxEntries: Int = 128, timeoutMillis: Int = 5_000): SshFilesResult<List<SshFileEntry>> {
        if (!SshFileLimits.path(path) || maxEntries !in 1..MAX_ENTRIES) return SshFilesResult.Failure(SshTransportCode.INVALID_INPUT)
        return transport.withFiles(timeoutMillis) { sftp ->
            val entries = ArrayList<SshFileEntry>(maxEntries)
            var problem: SshTransportCode? = null
            // BREAK bounds accumulation and further directory requests. SSHJ decodes each
            // response first; this cap does not claim to replace upstream packet/string bounds.
            sftp.ls(path, RemoteResourceSelector { remote ->
                if (entries.size == maxEntries) {
                    problem = SshTransportCode.LIMIT_EXCEEDED
                    RemoteResourceSelector.Result.BREAK
                } else if (!SshFileLimits.name(remote.name)) {
                    problem = SshTransportCode.INVALID_REMOTE_DATA
                    RemoteResourceSelector.Result.BREAK
                } else {
                    val attributes = remote.attributes
                    val reportedSize = if (attributes.has(FileAttributes.Flag.SIZE)) attributes.size else null
                    if (reportedSize != null && reportedSize < 0 && attributes.type == FileMode.Type.REGULAR) {
                        problem = SshTransportCode.INVALID_REMOTE_DATA
                        RemoteResourceSelector.Result.BREAK
                    } else {
                        val kind = when (attributes.type) {
                            FileMode.Type.REGULAR -> SshFileKind.REGULAR
                            FileMode.Type.DIRECTORY -> SshFileKind.DIRECTORY
                            else -> SshFileKind.OTHER
                        }
                        // SSHJ preserves UINT64_MAX as -1 for some servers' directory size.
                        // It is unknown metadata there, never a negative local allocation size.
                        entries.add(SshFileEntry(remote.name, kind, reportedSize?.takeIf { it >= 0 }))
                        RemoteResourceSelector.Result.ACCEPT
                    }
                }
            })
            problem?.let { throw SshFileBoundException(it) }
            Collections.unmodifiableList(entries)
        }
    }

    /** Reads a complete bounded regular file; exceeding the limit is failure, never a prefix success. */
    fun read(path: String, maxBytes: Int = 65_536, timeoutMillis: Int = 5_000): SshFilesResult<SshFileBytes> {
        if (!SshFileLimits.path(path) || maxBytes !in 1..MAX_BYTES) return SshFilesResult.Failure(SshTransportCode.INVALID_INPUT)
        return transport.withFiles(timeoutMillis) { sftp ->
            val attributes = sftp.lstat(path)
            if (attributes.type != FileMode.Type.REGULAR) throw SshFileBoundException(SshTransportCode.INVALID_REMOTE_DATA)
            if (attributes.has(FileAttributes.Flag.SIZE) && (attributes.size < 0 || attributes.size > maxBytes)) {
                throw SshFileBoundException(SshTransportCode.LIMIT_EXCEEDED)
            }
            val buffer = ByteArray(maxBytes + 1)
            var used = 0
            try {
                sftp.open(path, EnumSet.of(OpenMode.READ)).use { file ->
                    while (true) {
                        val count = file.read(used.toLong(), buffer, used, minOf(32_768, buffer.size - used))
                        if (count == -1) break
                        if (count <= 0 || count > buffer.size - used) throw SshFileBoundException(SshTransportCode.INVALID_REMOTE_DATA)
                        used += count
                        if (used > maxBytes) throw SshFileBoundException(SshTransportCode.LIMIT_EXCEEDED)
                    }
                }
                SshFileBytes(buffer.copyOf(used))
            } finally { buffer.fill(0) }
        }
    }

    companion object {
        const val MAX_ENTRIES = 512
        const val MAX_BYTES = 1_048_576
        const val MAX_PATH_UNITS = 4096
        const val MAX_NAME_UNITS = 1024
    }
}

internal object SshFileLimits {
    fun path(value: String): Boolean = value.length in 1..SshReadOnlyFiles.MAX_PATH_UNITS && text(value)
    fun name(value: String): Boolean = value.length in 1..SshReadOnlyFiles.MAX_NAME_UNITS &&
        value != "." && value != ".." && '/' !in value && '\\' !in value && text(value)
    private fun text(value: String): Boolean {
        var at = 0
        while (at < value.length) {
            val character = value[at++]
            if (Character.isISOControl(character) || character == '\u2028' || character == '\u2029' || Character.isLowSurrogate(character)) return false
            if (Character.isHighSurrogate(character) && (at >= value.length || !Character.isLowSurrogate(value[at++]))) return false
        }
        return true
    }
}
