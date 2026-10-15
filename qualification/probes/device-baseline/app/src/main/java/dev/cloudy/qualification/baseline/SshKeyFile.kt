package dev.cloudy.qualification.baseline

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/** Shared bounded SEC1 EC PEM loading; neither caller accepts arbitrary key/KDF formats. */
internal object SshKeyFile {
    private const val MAX_PRIVATE_KEY_BYTES = 16_384

    fun read(file: File): String {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS)
        if (!attributes.isRegularFile || attributes.size() !in 1..MAX_PRIVATE_KEY_BYTES.toLong()) {
            throw SshProbeFailure("private-key-type-or-size-out-of-bounds")
        }
        val buffer = ByteArray(MAX_PRIVATE_KEY_BYTES + 1)
        try {
            val size = file.inputStream().use { input ->
                var size = 0
                while (size < buffer.size) {
                    val count = input.read(buffer, size, buffer.size - size)
                    if (count == -1) break
                    if (count == 0) throw SshProbeFailure("private-key-no-progress")
                    size += count
                }
                size
            }
            if (size !in 1..MAX_PRIVATE_KEY_BYTES) throw SshProbeFailure("private-key-size-out-of-bounds")
            // SSHJ's public content API retains a String; do not claim complete secret zeroization.
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(buffer, 0, size)).toString()
            return SshPrivateKeyPem.validate(text)
        } finally {
            buffer.fill(0)
        }
    }

}
