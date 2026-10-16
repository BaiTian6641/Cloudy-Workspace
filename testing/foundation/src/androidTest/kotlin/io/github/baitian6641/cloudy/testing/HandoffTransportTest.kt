package io.github.baitian6641.cloudy.testing

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.domain.FilesResourceReference
import io.github.baitian6641.cloudy.domain.HandoffEnvelope
import io.github.baitian6641.cloudy.platform.FilesConsumption
import io.github.baitian6641.cloudy.platform.FilesDiscoveryStatus
import io.github.baitian6641.cloudy.platform.FilesHandoffWire
import io.github.baitian6641.cloudy.platform.FilesSubmitStatus
import io.github.baitian6641.cloudy.platform.HandoffEndpoint
import io.github.baitian6641.cloudy.platform.HandoffEndpointResult
import io.github.baitian6641.cloudy.platform.HandoffJson
import io.github.baitian6641.cloudy.platform.ICloudyFilesHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Real product UID -> actual sibling Binder. Execution is opt-in and recorded separately.
 * No BAL opt-in, Activity launch, fabricated caller proof or positive resource/auth claim.
 * Parent must separately verify BOTH receiver ledger/catalog baselines before/after the run.
 */
@RunWith(AndroidJUnit4::class)
class HandoffTransportTest {
    @Test fun actualSiblingDiscoveryReturnsExistingEmptyCatalog() = exercise { endpoint ->
        val reply = FilesHandoffWire.decodeDiscovery(endpoint.discover(1, ""))
        assertEquals(FilesDiscoveryStatus.OK, reply.status)
        assertTrue(reply.locations.isEmpty())
        assertEquals("", reply.nextAfterResourceId)
    }

    @Test fun actualSiblingRejectsUnsupportedDiscoveryVersion() = exercise { endpoint ->
        assertEquals(FilesDiscoveryStatus.UNSUPPORTED_VERSION,
            FilesHandoffWire.decodeDiscovery(endpoint.discover(2, "")).status)
    }

    @Test fun actualSiblingRejectsMalformedDiscoveryCursor() = exercise { endpoint ->
        assertEquals(FilesDiscoveryStatus.INVALID_INPUT,
            FilesHandoffWire.decodeDiscovery(endpoint.discover(1, "invalid")).status)
    }

    @Test fun actualSiblingRejectsMalformedEnvelopeWithoutConsumption() = exercise { endpoint ->
        val reply = FilesHandoffWire.decodeSubmit(endpoint.submit("{}".toByteArray()))
        assertEquals(FilesSubmitStatus.INVALID_ENVELOPE, reply.status)
        assertEquals(FilesConsumption.NONE, reply.consumption)
    }

    @Test fun actualSiblingRejectsBothKnownUnsupportedActionsWithoutConsumption() = exercise { endpoint ->
        for (action in listOf("open-console", "open-desktop")) {
            val reply = FilesHandoffWire.decodeSubmit(endpoint.submit(envelope(action)))
            assertEquals(FilesSubmitStatus.UNSUPPORTED_ACTION, reply.status)
            assertEquals(FilesConsumption.NONE, reply.consumption)
        }
    }

    @Test fun actualSiblingRejectsUnregisteredFilesTupleWithoutConsumption() = exercise { endpoint ->
        val reply = FilesHandoffWire.decodeSubmit(endpoint.submit(envelope("open-files")))
        assertEquals(FilesSubmitStatus.RESOURCE_UNAVAILABLE, reply.status)
        assertEquals(FilesConsumption.NONE, reply.consumption)
    }

    @Test fun actualSiblingRejectsTrailingParcelData() = exercise { endpoint ->
        // Establish admission on this actual binding before crediting a malformed-data denial.
        val baseline = FilesHandoffWire.decodeDiscovery(endpoint.discover(1, ""))
        assertEquals(FilesDiscoveryStatus.OK, baseline.status)
        assertTrue(baseline.locations.isEmpty())
        assertEquals("", baseline.nextAfterResourceId)
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(ICloudyFilesHandoff.DESCRIPTOR)
            data.writeInt(1); data.writeString(""); data.writeInt(7)
            assertTrue(endpoint.asBinder().transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0))
            org.junit.Assert.assertThrows(SecurityException::class.java) { reply.readException() }
        } finally { data.recycle(); reply.recycle() }
    }

    private fun envelope(action: String): ByteArray {
        val ids = FilesResourceReference.createNew()
        val now = Instant.now()
        return HandoffJson.encode(HandoffEnvelope.create(1, UUID.randomUUID().toString(), now.toString(),
            now.plusSeconds(30).toString(), action, ids.hostId, ids.environmentId, ids.workspaceId, ids.resourceId))
    }

    private fun exercise(block: (ICloudyFilesHandoff) -> Unit) {
        // Default invocation touches no app storage, endpoint or package lookup.
        assumeTrue(InstrumentationRegistry.getArguments().getString("cloudyFilesHandoffTransport") == "enabled")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        var stage = "IDENTITY"
        var failed = false
        var attempted = false
        var bound = false
        val connected = CountDownLatch(1)
        val binder = AtomicReference<IBinder?>()
        val observedComponent = AtomicReference<ComponentName?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                observedComponent.set(name); binder.set(service); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { binder.set(null); connected.countDown() }
            override fun onBindingDied(name: ComponentName) { binder.set(null); connected.countDown() }
            override fun onNullBinding(name: ComponentName) { binder.set(null); connected.countDown() }
        }
        try {
            assertTrue(context.packageName in setOf("io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop"))
            assertEquals(context.applicationInfo.uid, Process.myUid())
            assertEquals(context.packageName, Application.getProcessName())
            assertTrue(Looper.myLooper() != Looper.getMainLooper())
            // Both apps' existing initialized/empty catalog is an external parent preflight.
            // Refuse local absence here rather than initialize a test baseline by accident.
            assertTrue(File(context.noBackupFilesDir, "files-resources-v1/resources.db").isFile)
            val localBaseline = ledgerSnapshot(context)
            stage = "ENDPOINT"
            val verified = HandoffEndpoint.verify(context)
            assertTrue(verified is HandoffEndpointResult.Verified)
            val component = (verified as HandoffEndpointResult.Verified).component
            stage = "BIND"
            instrumentation.runOnMainSync {
                attempted = true
                // Deliberately no BIND_ALLOW_ACTIVITY_STARTS: every case is discovery/denial only.
                bound = context.bindService(Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE)
            }
            assertTrue(bound)
            assertTrue(connected.await(4, TimeUnit.SECONDS))
            assertEquals(component, observedComponent.get())
            val remote = binder.get()
            assertTrue(remote != null && remote.isBinderAlive)
            stage = "TRANSACTION"
            // Real remote transaction runs on this worker test thread. Never substitute a local Stub.
            block(ICloudyFilesHandoff.Stub.asInterface(remote))
            stage = "LOCAL_LEDGER"
            assertTrue(localBaseline == ledgerSnapshot(context))
        } catch (failure: Throwable) {
            failed = true
            // Fixed categories retain useful boundary evidence without exposing remote text,
            // payloads or an attached exception cause in instrumentation output.
            val category = when (failure) {
                is SecurityException -> "SECURITY"
                is android.os.DeadObjectException -> "DEAD_OBJECT"
                is android.os.RemoteException -> "REMOTE"
                is android.os.BadParcelableException -> "BAD_PARCEL"
                is AssertionError -> "ASSERTION"
                else -> "OTHER"
            }
            throw AssertionError("HANDOFF_TRANSPORT_FAILED_${stage}_$category")
        } finally {
            var retired = !attempted
            if (attempted) instrumentation.runOnMainSync {
                try { context.unbindService(connection); retired = true }
                catch (_: IllegalArgumentException) { retired = !bound }
                catch (_: Exception) { retired = false }
            }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("cloudyFilesHandoffTransportBinding", if (retired) "RETIRED" else "UNCERTAIN")
                putString("cloudyFilesHandoffTransportCase", if (!failed && retired) "PASSED" else "FAILED")
            })
            if (!retired) throw AssertionError("HANDOFF_TRANSPORT_BINDING_UNCERTAIN")
        }
    }

    /** Own-app observation only. It cannot establish the sibling receiver's ledger preservation. */
    private fun ledgerSnapshot(context: Context): Map<String, String> {
        val root = context.noBackupFilesDir
        val result = linkedMapOf<String, String>()
        for (entry in root.listFiles().orEmpty().filter { it.name.startsWith("handoff-replay-v1") }.sortedBy { it.name }) {
            val paths = if (entry.isDirectory) entry.walkTopDown().toList() else listOf(entry)
            for (path in paths) {
                assertTrue(!java.nio.file.Files.isSymbolicLink(path.toPath()))
                val relative = path.relativeTo(root).path
                result[relative] = if (path.isDirectory) "DIRECTORY" else {
                    assertTrue(path.length() <= 4L * 1024 * 1024)
                    MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
                }
            }
        }
        return result
    }
}
