package io.github.baitian6641.cloudy.ssh

import java.util.concurrent.atomic.AtomicBoolean
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider
import net.schmizz.sshj.userauth.password.Resource

/**
 * Bounded keyboard-interactive policy: answer exactly one prompt once. A second prompt in the
 * same challenge, a retry, or a cancelled/expired attempt is refused as a typed failure; the
 * adapter never repeats a secret or falls back to another authentication method.
 */
internal class SshOneReleaseKeyboardInteractiveResponder(
    private val material: SshKeyboardInteractiveCredentials.Material,
    private val cancellation: SshObservationCancellation,
    private val active: () -> Boolean,
) : ChallengeResponseProvider {
    val unsupported = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    override fun getSubmethods(): List<String> = emptyList()

    override fun init(resource: Resource<*>?, name: String?, instruction: String?) = Unit

    override fun getResponse(prompt: String?, echo: Boolean): CharArray = cancellation.tryIfActive {
        if (!active() || !released.compareAndSet(false, true)) {
            unsupported.set(true)
            throw UserAuthException("KEYBOARD_INTERACTIVE_MULTI_PROMPT_UNSUPPORTED")
        }
        material.response
    } ?: throw UserAuthException("AUTHENTICATION_STOPPED")

    override fun shouldRetry(): Boolean = false

    fun eraseResponse() { material.response.fill('\u0000') }
}
