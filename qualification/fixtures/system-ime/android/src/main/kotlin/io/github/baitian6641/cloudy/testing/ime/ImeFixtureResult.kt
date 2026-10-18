package io.github.baitian6641.cloudy.testing.ime

/** SUBMITTED is framework submission evidence only, never delivery or remote execution. */
class ImeFixtureResult internal constructor(
    val outcome: Outcome,
    val reason: Reason?,
) {
    val submitted: Boolean get() = outcome == Outcome.SUBMITTED

    enum class Outcome { SUBMITTED, API_REFUSED, REJECTED }

    enum class Reason {
        INVALID_TEXT, INVALID_REQUEST, TEXT_TOO_LARGE,
        NO_LEASE, LEASE_MISMATCH, NOT_DEBUGGABLE,
        NO_ACTIVE_INPUT, NO_ACTIVE_IME,
        STALE_INSTANCE, STALE_GENERATION, TARGET_MISMATCH,
        DUPLICATE_REQUEST, REQUEST_LIMIT, EXPIRED_REQUEST,
        UNSUPPORTED_OPERATION,
        COMMIT_REFUSED, COMPOSE_REFUSED, FINISH_REFUSED, KEY_REFUSED,
        RESTORE_REFUSED, RESTORE_UNAVAILABLE, NOT_SELECTED,
        RESULT_MISSING, RESULT_INVALID, SHELL_FAILED, IO_FAILED,
        CANCELLED, SETUP_FAILED, DEVICE_CHANGE_NOT_ALLOWED,
        PREVIOUS_IME_MISSING, FIXTURE_ALREADY_SELECTED, LEASE_EXISTS,
        SESSION_CLOSED, RESTORE_UNCONFIRMED,
    }
}

class ImeQualificationException internal constructor(
    message: String,
    val reason: ImeFixtureResult.Reason,
    cause: Throwable? = null,
) : Exception(message, cause)
