package io.github.baitian6641.cloudy.ssh

enum class SshAuthenticationCode {
    INVALID_TIMEOUT, BUSY, QUARANTINED, CANCELLED, TIMED_OUT, CONNECT_FAILED,
    ALGORITHM_REJECTED, KEY_REJECTED, ADMISSION_DENIED, CREDENTIALS_UNAVAILABLE,
    PASSWORD_CHANGE_REFUSED,
    /** Authentication was not confirmed; this can include transport loss after key verification. */
    AUTHENTICATION_FAILED,
    CLEANUP_UNCERTAIN,
}

/** Success is finite authentication followed by proven retirement; it grants no reusable session. */
sealed interface SshAuthenticationResult {
    data object Success : SshAuthenticationResult
    class Failure(val code: SshAuthenticationCode) : SshAuthenticationResult {
        override fun toString(): String = "SshAuthenticationFailure(${code.name})"
    }
}
