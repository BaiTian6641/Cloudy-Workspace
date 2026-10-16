package io.github.baitian6641.cloudy.platform

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/** Bound only, in each product's own process. Every transaction authenticates the actual caller. */
class CloudyFilesHandoffService : Service() {
    private val receiver by lazy { FilesHandoffReceiver(this) }
    private val endpoint = object : ICloudyFilesHandoff.Stub() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            capture()
            FilesHandoffParcel.validate(code, data, flags, reply != null)
            // Build-tools36 omits enforceNoDataAvail: the bounded validation copy checks that
            // separately. The original cursor stays untouched for generated interface decoding.
            return super.onTransact(code, data, reply, flags)
        }

        override fun discover(protocolVersion: Int, afterResourceId: String?): ByteArray {
            val caller = capture()
            if (afterResourceId == null || afterResourceId.length > 36) {
                return FilesHandoffWire.encodeDiscovery(FilesDiscoveryReply.failure(FilesDiscoveryStatus.INVALID_INPUT))
            }
            val identity = Binder.clearCallingIdentity()
            try { return FilesHandoffWire.encodeDiscovery(receiver.discover(caller, protocolVersion, afterResourceId)) }
            finally { Binder.restoreCallingIdentity(identity) }
        }

        override fun submit(envelopeUtf8: ByteArray?): ByteArray {
            val caller = capture()
            if (envelopeUtf8 == null || envelopeUtf8.size > HandoffJson.MAX_BYTES) {
                return FilesHandoffWire.encodeSubmit(FilesSubmitReply.create(FilesSubmitStatus.INVALID_ENVELOPE, FilesConsumption.NONE))
            }
            val identity = Binder.clearCallingIdentity()
            try { return FilesHandoffWire.encodeSubmit(receiver.submit(caller, envelopeUtf8)) }
            finally { Binder.restoreCallingIdentity(identity) }
        }
    }

    override fun onBind(intent: Intent?): IBinder = endpoint

    private fun capture(): VerifiedHandoffCaller {
        if (packageName !in setOf("io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop")) deny()
        // OrSelf would admit a local asInterface shortcut. Such calls carry no sibling authority.
        enforceCallingPermission("$packageName.permission.FILES_HANDOFF", "Files handoff denied")
        return when (val result = HandoffCaller.captureIncoming(this)) {
            is HandoffCallerCapture.Verified -> result.caller
            is HandoffCallerCapture.Rejected -> deny()
        }
    }

    private fun deny(): Nothing = throw SecurityException("Files handoff denied")
}
