package io.github.baitian6641.cloudy.identity

import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultAuthorizationTest {
    private var now = 1000L
    private val id = CredentialId.create()
    private val binding = VaultBinding.create("io.cloudy.console", VaultProtocol.SSH,
        "example.org", 22, VaultRole.TARGET, CredentialId.create().value)
    private fun authority(owner: String = binding.ownerPackage) =
        VaultAuthorization(owner, UUID.randomUUID()) { now }.also { it.setVisible(true) }
    private fun rejected(action: () -> Unit) {
        assertThrows(VaultAuthorizationException::class.java) { action() }
    }

    @Test fun currentSuccessIsRequiredAndGrantIsExactAndOneUse() {
        val owner = authority()
        val attempt = owner.begin(VaultOperation.USE, id, binding)
        rejected { owner.consume(attempt, VaultOperation.USE, id, binding) }
        val grant = owner.succeeded(attempt)
        rejected { grant.consume(VaultOperation.SAVE, id, binding) }
        rejected { grant.consume(VaultOperation.USE, CredentialId.create(), binding) }
        val different = VaultBinding.create(binding.ownerPackage, binding.protocol,
            binding.host, 23, binding.role, binding.accountBindingId)
        rejected { grant.consume(VaultOperation.USE, id, different) }
        val lease = grant.consume(VaultOperation.USE, id, binding)
        lease.check()
        rejected { grant.consume(VaultOperation.USE, id, binding) }
        rejected { owner.succeeded(attempt) }
        lease.close()
        rejected { lease.check() }
    }

    @Test fun ownerAndWindowAuthoritiesCannotUseEachOthersAttempts() {
        val first = authority()
        val second = authority()
        val attempt = first.begin(VaultOperation.SAVE, id, binding)
        rejected { second.succeeded(attempt) }
        rejected { authority("io.cloudy.desktop").begin(VaultOperation.SAVE, id, binding) }
        val lease = first.succeeded(attempt).consume(VaultOperation.SAVE, id, binding)
        second.cancel()
        lease.check()
        first.setVisible(false)
        rejected { lease.check() }
    }

    @Test fun promptAndUseHaveStrictOriginalDeadlines() {
        val owner = authority()
        val expiredPrompt = owner.begin(VaultOperation.USE, id, binding)
        now += 120000
        rejected { owner.succeeded(expiredPrompt) }
        val attempt = owner.begin(VaultOperation.USE, id, binding)
        now += 119999
        val grant = owner.succeeded(attempt)
        val lease = grant.consume(VaultOperation.USE, id, binding)
        now += 29999
        lease.check()
        now++
        rejected { lease.check() }
    }

    @Test fun hideCancelAndOldCallbacksCannotReviveAuthorization() {
        val owner = authority()
        val hidden = owner.begin(VaultOperation.USE, id, binding)
        owner.setVisible(false)
        rejected { owner.succeeded(hidden) }
        owner.setVisible(true)
        rejected { owner.succeeded(hidden) }
        val cancelled = owner.begin(VaultOperation.USE, id, binding)
        owner.cancel()
        rejected { owner.succeeded(cancelled) }
        val old = owner.begin(VaultOperation.USE, id, binding)
        val current = owner.begin(VaultOperation.DELETE, id, binding)
        rejected { owner.succeeded(old) }
        owner.succeeded(current).consume(VaultOperation.DELETE, id, binding).close()
    }

    @Test fun lateOldLeaseCloseDoesNotRevokeNewAttempt() {
        val owner = authority()
        val old = owner.begin(VaultOperation.USE, id, binding)
        val oldLease = owner.succeeded(old).consume(VaultOperation.USE, id, binding)
        val current = owner.begin(VaultOperation.SAVE, id, binding)
        oldLease.close()
        val lease = owner.succeeded(current).consume(VaultOperation.SAVE, id, binding)
        lease.check()
        owner.cancel()
        rejected { lease.check() }
    }

    @Test fun backwardsClockCannotAuthorizeOrKeepLeaseUsable() {
        val owner = authority()
        val attempt = owner.begin(VaultOperation.USE, id, binding)
        now--
        rejected { owner.succeeded(attempt) }
        now = 2000
        val current = owner.begin(VaultOperation.USE, id, binding)
        val lease = owner.succeeded(current).consume(VaultOperation.USE, id, binding)
        now--
        rejected { lease.check() }
    }
}
