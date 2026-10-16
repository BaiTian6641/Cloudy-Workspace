package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.common.Message
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.util.concurrent.atomic.AtomicBoolean

/** Finite password-only policy: no password change, retry or keyboard-interactive fallback. */
internal class SshStrictPasswordMethod(
    private val finder: SshOneReleasePasswordFinder,
    private val active: () -> Boolean,
) : AuthPassword(finder) {
    val passwordChangeRefused = AtomicBoolean(false)
    private val built = AtomicBoolean(false)

    override fun buildReq(): SSHPacket {
        if (!active() || !built.compareAndSet(false, true)) throw UserAuthException("AUTHENTICATION_STOPPED")
        return try { super.buildReq() } finally { finder.erasePassword() }
    }

    override fun handle(cmd: Message, buf: SSHPacket) {
        if (cmd == Message.USERAUTH_60) {
            passwordChangeRefused.set(true)
            // Do not parse server prompts or invoke the superclass change-password encoder.
            // Its default provider returns null, which would encode an empty new password.
            throw UserAuthException("PASSWORD_CHANGE_REFUSED")
        }
        super.handle(cmd, buf)
    }

    override fun shouldRetry(): Boolean = false
}

internal class SshOneReleasePasswordFinder(
    private val material: SshPasswordCredentials.Material,
    private val cancellation: SshObservationCancellation,
    private val active: () -> Boolean,
) : PasswordFinder {
    private val released = AtomicBoolean(false)
    override fun reqPassword(resource: Resource<*>): CharArray = cancellation.tryIfActive {
        if (!active() || !released.compareAndSet(false, true)) throw UserAuthException("CREDENTIALS_UNAVAILABLE")
        material.password
    } ?: throw UserAuthException("AUTHENTICATION_STOPPED")
    override fun shouldRetry(resource: Resource<*>): Boolean = false
    fun erasePassword() { material.password.fill('\u0000') }
}
