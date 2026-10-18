package io.github.baitian6641.cloudy.ssh

import java.util.UUID

/**
 * Two-phase staged edit over [SshFiles]. Staging writes a private sibling temp file; commit
 * re-checks the target version and renames with explicit overwrite. The protocol has no atomic
 * compare-and-swap: the final re-check narrows, but cannot eliminate, a concurrent writer race.
 * Every commit is an explicit replace decision from the caller; a changed target is never
 * overwritten without [commit]'s replaceChanged argument. Version equality compares kind, size and
 * second-resolution mtime: a same-size write within the same second is not detectable, so this is
 * conflict detection, not atomic compare-and-swap.
 */
class SshFileEditor(private val files: SshFiles) {
    class Document internal constructor(
        val path: String, val bytes: SshFileBytes, val version: SshFiles.Stat,
    ) { override fun toString() = "SshEditorDocument(redacted)" }

    class Staged internal constructor(
        val path: String, val tempPath: String, val version: SshFiles.Stat, val byteCount: Int,
    ) { override fun toString() = "SshStagedEdit(redacted)" }

    sealed interface CommitResult {
        class Committed(val version: SshFiles.Stat?) : CommitResult
        class Conflict(val current: SshFiles.Stat) : CommitResult
        class Failure(val code: SshTransportCode) : CommitResult
    }

    fun open(path: String, maxBytes: Int = MAX_EDIT_BYTES, timeoutMillis: Int = 3_000):
        SshFilesResult<Document> {
        if (maxBytes !in 1..MAX_EDIT_BYTES) return SshFilesResult.Failure(SshTransportCode.INVALID_INPUT)
        val version = when (val stat = files.stat(path, timeoutMillis)) {
            is SshFilesResult.Failure -> return stat
            is SshFilesResult.Success -> stat.value
        }
        if (version.kind != SshFileKind.REGULAR) return SshFilesResult.Failure(SshTransportCode.INVALID_REMOTE_DATA)
        if (version.size != null && version.size > maxBytes) {
            return SshFilesResult.Failure(SshTransportCode.LIMIT_EXCEEDED)
        }
        return when (val read = files.read(path, maxBytes, timeoutMillis)) {
            is SshFilesResult.Failure -> read
            is SshFilesResult.Success -> SshFilesResult.Success(Document(path, read.value, version))
        }
    }

    /** Writes the replacement to a same-directory exclusive temp; the target is untouched. */
    fun stage(document: Document, bytes: ByteArray, timeoutMillis: Int = 3_000):
        SshFilesResult<Staged> {
        if (bytes.size > MAX_EDIT_BYTES) return SshFilesResult.Failure(SshTransportCode.INVALID_INPUT)
        val owned = bytes.copyOf()
        try {
            repeat(3) {
                val temp = sibling(document.path, ".cloudy-stage-${UUID.randomUUID().toString().replace("-", "").take(16)}")
                when (val written = files.writeNew(temp, owned, timeoutMillis)) {
                    is SshFilesResult.Success ->
                        return SshFilesResult.Success(Staged(document.path, temp, document.version, owned.size))
                    is SshFilesResult.Failure -> if (written.code != SshTransportCode.ALREADY_EXISTS) return written
                }
            }
            return SshFilesResult.Failure(SshTransportCode.OPERATION_FAILED)
        } finally { owned.fill(0) }
    }

    fun commit(staged: Staged, replaceChanged: Boolean, timeoutMillis: Int = 3_000): CommitResult {
        val current = when (val stat = files.stat(staged.path, timeoutMillis)) {
            is SshFilesResult.Failure -> return CommitResult.Failure(stat.code)
            is SshFilesResult.Success -> stat.value
        }
        if (!sameVersion(staged.version, current) && !replaceChanged) {
            return CommitResult.Conflict(current)
        }
        when (val renamed = files.rename(staged.tempPath, staged.path, overwrite = true, timeoutMillis)) {
            is SshFilesResult.Failure -> {
                // The staged temp is ours; an unremoved temp makes the outcome explicitly uncertain.
                val cleaned = files.removeFile(staged.tempPath, timeoutMillis)
                return CommitResult.Failure(if (cleaned is SshFilesResult.Failure &&
                    cleaned.code != SshTransportCode.NOT_FOUND) SshTransportCode.CLEANUP_UNCERTAIN else renamed.code)
            }
            is SshFilesResult.Success -> {}
        }
        val after = files.stat(staged.path, timeoutMillis)
        return CommitResult.Committed((after as? SshFilesResult.Success)?.value)
    }

    /** Removes only the staged sibling temp; a missing temp is already settled. */
    fun discard(staged: Staged, timeoutMillis: Int = 3_000): SshFilesResult<Unit> {
        val removed = files.removeFile(staged.tempPath, timeoutMillis)
        return if (removed is SshFilesResult.Failure && removed.code == SshTransportCode.NOT_FOUND)
            SshFilesResult.Success(Unit) else removed
    }

    companion object {
        const val MAX_EDIT_BYTES = 65_536

        private fun sameVersion(left: SshFiles.Stat, right: SshFiles.Stat): Boolean =
            left.kind == right.kind && left.size == right.size && left.modifiedSeconds == right.modifiedSeconds

        private fun sibling(path: String, leaf: String): String = when {
            '/' !in path -> "./$leaf"
            path.endsWith('/') -> path + leaf
            else -> path.substringBeforeLast('/') + "/" + leaf
        }
    }
}
