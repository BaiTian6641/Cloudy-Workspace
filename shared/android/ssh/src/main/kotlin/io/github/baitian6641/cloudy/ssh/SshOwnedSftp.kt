package io.github.baitian6641.cloudy.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import net.schmizz.sshj.connection.channel.direct.SessionFactory
import net.schmizz.sshj.sftp.SFTPEngine
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

/** Selected-pin public/protected ownership hooks; SSHJ continues all SFTP parsing and encoding. */
internal class SshOwnedSftp(factory: SshTrackedSftpFactory) : SFTPEngine(factory) {
    val packetReaderThread: Thread get() = reader
}

/** Publish the actual channel before open/subsystem requests, including constructor failures. */
internal class SshTrackedSftpFactory(private val client: SSHClient) : SessionFactory by client {
    private val channel = AtomicReference<SessionChannel?>(null)
    override fun startSession(): Session {
        if (!client.isConnected || !client.isAuthenticated) throw IOException("SFTP_TRANSPORT_UNAVAILABLE")
        // Exact public construction used by SSHClient.startSession(), with ownership published
        // before open() rather than after it returns. No custom channel protocol is introduced.
        val actual = SessionChannel(client.connection, client.remoteCharset)
        if (!channel.compareAndSet(null, actual)) throw IOException("SECOND_SFTP_CHANNEL")
        actual.open()
        return object : Session by actual {
            override fun startSubsystem(name: String): Session.Subsystem {
                if (name != "sftp") throw IOException("SFTP_ONLY")
                val subsystem = actual.startSubsystem(name)
                val input = SshSftpFramingInput(subsystem.inputStream)
                return object : Session.Subsystem by subsystem {
                    override fun getInputStream(): InputStream = input
                }
            }
        }
    }
    fun close() { channel.get()?.close() }
    fun isClosed(): Boolean = channel.get()?.isOpen != true
}
