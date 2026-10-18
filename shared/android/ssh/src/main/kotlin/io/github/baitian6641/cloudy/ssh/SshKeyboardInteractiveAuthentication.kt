package io.github.baitian6641.cloudy.ssh

import java.util.concurrent.atomic.AtomicBoolean
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider
import net.schmizz.sshj.userauth.password.Resource

/**
 * Bounded keyboard-interactive policy. A fixed credential answers exactly one prompt once; a
 * prompted credential asks the supplied handler for every server prompt, one at a time. A retry,
 * a fixed-credential repeat, or a cancelled/expired attempt fails closed; no other authentication
 * method is attempted. Prompted answers are owned here and wiped after the auth call.
 */
internal class SshOneReleaseKeyboardInteractiveResponder(
    private val material: SshKeyboardInteractiveCredentials.Material,
    private val cancellation: SshObservationCancellation,
    private val active: () -> Boolean,
    private val prompts: SshInteractivePromptHandler? = null,
) : ChallengeResponseProvider {
    val unsupported = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val answers = mutableListOf<CharArray>()

    override fun getSubmethods(): List<String> = emptyList()

    override fun init(resource: Resource<*>?, name: String?, instruction: String?) = Unit

    override fun getResponse(prompt: String?, echo: Boolean): CharArray = cancellation.tryIfActive {
        if (!active()) throw UserAuthException("AUTHENTICATION_STOPPED")
        val fixed = material.response
        if (fixed != null) {
            if (!released.compareAndSet(false, true)) {
                unsupported.set(true)
                throw UserAuthException("KEYBOARD_INTERACTIVE_MULTI_PROMPT_UNSUPPORTED")
            }
            fixed
        } else {
            val handler = prompts ?: run {
                unsupported.set(true)
                throw UserAuthException("KEYBOARD_INTERACTIVE_PROMPT_UNAVAILABLE")
            }
            val answer = handler.request(prompt ?: "", echo)
                ?: throw UserAuthException("KEYBOARD_INTERACTIVE_CANCELLED")
            answers += answer
            answer
        }
    } ?: throw UserAuthException("AUTHENTICATION_STOPPED")

    override fun shouldRetry(): Boolean = false

    fun eraseResponse() {
        material.response?.fill('\u0000')
        answers.forEach { it.fill('\u0000') }
        answers.clear()
    }
}
