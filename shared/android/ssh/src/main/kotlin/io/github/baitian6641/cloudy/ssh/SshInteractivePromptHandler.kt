package io.github.baitian6641.cloudy.ssh

/**
 * Supplies one keyboard-interactive response to the authentication worker. The handler may block
 * until a person answers, and returns null to cancel the attempt. Prompt text is untrusted server
 * data: callers must bound/display it as plain text and never log the response.
 */
fun interface SshInteractivePromptHandler {
    fun request(prompt: String, echo: Boolean): CharArray?
}
