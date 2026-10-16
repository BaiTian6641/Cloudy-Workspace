package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.common.Message
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.AccountResource
import org.junit.Assert.*
import org.junit.Test

class SshStrictPasswordMethodTest {
    @Test fun passwordChangeIsRefusedBeforeParsingOrAccessingUpstreamParameters() {
        val credentials = SshPasswordCredentials.create("user".toCharArray(), "secret".toCharArray())
        val material = checkNotNull(credentials.take())
        try {
            val finder = SshOneReleasePasswordFinder(material, SshObservationCancellation()) { true }
            val method = SshStrictPasswordMethod(finder) { true }
            // Empty packet and absent params prove the superclass prompt/change path is not used.
            assertThrows(UserAuthException::class.java) { method.handle(Message.USERAUTH_60, SSHPacket()) }
            assertTrue(method.passwordChangeRefused.get())
            assertFalse(method.shouldRetry())
            assertArrayEquals("secret".toCharArray(), material.password)
        } finally { material.close(); credentials.close() }
    }

    @Test fun finderReleasesExactlyOnceAndNeverReturnsNullAsCancellation() {
        val material = checkNotNull(SshPasswordCredentials.create("user".toCharArray(), "secret".toCharArray()).take())
        try {
            val cancellation = SshObservationCancellation()
            val finder = SshOneReleasePasswordFinder(material, cancellation) { true }
            val resource = AccountResource("unused", "unused.invalid")
            assertSame(material.password, finder.reqPassword(resource))
            assertThrows(UserAuthException::class.java) { finder.reqPassword(resource) }
            assertFalse(finder.shouldRetry(resource))
            finder.erasePassword()
            assertTrue(material.password.all { it == '\u0000' })
        } finally { material.close() }
    }

    @Test fun cancelledExpiredAndInactiveRequestsCannotReleasePassword() {
        val resource = AccountResource("unused", "unused.invalid")
        for (cancelled in listOf(false, true)) {
            val material = checkNotNull(SshPasswordCredentials.create("user".toCharArray(), "secret".toCharArray()).take())
            try {
                val cancellation = SshObservationCancellation()
                if (cancelled) cancellation.cancel()
                val finder = SshOneReleasePasswordFinder(material, cancellation) { cancelled }
                assertThrows(UserAuthException::class.java) { finder.reqPassword(resource) }
                val method = SshStrictPasswordMethod(finder) { false }
                assertThrows(UserAuthException::class.java) { method.buildReq() }
            } finally { material.close() }
        }
    }
}
