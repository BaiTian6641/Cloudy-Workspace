package io.github.baitian6641.cloudy.domain

import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SshObservationPresentationTest {
    private val generation = "12345678-1234-4234-8234-123456789abc"
    private val trustGeneration = "12345678-1234-4234-9234-123456789abc"
    private val otherGeneration = "12345678-1234-4234-a234-123456789abc"
    private val owner = WindowOwner.CONSOLE
    private val draft = ConnectionProfileDraft.create(owner, "Local profile", ConnectionProtocol.SSH, "example.invalid", 22)
    private val profile = ConnectionProfile.create(draft)
    private val binding = SshObservationProfile.create(generation, profile)
    private fun key(byte: Int = 0) = SshHostKeyIdentity.create("ssh-ed25519", "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { byte.toByte() }))
    private fun evaluation(current: SshTrustSnapshot? = null, observed: SshHostKeyIdentity = key(),
                           peer: SshPeerIdentity = binding.peer, storeOwner: WindowOwner = owner) =
        SshHostTrust.evaluate(storeOwner, trustGeneration, peer, observed, current)
    private fun controller(lifetime: Long = 120_000): SshObservationPresentation =
        SshObservationPresentation(owner, lifetime).also { success(it.select(binding)); success(it.resume()) }
    private fun <T> success(result: SshObservationResult<T>): T = when (result) {
        is SshObservationResult.Success -> result.value
        is SshObservationResult.Failure -> error(result.code.name)
    }
    private fun failure(code: SshObservationCode, result: SshObservationResult<*>) {
        assertEquals(SshObservationResult.Failure(code), result)
    }
    private fun presented(controller: SshObservationPresentation, start: Long = 0, completed: Long = 1,
                          evaluation: SshTrustEvaluation = evaluation()): SshObservationChallenge {
        val attempt = success(controller.begin(start))
        return (success(controller.complete(attempt, evaluation, completed)) as SshObservationCompletion.Challenge).presentation
    }

    @Test fun freshTicketsBindExactProfileAndIndependentStoreGenerationAndAdmitOnlyOnce() {
        val controller = controller()
        val presentation = presented(controller)
        val challenge = presentation.challenge
        assertEquals(binding, presentation.profile)
        assertEquals(trustGeneration, challenge.token.generation)
        assertEquals(generation, presentation.profile.generation)
        assertEquals(4, java.util.UUID.fromString(challenge.attemptId).version())
        assertEquals(4, java.util.UUID.fromString(challenge.presentationGeneration).version())
        assertNotEquals(challenge.attemptId, challenge.presentationGeneration)
        assertEquals(120_001, presentation.expiresAtMillis)
        val admitted = success(controller.approve(challenge.presentationGeneration, binding, 2))
        assertSame(challenge, admitted.challenge)
        assertEquals(binding, admitted.profile)
        assertNull(controller.presentation)
        assertNull(controller.currentAttempt)
        failure(SshObservationCode.STALE_PRESENTATION, controller.approve(challenge.presentationGeneration, binding, 3))
    }

    @Test fun challengeReasonsRetainExactOldAndObservedKeysWithoutImplicitApproval() {
        val old = key(1)
        val cases = listOf(
            null to SshTrustChallengeReason.FIRST_USE,
            SshTrustSnapshot.create(owner, trustGeneration, binding.peer, 7, SshTrustStatus.accepted(old)) to
                SshTrustChallengeReason.REPLACEMENT,
            SshTrustSnapshot.create(owner, trustGeneration, binding.peer, 8, SshTrustStatus.revoked(old)) to
                SshTrustChallengeReason.REENROLLMENT,
        )
        for ((current, reason) in cases) {
            val controller = controller()
            val value = presented(controller, evaluation = evaluation(current))
            assertEquals(reason, value.challenge.reason)
            assertEquals(key(), value.challenge.observedKey)
            assertEquals(if (current == null) null else old, value.challenge.previousKey)
            assertEquals(current?.revision ?: 0, value.challenge.token.expectedRevision)
            assertSame(value, controller.presentation)
        }
    }

    @Test fun matchedObservationRetiresAttemptWithoutCreatingApprovalOrChallenge() {
        val controller = controller()
        val attempt = success(controller.begin(0))
        val snapshot = SshTrustSnapshot.create(owner, trustGeneration, binding.peer, 1, SshTrustStatus.accepted(key()))
        val result = success(controller.complete(attempt, evaluation(snapshot), 1))
        assertTrue(result is SshObservationCompletion.Matched)
        assertNull(controller.presentation)
        assertNull(controller.currentAttempt)
        failure(SshObservationCode.STALE_ATTEMPT, controller.complete(attempt, evaluation(), 2))
    }

    @Test fun transportAndPresentationDeadlinesRejectAtExactBoundary() {
        val controller = controller(10)
        val attempt = success(controller.begin(0))
        failure(SshObservationCode.EXPIRED, controller.complete(attempt, evaluation(), 10))
        val presentation = presented(controller, 11, 12)
        assertEquals(22, presentation.expiresAtMillis)
        failure(SshObservationCode.EXPIRED, controller.approve(presentation.challenge.presentationGeneration, binding, 22))
        val next = presented(controller, 23, 24)
        success(controller.approve(next.challenge.presentationGeneration, binding, 33))
    }

    @Test fun pauseCancelAndCloseRetireApprovalAndResumeNeverRestoresIt() {
        for (event in listOf<(SshObservationPresentation) -> Unit>({ it.pause() }, { it.cancel() }, { it.close() })) {
            val controller = controller()
            val value = presented(controller)
            event(controller)
            assertNull(controller.presentation)
            assertNull(controller.currentAttempt)
            if (controller.resume() is SshObservationResult.Success) {
                failure(SshObservationCode.STALE_PRESENTATION, controller.approve(value.challenge.presentationGeneration, binding, 2))
            } else {
                failure(SshObservationCode.CLOSED, controller.begin(2))
            }
        }
        val fresh = SshObservationPresentation(owner)
        success(fresh.select(binding))
        failure(SshObservationCode.NOT_FOREGROUND, fresh.begin(0))
        success(fresh.resume())
        success(fresh.begin(0))
        fresh.pause()
        success(fresh.resume())
        assertNull(fresh.currentAttempt)
    }

    @Test fun supersededCompletionCannotRetireCurrentAttemptEvenWithOldTimestamp() {
        val controller = controller()
        val previous = success(controller.begin(10))
        val current = success(controller.begin(20))
        assertNotEquals(previous.attemptId, current.attemptId)
        failure(SshObservationCode.STALE_ATTEMPT, controller.complete(previous, evaluation(), 11))
        assertSame(current, controller.currentAttempt)
        success(controller.complete(current, evaluation(), 21))
        failure(SshObservationCode.STALE_ATTEMPT, controller.complete(current, evaluation(), 22))
    }

    @Test fun wrongPresentationDoesNotConsumeCurrentButProfileRevisionOrGenerationChangeDoes() {
        val controller = controller()
        val value = presented(controller)
        failure(SshObservationCode.STALE_PRESENTATION, controller.approve(otherGeneration, binding, 2))
        assertSame(value, controller.presentation)
        val revised = SshObservationProfile.create(generation, profile.revised(draft))
        failure(SshObservationCode.PROFILE_CHANGED, controller.approve(value.challenge.presentationGeneration, revised, 3))
        assertNull(controller.presentation)
        val next = presented(controller, 4, 5)
        success(controller.select(SshObservationProfile.create(otherGeneration, profile)))
        failure(SshObservationCode.STALE_PRESENTATION, controller.approve(next.challenge.presentationGeneration, binding, 6))
    }

    @Test fun exactBindingDetectsChangedFieldsEvenIfAnInvalidCallerReusesRevision() {
        val controller = controller()
        val value = presented(controller)
        val altered = ConnectionProfile.restore(profile.id, profile.revision,
            ConnectionProfileDraft.create(owner, "Changed label", ConnectionProtocol.SSH, profile.host, profile.port))
        failure(SshObservationCode.PROFILE_CHANGED, controller.approve(value.challenge.presentationGeneration,
            SshObservationProfile.create(generation, altered), 2))
    }

    @Test fun wrongPeerOwnerAndSelectionCannotPresentAHandshake() {
        val controller = controller()
        val wrongPeer = SshPeerIdentity.create(profile.host, 2222)
        var attempt = success(controller.begin(0))
        failure(SshObservationCode.WRONG_PEER, controller.complete(attempt, evaluation(peer = wrongPeer), 1))
        attempt = success(controller.begin(2))
        failure(SshObservationCode.WRONG_OWNER, controller.complete(attempt,
            evaluation(storeOwner = WindowOwner.DESKTOP), 3))
        attempt = success(controller.begin(4))
        success(controller.select(null))
        failure(SshObservationCode.STALE_ATTEMPT, controller.complete(attempt, evaluation(), 5))
        failure(SshObservationCode.NO_SELECTION, controller.begin(6))
        val desktop = ConnectionProfile.create(ConnectionProfileDraft.create(WindowOwner.DESKTOP, "Desktop",
            ConnectionProtocol.SSH, profile.host, profile.port))
        failure(SshObservationCode.WRONG_OWNER, controller.select(SshObservationProfile.create(generation, desktop)))
    }

    @Test fun invalidTimeAndOverflowDenyWithoutExposingIdentifiers() {
        val controller = controller()
        failure(SshObservationCode.INVALID_TIME, controller.begin(-1))
        val value = presented(controller, 10, 11)
        failure(SshObservationCode.INVALID_TIME, controller.approve(value.challenge.presentationGeneration, binding, 10))
        failure(SshObservationCode.INVALID_TIME, controller.begin(Long.MAX_VALUE))
        assertNull(controller.currentAttempt)
        assertNull(controller.presentation)
        assertFalse(binding.toString().contains(profile.host))
        assertFalse(value.toString().contains(value.challenge.presentationGeneration))
    }

    @Test fun factoriesRejectInvalidProfilesAndLifetimeWithFixedErrors() {
        for (lifetime in listOf(Long.MIN_VALUE, 0, 120_001, Long.MAX_VALUE)) {
            val error = assertThrows(SshObservationException::class.java) { SshObservationPresentation(owner, lifetime) }
            assertEquals(SshObservationCode.INVALID_LIFETIME, error.code)
            assertEquals(error.code.name, error.message)
            assertNull(error.cause)
        }
        for (generation in listOf("invalid", this.generation.uppercase(), "12345678-1234-1234-8234-123456789abc")) {
            val error = assertThrows(SshObservationException::class.java) { SshObservationProfile.create(generation, profile) }
            assertEquals(SshObservationCode.INVALID_PROFILE, error.code)
            assertNull(error.cause)
        }
        val rdp = ConnectionProfile.create(ConnectionProfileDraft.create(WindowOwner.DESKTOP, "RDP",
            ConnectionProtocol.RDP, profile.host, profile.port))
        assertThrows(SshObservationException::class.java) { SshObservationProfile.create(generation, rdp) }
    }

    @Test fun competingApprovalsHaveExactlyOneAdmissionAndPauseWinsWhenOrderedFirst() {
        val controller = controller()
        val value = presented(controller)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val approvals = (0..1).map {
                pool.submit<SshObservationResult<SshObservationApproval>> {
                    check(start.await(5, TimeUnit.SECONDS))
                    controller.approve(value.challenge.presentationGeneration, binding, 2)
                }
            }
            start.countDown()
            val results = approvals.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is SshObservationResult.Success })
            assertEquals(1, results.count { it == SshObservationResult.Failure(SshObservationCode.STALE_PRESENTATION) })
        } finally { pool.shutdownNow() }
        val paused = this.controller()
        val pending = presented(paused)
        paused.pause()
        failure(SshObservationCode.STALE_PRESENTATION, paused.approve(pending.challenge.presentationGeneration, binding, 2))
    }
}
