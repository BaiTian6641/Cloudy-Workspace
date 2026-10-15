package dev.cloudy.qualification.baseline

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

internal data class TransferDigest(val lengthBytes: Long, val sha256: String)
internal class TransferFailure(val code: String) : IOException(code)

/** The download is bounded before every write. Verification separately rereads
 * the closed private file; a network byte count alone can never complete a job.
 */
internal object TransferIntegrity {
    fun copy(input: InputStream, file: File, expected: Long, checkContinue: () -> Unit, progress: (Long) -> Unit) {
        require(expected in 1..TransferConfigSpec.MAX_BYTES)
        if (!file.createNewFile()) throw TransferFailure("part-already-exists")
        var count = 0L
        FileOutputStream(file).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkContinue()
                val read = input.read(buffer)
                checkContinue()
                if (read < 0) break
                if (read == 0) continue
                if (count + read > expected) throw TransferFailure("payload-exceeds-expected")
                output.write(buffer, 0, read)
                count += read
                progress(count)
            }
            if (count != expected) throw TransferFailure("payload-short")
            output.flush()
            output.fd.sync()
        }
        checkContinue()
    }

    fun verify(file: File, expected: Long, expectedHash: String, checkContinue: () -> Unit): TransferDigest {
        require(expected in 1..TransferConfigSpec.MAX_BYTES)
        if (file.length() != expected) throw TransferFailure("file-size-mismatch")
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkContinue()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                count += read
                if (count > expected) throw TransferFailure("verification-size-exceeded")
                digest.update(buffer, 0, read)
            }
        }
        checkContinue()
        if (count != expected) throw TransferFailure("verification-size-mismatch")
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (hash != expectedHash) throw TransferFailure("sha256-mismatch")
        return TransferDigest(count, hash)
    }
}
