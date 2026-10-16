package io.github.baitian6641.cloudy.ssh

import io.github.baitian6641.cloudy.domain.SshHostKeyIdentity
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

internal object SshObservedKeyCodec {
    fun encode(key: PublicKey): SshHostKeyIdentity {
        val format = KeyType.fromKey(key).toString()
        // The maintained serializer emits the SSH public-key blob, not X.509/DER bytes.
        val wire = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(wire)
        return SshHostKeyIdentity.create(format, "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest))
    }
}
