package io.github.baitian6641.cloudy.desktop

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.desktop.vnc.VncEngine
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.platform.ConnectionProfiles
import io.github.baitian6641.cloudy.platform.ConnectionProfileCatalog
import io.github.baitian6641.cloudy.platform.ConnectionProfilesResult
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual main-looper/model/JNI/socket failure, no authentication, frame or UI-draw proof.
 * Parent creates/deletes the single exact profile through ordinary UI. This test never writes stores.
 */
@RunWith(AndroidJUnit4::class)
class VncOperationFailureAndroidTest {
    private enum class Stage {
        ARGUMENTS, TARGET_CONTEXT, UID, VERSION, PERMISSION, CATALOG_READ, ROW_BINDING,
        LOOPBACK_BIND, PEER_START, MODEL_A, MODEL_B, ATTACH_A, REFRESH_A, CONNECT_A,
        ACCEPT_A, ADMIT_A, DETACH_A_ATTACH_B, OBSERVE_FOREIGN_A, CLOSE_A,
        REAP_A, REFRESH_B, CONNECT_B, ACCEPT_B, ADMIT_B, REATTACH_A,
        ASSERT_ORIGINAL_FAILURE, ASSERT_ORIGINAL_ATTEMPT_NOT_BORROWED,
        ASSERT_REPLACEMENT_NO_FAILURE, ASSERT_FOREIGN_OWNER_ACTIVE,
        DISCONNECT_B, CLOSE_B, RETIRE_B, PEER_RESULT,
        CATALOG_AFTER, CATALOG_COMPARE, COMPLETE,
    }
    private enum class FailureCategory { ARGUMENT, STATE, ASSERTION, SECURITY, IO, INTERRUPTED, TIMEOUT, PEER, OTHER }
    private enum class CleanupStatus { CALLS_RETURNED, CALL_FAILED }

    private fun category(failure: Throwable): FailureCategory = when (failure) {
        is SecurityException -> FailureCategory.SECURITY
        is IllegalArgumentException -> FailureCategory.ARGUMENT
        is IllegalStateException -> FailureCategory.STATE
        is AssertionError -> FailureCategory.ASSERTION
        is java.io.IOException -> FailureCategory.IO
        is InterruptedException -> FailureCategory.INTERRUPTED
        is java.util.concurrent.TimeoutException -> FailureCategory.TIMEOUT
        is java.util.concurrent.ExecutionException -> FailureCategory.PEER
        else -> FailureCategory.OTHER
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable?>()
        instrumentation.runOnMainSync {
            try { result.set(block()) } catch (failure: Throwable) { error.set(failure) }
        }
        error.get()?.let { throw it }
        return result.get()
    }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 6000
        while (SystemClock.elapsedRealtime() < end) {
            if (condition()) return
            Thread.sleep(20)
        }
        check(condition()) { "VNC_NEGATIVE_WAIT" }
    }

    private fun model(app: Application, store: ViewModelStore): VncSessionModel =
        onMain {
            ViewModelProvider(store, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    check(modelClass == VncSessionModel::class.java)
                    @Suppress("UNCHECKED_CAST")
                    return VncSessionModel(app) as T
                }
            })[VncSessionModel::class.java]
        }

    @Test fun nativeFailureSurvivesForeignReapAndActiveReplacement() {
        val arguments = InstrumentationRegistry.getArguments()
        // Default discovery must not start a listener against an arbitrary user's profile.
        assumeTrue("Explicit owned negative fixture required", arguments.containsKey("cloudyVncNegativeRun"))
        val stores = mutableListOf<ViewModelStore>()
        var server: ServerSocket? = null
        val closeFirst = CountDownLatch(1)
        val closeSecond = CountDownLatch(1)
        val firstAccepted = CountDownLatch(1)
        val secondAccepted = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        var settled = false
        var stage = Stage.ARGUMENTS
        var failedStage: Stage? = null
        var failedCategory: FailureCategory? = null
        var cleanupStatus = CleanupStatus.CALLS_RETURNED
        try {
            val run = UUID.fromString(checkNotNull(arguments.getString("cloudyVncNegativeRun"))).toString()
            check(run == arguments.getString("cloudyVncNegativeRun"))
            val id = UUID.fromString(checkNotNull(arguments.getString("cloudyVncNegativeProfile"))).toString()
            check(id == arguments.getString("cloudyVncNegativeProfile"))
            val revisionText = checkNotNull(arguments.getString("cloudyVncNegativeRevision"))
            val revision = revisionText.toLong()
            check(revision > 0 && revision.toString() == revisionText)
            val expectedUid = checkNotNull(arguments.getString("cloudyVncNegativeUid")).toInt()
            stage = Stage.TARGET_CONTEXT
            val app = instrumentation.targetContext.applicationContext as Application
            stage = Stage.UID
            check(app.packageName == "io.github.baitian6641.cloudy.desktop" && Process.myUid() == expectedUid)
            stage = Stage.VERSION
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            check(info.versionName == "0.1.19" && info.longVersionCode == 20L)
            stage = Stage.PERMISSION
            check(Build.VERSION.SDK_INT < 37 || app.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED)
            stage = Stage.CATALOG_READ
            val catalog = (ConnectionProfiles(app).load() as ConnectionProfilesResult.Success<ConnectionProfileCatalog>).value
            stage = Stage.ROW_BINDING
            val owned = catalog.profiles.single { it.id == id && it.revision == revision }
            check(owned.owner == WindowOwner.DESKTOP && owned.protocol == ConnectionProtocol.VNC)
            check(owned.host == "127.0.0.1" && owned.label == "VNC-negative-$run")
            check(owned.port in 1024..65535)
            // Bind only the exact parent-owned endpoint; no port reuse or external interface.
            stage = Stage.LOOPBACK_BIND
            server = ServerSocket(owned.port, 2, InetAddress.getByName("127.0.0.1"))
            val listener = checkNotNull(server)
            listener.soTimeout = 6000
            stage = Stage.PEER_START
            val peer = executor.submit {
                listener.accept().use {
                    firstAccepted.countDown()
                    check(closeFirst.await(6, TimeUnit.SECONDS))
                    // No RFB, TLS or authentication bytes are ever sent or read by this peer.
                }
                listener.accept().use {
                    secondAccepted.countDown()
                    check(closeSecond.await(6, TimeUnit.SECONDS))
                }
            }
            val firstStore = ViewModelStore().also { stores += it }
            val secondStore = ViewModelStore().also { stores += it }
            stage = Stage.MODEL_A
            val first = model(app, firstStore)
            stage = Stage.MODEL_B
            val second = model(app, secondStore)
            val firstToken = Any()
            val secondToken = Any()
            stage = Stage.ATTACH_A
            onMain {
                check(first.attach(firstToken))
                first.foreground(firstToken, true)
                stage = Stage.REFRESH_A
                first.refresh(firstToken)
            }
            stage = Stage.REFRESH_A
            await { onMain { !first.state.loading && first.state.profiles.any { it.id == id && it.revision == revision } } }
            fun connect(owner: VncSessionModel, token: Any) = onMain {
                val random = SecureRandom()
                // Synthetic bytes only. The peer closes before any authentication exchange.
                owner.connectUserEnded(token, id, revision, ByteArray(32).also(random::nextBytes),
                    byteArrayOf(116, 101, 115, 116), ByteArray(16).also { random.nextBytes(it) }.map { ((it.toInt() and 15) + 97).toByte() }.toByteArray())
            }
            stage = Stage.CONNECT_A
            connect(first, firstToken)
            stage = Stage.ACCEPT_A
            check(firstAccepted.await(6, TimeUnit.SECONDS))
            stage = Stage.ADMIT_A
            await { onMain { first.state.attempt != null } }
            stage = Stage.DETACH_A_ATTACH_B
            onMain {
                // A configuration-transfer-like detach pauses A's poll without inventing an engine.
                first.detach(firstToken)
                check(second.attach(secondToken))
                second.foreground(secondToken, true)
            }
            stage = Stage.OBSERVE_FOREIGN_A
            await { onMain { second.state.engineState != null } }
            stage = Stage.CLOSE_A
            closeFirst.countDown()
            // B actually reaps A after native error/cancel/join/free; A cannot poll while detached.
            stage = Stage.REAP_A
            await { onMain { second.state.engineState == null } }
            stage = Stage.REFRESH_B
            onMain { second.refresh(secondToken) }
            await { onMain { !second.state.loading && second.state.profiles.any { it.id == id } } }
            stage = Stage.CONNECT_B
            connect(second, secondToken)
            stage = Stage.ACCEPT_B
            check(secondAccepted.await(6, TimeUnit.SECONDS))
            stage = Stage.ADMIT_B
            await { onMain { second.state.attempt != null } }
            val replacement = Any()
            stage = Stage.REATTACH_A
            onMain {
                check(first.attach(replacement))
                first.foreground(replacement, true)
                stage = Stage.ASSERT_ORIGINAL_FAILURE
                assertNotNull(first.state.operationFailure)
                stage = Stage.ASSERT_ORIGINAL_ATTEMPT_NOT_BORROWED
                assertNull(first.state.attempt) // Foreign active owner is never borrowed.
                stage = Stage.ASSERT_REPLACEMENT_NO_FAILURE
                assertNull(second.state.operationFailure)
                stage = Stage.ASSERT_FOREIGN_OWNER_ACTIVE
                assertTrue(first.state.engineState !in setOf(null, VncEngine.State.RETIRED, VncEngine.State.FAILED))
                stage = Stage.DISCONNECT_B
                second.disconnect(secondToken)
            }
            stage = Stage.CLOSE_B
            closeSecond.countDown()
            // Null requires the existing central registry to have observed a terminal owner first.
            stage = Stage.RETIRE_B
            await { onMain { second.state.engineState in setOf(null, VncEngine.State.RETIRED, VncEngine.State.FAILED) } }
            stage = Stage.PEER_RESULT
            peer.get(6, TimeUnit.SECONDS)
            stage = Stage.CATALOG_AFTER
            val after = (ConnectionProfiles(app).load() as ConnectionProfilesResult.Success<ConnectionProfileCatalog>).value
            stage = Stage.CATALOG_COMPARE
            assertEquals(catalog.generation, after.generation)
            fun rows(value: io.github.baitian6641.cloudy.platform.ConnectionProfileCatalog) =
                value.profiles.map { listOf(it.id, it.owner.wireId, it.label, it.protocol.wireId, it.host, it.port, it.revision) }
            assertEquals(rows(catalog), rows(after))
            stage = Stage.COMPLETE
            settled = true
        } catch (failure: Throwable) {
            // Keep only the first fixed origin. Never retain or print the throwable/cause/message.
            failedStage = stage
            failedCategory = category(failure)
        } finally {
            closeFirst.countDown()
            closeSecond.countDown()
            var cleanupFailed = false
            try { server?.close() } catch (_: Throwable) { cleanupFailed = true }
            try { onMain { stores.forEach { it.clear() } } } catch (_: Throwable) { cleanupFailed = true }
            try { executor.shutdownNow() } catch (_: Throwable) { cleanupFailed = true }
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) cleanupFailed = true
            } catch (_: Throwable) { cleanupFailed = true }
            cleanupStatus = if (cleanupFailed) CleanupStatus.CALL_FAILED else CleanupStatus.CALLS_RETURNED
        }
        // Cleanup-call status is not proof of native retirement. It cannot replace the first failure.
        if (failedStage != null) {
            throw AssertionError("VNC_NEGATIVE_FAILED_PRESERVE_STATE:${failedStage.name}:${checkNotNull(failedCategory).name}:${cleanupStatus.name}")
        }
        if (!settled || cleanupStatus == CleanupStatus.CALL_FAILED) {
            throw AssertionError("VNC_NEGATIVE_CLEANUP_UNCERTAIN_PRESERVE_STATE:${stage.name}:${cleanupStatus.name}")
        }
    }
}
