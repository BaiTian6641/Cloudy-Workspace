package io.github.baitian6641.cloudy.platform

import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.Process
import io.github.baitian6641.cloudy.domain.FilesResourceReference
import io.github.baitian6641.cloudy.domain.FilesResourceRegistration
import io.github.baitian6641.cloudy.domain.HandoffAction
import io.github.baitian6641.cloudy.domain.HandoffFreshness
import java.time.Instant
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Off-main admission only. A reservation precedes navigation and never promises a visible window. */
internal class FilesHandoffReceiver(context: Context) {
    private val application = context.applicationContext
    private val deviceProtected = context.isDeviceProtectedStorage || application.isDeviceProtectedStorage
    private val suppliedPackage = context.packageName

    fun discover(caller: VerifiedHandoffCaller, version: Int, afterResourceId: String): FilesDiscoveryReply {
        if (!eligible() || HandoffCaller.recheck(application, caller) != HandoffCallerCode.VERIFIED) {
            return FilesDiscoveryReply.failure(FilesDiscoveryStatus.DENIED)
        }
        if (version != 1) return FilesDiscoveryReply.failure(FilesDiscoveryStatus.UNSUPPORTED_VERSION)
        if (!permit.tryAcquire()) return FilesDiscoveryReply.failure(FilesDiscoveryStatus.BUSY)
        return try {
            when (val result = FilesResourceStore(application).discoverShared(afterResourceId)) {
                is FilesResourceResult.Success -> {
                    if (HandoffCaller.recheck(application, caller) != HandoffCallerCode.VERIFIED) {
                        FilesDiscoveryReply.failure(FilesDiscoveryStatus.DENIED)
                    } else FilesDiscoveryReply.success(result.value.locations, result.value.nextAfterResourceId)
                }
                is FilesResourceResult.Failure -> FilesDiscoveryReply.failure(when (result.code) {
                    FilesResourceCode.INVALID_INPUT -> FilesDiscoveryStatus.INVALID_INPUT
                    FilesResourceCode.BUSY -> FilesDiscoveryStatus.BUSY
                    else -> FilesDiscoveryStatus.UNAVAILABLE
                })
            }
        } catch (_: Exception) { FilesDiscoveryReply.failure(FilesDiscoveryStatus.UNAVAILABLE) }
        finally { permit.release() }
    }

    fun submit(caller: VerifiedHandoffCaller, bytes: ByteArray): FilesSubmitReply {
        if (!eligible() || HandoffCaller.recheck(application, caller) != HandoffCallerCode.VERIFIED) {
            return reply(FilesSubmitStatus.DENIED)
        }
        if (!permit.tryAcquire()) return reply(FilesSubmitStatus.BUSY)
        var admissionAttempted = false
        var reserved = false
        var transferred = false
        var token: String? = null
        try {
            val envelope = try { HandoffJson.decode(bytes) }
            catch (_: Exception) { return reply(FilesSubmitStatus.INVALID_ENVELOPE) }
            if (envelope.action != HandoffAction.OPEN_FILES) return reply(FilesSubmitStatus.UNSUPPORTED_ACTION)
            val reference = try { FilesResourceReference.restore(envelope.hostId, envelope.environmentId,
                envelope.workspaceId, envelope.resourceId) }
            catch (_: Exception) { return reply(FilesSubmitStatus.RESOURCE_UNAVAILABLE) }
            if (!SshAuthenticationRegistry.lock.tryLock()) return reply(FilesSubmitStatus.BUSY)
            val registration: FilesResourceRegistration
            try {
                registration = when (val result = FilesResourceStore(application).resolve(reference)) {
                    is FilesResourceResult.Success -> result.value
                    is FilesResourceResult.Failure -> return reply(if (result.code == FilesResourceCode.BUSY)
                        FilesSubmitStatus.BUSY else FilesSubmitStatus.RESOURCE_UNAVAILABLE)
                }
                freshness(envelope.freshAt(Instant.now()))?.let { return reply(it) }
                // An unavailable ledger may have committed before failure: never assert NONE.
                admissionAttempted = true
                when (HandoffReplayLedger(application).reserve(envelope)) {
                    HandoffReplayResult.RESERVED -> reserved = true
                    HandoffReplayResult.DUPLICATE -> return reply(FilesSubmitStatus.DUPLICATE)
                    HandoffReplayResult.EXPIRED -> return reply(FilesSubmitStatus.EXPIRED)
                    HandoffReplayResult.NOT_YET_VALID -> return reply(FilesSubmitStatus.NOT_YET_VALID)
                    HandoffReplayResult.CLOCK_ROLLBACK -> return reply(FilesSubmitStatus.CLOCK_ROLLBACK)
                    HandoffReplayResult.CAPACITY_REACHED -> return reply(FilesSubmitStatus.CAPACITY)
                    HandoffReplayResult.BUSY -> return reply(FilesSubmitStatus.BUSY)
                    HandoffReplayResult.WRONG_THREAD, HandoffReplayResult.STORE_UNAVAILABLE ->
                        return reply(FilesSubmitStatus.ADMISSION_UNCONFIRMED)
                }
                if (envelope.freshAt(Instant.now()) != HandoffFreshness.CURRENT) {
                    return reply(FilesSubmitStatus.ADMITTED_NOT_DISPATCHED)
                }
                val current = FilesResourceStore(application).resolve(reference)
                if (current !is FilesResourceResult.Success || current.value != registration) {
                    return reply(FilesSubmitStatus.ADMITTED_NOT_DISPATCHED)
                }
            } finally { SshAuthenticationRegistry.lock.unlock() }
            // PackageManager and certificate hashing stay outside SSH coordination; that gate
            // cannot make package installation atomic with later navigation.
            if (HandoffCaller.recheck(application, caller) != HandoffCallerCode.VERIFIED ||
                envelope.freshAt(Instant.now()) != HandoffFreshness.CURRENT) {
                return reply(FilesSubmitStatus.ADMITTED_NOT_DISPATCHED)
            }
            val issuedToken = when (val issued = FilesLocationTickets.issueHandoff(application, registration, caller, envelope)) {
                is FilesResourceResult.Success -> issued.value
                is FilesResourceResult.Failure -> return reply(FilesSubmitStatus.ADMITTED_NOT_DISPATCHED)
            }
            token = issuedToken
            val retired = AtomicBoolean(false)
            val observation = Any()
            FilesPositiveObservation.start(application, FilesPositiveObservation.Event.DISPATCH_STARTED, observation)
            // Dispatch owns the permit until its actual main-thread work retires, even if its
            // acknowledgment times out. It may retire synchronously before returning.
            transferred = true
            val status = FilesHandoffDispatch.dispatch(application, issuedToken, envelope) {
                if (retired.compareAndSet(false, true)) {
                    permit.release()
                    FilesPositiveObservation.record(FilesPositiveObservation.Event.DISPATCH_RETIRED, observation)
                }
            }
            return reply(status)
        } catch (_: Exception) {
            // An unexpected dispatch escape retains capacity: retirement is no longer proven.
            return reply(if (transferred) FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED
                else if (reserved) FilesSubmitStatus.ADMITTED_NOT_DISPATCHED
                else if (admissionAttempted) FilesSubmitStatus.ADMISSION_UNCONFIRMED else FilesSubmitStatus.RESOURCE_UNAVAILABLE)
        } finally {
            if (!transferred) {
                token?.let(FilesLocationTickets::discard)
                permit.release()
            }
        }
    }

    private fun eligible(): Boolean = !deviceProtected && Looper.myLooper() != Looper.getMainLooper() &&
        suppliedPackage == application.packageName && application.packageName in PACKAGES &&
        application.applicationInfo.uid == Process.myUid() && Application.getProcessName() == application.packageName

    private fun freshness(value: HandoffFreshness): FilesSubmitStatus? = when (value) {
        HandoffFreshness.CURRENT -> null
        HandoffFreshness.EXPIRED -> FilesSubmitStatus.EXPIRED
        HandoffFreshness.NOT_YET_VALID -> FilesSubmitStatus.NOT_YET_VALID
    }

    companion object {
        private val permit = Semaphore(1)
        private val PACKAGES = setOf("io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop")
        private fun reply(status: FilesSubmitStatus): FilesSubmitReply = FilesSubmitReply.create(status, when (status) {
            FilesSubmitStatus.DISPATCH_REQUESTED, FilesSubmitStatus.ADMITTED_NOT_DISPATCHED,
            FilesSubmitStatus.ADMITTED_DELIVERY_UNCONFIRMED -> FilesConsumption.RESERVED
            FilesSubmitStatus.ADMISSION_UNCONFIRMED -> FilesConsumption.UNKNOWN
            else -> FilesConsumption.NONE
        })
    }
}
