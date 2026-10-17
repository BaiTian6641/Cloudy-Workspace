package io.github.baitian6641.cloudy.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure outcome retention/mapping only, not Android rendering or actual authentication evidence. */
class VncOperationFailureTest {
    @Test fun initialOwnerSentinelCannotCreateFailureBeforeNativeResult() {
        val outcome = VncOperationOutcome()
        val attempt = Any()
        outcome.admit(attempt)
        // VncEngine publishes -1 through NEW/STARTING until its first native result.
        assertNull(outcome.observe(attempt, -1, false))
        assertNull(outcome.observe(attempt, 0, false))
        assertNull(outcome.failure)
        assertEquals(VncOperationFailure.TIMEOUT, outcome.observe(attempt, 2, false))
        val failedBeforeNativeHandle = Any()
        outcome.admit(failedBeforeNativeHandle)
        assertEquals(VncOperationFailure.CONNECTION, outcome.observe(failedBeforeNativeHandle, -1, true))
    }
    @Test fun firstObservedRetiredErrorDoesNotNeedIntermediateJavaFailure() {
        val outcome = VncOperationOutcome()
        val attempt = Any()
        outcome.admit(attempt)
        // The model may first poll after cleanup has already published RETIRED.
        assertEquals(VncOperationFailure.SECURITY_OR_AUTHENTICATION, outcome.observe(attempt, 10, false))
        assertEquals(VncOperationFailure.SECURITY_OR_AUTHENTICATION, outcome.observe(attempt, 0, false))
    }

    @Test fun firstFailureSurvivesCancelCleanupRetryAndRepeatedAdmissionOfSameOwner() {
        val outcome = VncOperationOutcome()
        val attempt = Any()
        outcome.admit(attempt)
        outcome.observe(attempt, 4, false)
        // Lifecycle STOP_REQUESTED/UNCERTAIN/RETIRED is separate and cannot erase the outcome.
        outcome.observe(attempt, 1, false)
        outcome.observe(attempt, 0, false)
        outcome.observe(attempt, 10, true)
        outcome.admit(attempt)
        assertEquals(VncOperationFailure.CERTIFICATE, outcome.failure)
    }

    @Test fun onlyNewAdmittedIdentityResetsAndStaleOrForeignSnapshotsCannotMutateIt() {
        val outcome = VncOperationOutcome()
        // Equality is insufficient for original owner authority.
        class EqualToken {
            override fun equals(other: Any?) = other is EqualToken
            override fun hashCode() = 1
        }
        val first = EqualToken()
        val second = EqualToken()
        outcome.admit(first)
        outcome.observe(second, 10, true)
        assertNull(outcome.failure)
        outcome.observe(first, 2, false)
        assertEquals(VncOperationFailure.TIMEOUT, outcome.failure)
        outcome.admit(second)
        assertNull(outcome.failure)
        outcome.observe(first, 10, true)
        assertNull(outcome.failure)
        outcome.observe(second, 3, false)
        assertEquals(VncOperationFailure.CONNECTION, outcome.failure)
    }

    @Test fun foreignReapBeforeOwnerPollRetainsOnlyOriginalModelsOutcome() {
        val originalOwner = VncOperationOutcome()
        val foreignOwner = VncOperationOutcome()
        val originalAttempt = Any()
        val foreignAttempt = Any()
        originalOwner.admit(originalAttempt)
        // The production registry record shares the original holder, not a copy of UI state.
        var registryHolder: VncOperationOutcome? = originalOwner
        registryHolder?.observe(originalAttempt, 10, false)
        registryHolder = null // Global reap precedes any original-model observation.
        foreignOwner.admit(foreignAttempt) // A replacement is already active before A observes.
        foreignOwner.observe(foreignAttempt, 0, false)
        assertNull(registryHolder)
        assertEquals(VncOperationFailure.SECURITY_OR_AUTHENTICATION, originalOwner.failure)
        assertNull(foreignOwner.failure)
        // Rejected stale observations cannot leak that result into another model's holder.
        foreignOwner.observe(originalAttempt, 10, true)
        assertNull(foreignOwner.failure)
        assertEquals(VncOperationFailure.SECURITY_OR_AUTHENTICATION, originalOwner.failure)
    }

    @Test fun ordinaryUserCancelDoesNotBecomeConnectionOrAuthenticationFailure() {
        val outcome = VncOperationOutcome()
        val attempt = Any()
        outcome.admit(attempt)
        assertNull(outcome.observe(attempt, 0, false))
        assertNull(outcome.observe(attempt, 1, false))
        assertNull(outcome.failure)
        assertEquals(VncOperationFailure.CONNECTION, outcome.observe(attempt, 0, true))
    }

    @Test fun fixedNativeCategoriesDoNotClaimWrongPasswordOrSerializeUnknownValues() {
        fun mapped(code: Int): VncOperationFailure? {
            val outcome = VncOperationOutcome()
            val attempt = Any()
            outcome.admit(attempt)
            return outcome.observe(attempt, code, false)
        }
        assertEquals(VncOperationFailure.CERTIFICATE, mapped(4))
        assertEquals(VncOperationFailure.SECURITY_OR_AUTHENTICATION, mapped(10))
        assertEquals(VncOperationFailure.TIMEOUT, mapped(2))
        assertNull(mapped(-1))
        for (code in intArrayOf(-2, 3, 5, 6, 7, 8, 9, Int.MAX_VALUE)) {
            assertEquals(VncOperationFailure.CONNECTION, mapped(code))
        }
    }
}
