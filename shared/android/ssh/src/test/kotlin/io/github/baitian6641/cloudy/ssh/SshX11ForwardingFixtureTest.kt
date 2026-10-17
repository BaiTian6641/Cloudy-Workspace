package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.SSHClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Real-fixture X11 forwarding qualification on the primary host: a private OpenSSH server and a
 * private Xvfb, both fixture-owned. Runs only when CLOUDY_X11_FIXTURE points at the fixture
 * properties emitted by qualification/probes/x11-forward-host; skipped (never failed) otherwise.
 */
@RunWith(JUnit4::class)
class SshX11ForwardingFixtureTest {
    private data class Fixture(
        val host: String, val port: Int, val user: String, val keyPath: String,
        val hostKeyFingerprint: String, val display: Int, val xauthorityLocal: String,
        val xauthorityRemote: String, val localSocket: String, val hostname: String,
        val display2: Int?, val localSocket2: String?,
    )

    private val fixture: Fixture? = System.getenv("CLOUDY_X11_FIXTURE")?.let { path ->
        val properties = Properties().apply { File(path).inputStream().use { load(it) } }
        Fixture(
            properties.getProperty("host"), properties.getProperty("port").toInt(),
            properties.getProperty("user"), properties.getProperty("keyPath"),
            properties.getProperty("hostKeyFingerprint"), properties.getProperty("display").toInt(),
            properties.getProperty("xauthorityLocal"), properties.getProperty("xauthorityRemote"),
            properties.getProperty("localSocket"), properties.getProperty("hostname"),
            properties.getProperty("display2")?.toInt(), properties.getProperty("localSocket2"),
        )
    }

    @Before fun requireFixture() = assumeTrue("no live X11 fixture", fixture != null)

    private fun authorityProvider(xauthority: String, display: Int, hostname: String) =
        SshX11AuthorityProvider {
            SshXauthorityCodec.findCookie(
                SshXauthorityCodec.parse(Files.readAllBytes(Paths.get(xauthority))), display, hostname)
        }

    private fun connect(): SSHClient {
        val target = checkNotNull(fixture)
        val client = SSHClient(SshObservationPolicy.create(SshReaderRetirement()))
        client.addHostKeyVerifier(target.hostKeyFingerprint)
        client.connect(target.host, target.port)
        client.authPublickey(target.user, target.keyPath)
        return client
    }

    private fun execXdpyinfo(client: SSHClient, forwarding: SshX11Forwarding): Triple<Int, String, String> {
        val target = checkNotNull(fixture)
        client.startSession().use { session ->
            session.setEnvVar("XAUTHORITY", target.xauthorityRemote)
            forwarding.enableOn(session)
            val command = session.exec("xdpyinfo")
            val output = command.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val errors = command.errorStream.readBytes().toString(StandardCharsets.UTF_8)
            command.join(30, TimeUnit.SECONDS)
            return Triple(command.exitStatus ?: -1, output, errors)
        }
    }

    @Test fun forwardedX11ReachesTheLocalServerWithSubstitutedCookie() {
        val target = checkNotNull(fixture)
        connect().use { client ->
            val forwarding = SshX11Forwarding.attach(client,
                SshX11UnixEndpoint(Paths.get(target.localSocket)),
                authorityProvider(target.xauthorityLocal, target.display, target.hostname))
            try {
                val (exit, output, errors) = execXdpyinfo(client, forwarding)
                // sshd allocates 10, 11, … per concurrent session; bind the check to the display
                // the server actually published rather than assuming the first offset.
                val negotiated = Regex("localhost:(1[0-9]+)\\.0").find(output)
                val display = negotiated?.groupValues?.get(1)?.toIntOrNull() ?: 10
                val stored = Files.readAllBytes(Paths.get(target.xauthorityRemote))
                val storedCookie = SshXauthorityCodec.findCookie(
                    SshXauthorityCodec.parse(stored), display, target.hostname)
                val cookieBound = storedCookie != null &&
                    java.security.MessageDigest.isEqual(storedCookie, forwarding.fakeCookie)
                assertEquals("exit=$exit stdout=[$output] stderr=[$errors] " +
                    "fakeCookieBoundToRemote=$cookieBound", 0, exit)
                assertTrue(output.contains("name of display:") && negotiated != null)
                assertTrue(output.contains("version number:") && output.contains("11.0"))
                assertTrue("fakeCookieBoundToRemote=$cookieBound", cookieBound)
            } finally {
                forwarding.stop()
            }
            assertEquals(0, forwarding.activeChannels())
        }
    }

    @Test fun wrongCookieIsRejectedByTheLocalServer() {
        val target = checkNotNull(fixture)
        connect().use { client ->
            val forwarding = SshX11Forwarding.attach(client,
                SshX11UnixEndpoint(Paths.get(target.localSocket)),
                authority = { ByteArray(16) { 0x55 } })
            try {
                val (exit, output, _) = execXdpyinfo(client, forwarding)
                assertTrue(exit != 0)
                assertFalse(output.contains("version number"))
            } finally {
                forwarding.stop()
            }
        }
    }

    @Test fun twoForwardingOwnersStayIsolatedOnTheirOwnDisplays() {
        val target = checkNotNull(fixture)
        assumeTrue("fixture has no second display", target.display2 != null)
        // Two independent SSH connections, each forwarding to its own X server display.
        connect().use { first ->
            connect().use { second ->
                val firstForwarding = SshX11Forwarding.attach(first,
                    SshX11UnixEndpoint(Paths.get(target.localSocket)),
                    authorityProvider(target.xauthorityLocal, target.display, target.hostname))
                val secondForwarding = SshX11Forwarding.attach(second,
                    SshX11UnixEndpoint(Paths.get(target.localSocket2!!)),
                    authorityProvider(target.xauthorityLocal, target.display2!!, target.hostname))
                try {
                    val (exitA, outA, errA) = execXdpyinfo(first, firstForwarding)
                    val (exitB, outB, errB) = execXdpyinfo(second, secondForwarding)
                    assertEquals("A: stdout=[$outA] stderr=[$errA]", 0, exitA)
                    assertEquals("B: stdout=[$outB] stderr=[$errB]", 0, exitB)
                    // Each context reached its own server: fixture displays differ in geometry.
                    assertTrue(outA.contains("1280x800"))
                    assertTrue(outB.contains("640x480"))
                    assertFalse(outA.contains("640x480"))
                    assertFalse(outB.contains("1280x800"))
                } finally {
                    firstForwarding.stop()
                    secondForwarding.stop()
                }
            }
        }
    }

    @Test fun stoppingTheOwnerEndsTheForwardedSession() {
        val target = checkNotNull(fixture)
        connect().use { client ->
            val forwarding = SshX11Forwarding.attach(client,
                SshX11UnixEndpoint(Paths.get(target.localSocket)),
                authorityProvider(target.xauthorityLocal, target.display, target.hostname))
            client.startSession().use { session ->
                session.setEnvVar("XAUTHORITY", target.xauthorityRemote)
                forwarding.enableOn(session)
                // xterm holds its X connection open until the forwarded channel dies.
                val command = session.exec("xterm -geometry 80x24+0+0")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                while (forwarding.activeChannels() == 0 && System.nanoTime() < deadline) {
                    Thread.sleep(50)
                }
                assertEquals(1, forwarding.activeChannels())
                forwarding.stop()
                command.join(30, TimeUnit.SECONDS)
                // The remote client must actually end, with a failure, once its channel dies.
                assertTrue(command.isEOF || command.exitStatus != null)
                assertTrue(command.exitStatus != null && command.exitStatus != 0)
                assertEquals(0, forwarding.activeChannels())
            }
        }
    }

    @Test fun missingAuthorityClosesTheChannelBeforeAnyLocalContact() {
        val target = checkNotNull(fixture)
        var opens = 0
        connect().use { client ->
            val counting = object : SshX11ServerEndpoint {
                override fun open(): SshX11Duplex {
                    opens++
                    return SshX11UnixEndpoint(Paths.get(target.localSocket)).open()
                }
            }
            val forwarding = SshX11Forwarding.attach(client, counting, authority = { null })
            try {
                val (exit, output, _) = execXdpyinfo(client, forwarding)
                assertTrue(exit != 0)
                assertFalse(output.contains("version number"))
                assertEquals(0, opens)
            } finally {
                forwarding.stop()
            }
        }
    }
}
