package io.github.baitian6641.cloudy.desktop

/** Fixed selected-native categories only; no peer strings, credentials or exception text. */
internal enum class VncOperationFailure {
    CONNECTION, TIMEOUT, CERTIFICATE, SECURITY_OR_AUTHENTICATION,
}

/** Main-thread owned operation result, independent from handle/lease retirement.
 * Admission is called only after the model installs a new original engine. Polls, cleanup,
 * reaping and rejected admissions cannot reset the first meaningful failure for that attempt.
 * The registry temporarily shares this plain holder, capturing before a foreign window reaps;
 * the original model retains it afterward. It owns no engine, callbacks, Activity or worker.
 */
internal class VncOperationOutcome {
    private var attempt: Any? = null
    var failure: VncOperationFailure? = null
        private set

    fun admit(original: Any) {
        if (attempt === original) return
        attempt = original
        failure = null
    }

    fun observe(original: Any, nativeError: Int, failed: Boolean): VncOperationFailure? {
        if (attempt !== original || failure != null) return failure
        failure = when (nativeError) {
            // The Java owner starts at -1 before native callbacks; zero is native no-error.
            // Neither observation can declare failure unless the owner itself failed.
            -1, 0 -> if (failed) VncOperationFailure.CONNECTION else null
            1 -> null // Native CANCELLED is an ordinary stop, never evidence of failed authentication.
            2 -> VncOperationFailure.TIMEOUT
            4 -> VncOperationFailure.CERTIFICATE
            10 -> VncOperationFailure.SECURITY_OR_AUTHENTICATION
            else -> VncOperationFailure.CONNECTION
        }
        return failure
    }
}
