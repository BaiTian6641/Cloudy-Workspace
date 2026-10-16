package io.github.baitian6641.cloudy.platform

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import android.os.Looper
import java.security.MessageDigest
import java.util.Collections

enum class HandoffCallerCode {
    VERIFIED, SELF_CALL, UNKNOWN_RECEIVER, RECEIVER_MISMATCH, PACKAGE_UNAVAILABLE,
    PACKAGE_DISABLED, UID_MISMATCH, AMBIGUOUS_UID, SIGNATURE_MISMATCH, LOOKUP_DENIED,
    WRONG_THREAD, IDENTITY_CHANGED, PERMISSION_DENIED,
}

/** Identity check only; it neither exposes an endpoint nor grants permission to navigate. */
object HandoffCaller {
    private const val CONSOLE = "io.github.baitian6641.cloudy.console"
    private const val DESKTOP = "io.github.baitian6641.cloudy.desktop"

    /**
     * Call synchronously inside the incoming Binder transaction, before clearing calling identity
     * or dispatching work. The eventual endpoint must separately enforce its signature permission.
     * No caller-supplied package name, UID extra or WorkSource attribution is trusted here.
     */
    fun verifyIncoming(context: Context): HandoffCallerCode =
        verifyTransactionUid(context, Binder.getCallingUid())

    /** Capture only in a genuine incoming transaction, before clearing identity or dispatch. */
    fun captureIncoming(context: Context): HandoffCallerCapture = VerifiedHandoffCaller.capture(context)

    /** Reuses only the captured UID and facts; never reads a worker thread's Binder identity. */
    fun recheck(context: Context, caller: VerifiedHandoffCaller): HandoffCallerCode = caller.recheck(context)

    // Test access permits policy rejection checks with explicit UIDs; only verifyIncoming captures
    // transaction identity. Calling this helper in instrumentation is not cross-app Binder evidence.
    internal fun verifyTransactionUid(context: Context, callingUid: Int): HandoffCallerCode {
        val ownUid = Process.myUid()
        if (callingUid == ownUid) return HandoffCallerCode.SELF_CALL
        val receiver = context.packageName
        val counterpart = when (receiver) {
            CONSOLE -> DESKTOP
            DESKTOP -> CONSOLE
            else -> return HandoffCallerCode.UNKNOWN_RECEIVER
        }
        val packages = context.packageManager
        return try {
            val receiverInfo = packages.getPackageInfo(receiver, PackageManager.PackageInfoFlags.of(0))
            val receiverApp = receiverInfo.applicationInfo
                ?: return HandoffCallerCode.PACKAGE_UNAVAILABLE
            if (receiverInfo.packageName != receiver || receiverApp.uid != ownUid) {
                return HandoffCallerCode.RECEIVER_MISMATCH
            }
            if (declaresSharedUid(receiverInfo) || !exclusivePackage(packages, ownUid, receiver)) {
                return HandoffCallerCode.AMBIGUOUS_UID
            }
            val counterpartInfo = packages.getPackageInfo(counterpart, PackageManager.PackageInfoFlags.of(0))
            val counterpartApp = counterpartInfo.applicationInfo
                ?: return HandoffCallerCode.PACKAGE_UNAVAILABLE
            if (counterpartInfo.packageName != counterpart || counterpartApp.uid != callingUid) {
                return HandoffCallerCode.UID_MISMATCH
            }
            if (!receiverApp.enabled || !counterpartApp.enabled) return HandoffCallerCode.PACKAGE_DISABLED
            // Reject shared-UID declarations even if visibility hides other members of that UID.
            if (declaresSharedUid(counterpartInfo) || !exclusivePackage(packages, callingUid, counterpart)) {
                return HandoffCallerCode.AMBIGUOUS_UID
            }
            if (packages.checkSignatures(callingUid, ownUid) != PackageManager.SIGNATURE_MATCH) {
                return HandoffCallerCode.SIGNATURE_MISMATCH
            }
            HandoffCallerCode.VERIFIED
        } catch (_: PackageManager.NameNotFoundException) {
            // Missing, not installed for this user, or invisible packages all fail closed.
            HandoffCallerCode.PACKAGE_UNAVAILABLE
        } catch (_: SecurityException) {
            HandoffCallerCode.LOOKUP_DENIED
        }
    }

    private fun exclusivePackage(packages: PackageManager, uid: Int, expected: String): Boolean {
        val members = packages.getPackagesForUid(uid) ?: return false
        return members.size == 1 && members[0] == expected
    }

    private fun declaresSharedUid(info: PackageInfo): Boolean = info.sharedUserId != null
}


sealed interface HandoffCallerCapture {
    class Verified(val caller: VerifiedHandoffCaller) : HandoffCallerCapture {
        override fun toString(): String = "HandoffCallerCapture.Verified(redacted)"
    }
    data class Rejected(val code: HandoffCallerCode) : HandoffCallerCapture
}

/** An incoming transaction observation, not a navigation grant. No UID-based proof factory exists. */
class VerifiedHandoffCaller private constructor(private val captured: HandoffInstallationPair) {
    val callingUid: Int get() = captured.counterpart.uid
    val callerPackage: String get() = captured.counterpart.packageName
    val receiverPackage: String get() = captured.receiver.packageName

    internal fun recheck(context: Context): HandoffCallerCode {
        if (Looper.myLooper() == Looper.getMainLooper()) return HandoffCallerCode.WRONG_THREAD
        return try {
            if (!handoffOwningProcess(context)) return HandoffCallerCode.RECEIVER_MISMATCH
            val current = readHandoffInstallations(context, callingUid)
            if (current == captured) HandoffCallerCode.VERIFIED else HandoffCallerCode.IDENTITY_CHANGED
        } catch (denied: HandoffIdentityDenied) { denied.code }
        catch (_: Exception) { HandoffCallerCode.LOOKUP_DENIED }
    }

    override fun toString(): String = "VerifiedHandoffCaller(redacted)"

    companion object {
        // Constructor and private installation types cannot be used to mint authority from
        // supplied UIDs. The only construction path observes the current real Binder transaction.
        internal fun capture(context: Context): HandoffCallerCapture {
            val uid = try { Binder.getCallingUidOrThrow() }
            catch (_: IllegalStateException) { return HandoffCallerCapture.Rejected(HandoffCallerCode.SELF_CALL) }
            if (uid == Process.myUid()) return HandoffCallerCapture.Rejected(HandoffCallerCode.SELF_CALL)
            if (Looper.myLooper() == Looper.getMainLooper()) return HandoffCallerCapture.Rejected(HandoffCallerCode.WRONG_THREAD)
            return try {
                if (!handoffOwningProcess(context)) return HandoffCallerCapture.Rejected(HandoffCallerCode.RECEIVER_MISMATCH)
                try { context.enforceCallingPermission(context.packageName + ".permission.FILES_HANDOFF", "HANDOFF_DENIED") }
                catch (_: SecurityException) { return HandoffCallerCapture.Rejected(HandoffCallerCode.PERMISSION_DENIED) }
                HandoffCallerCapture.Verified(VerifiedHandoffCaller(readHandoffInstallations(context, uid)))
            } catch (denied: HandoffIdentityDenied) { HandoffCallerCapture.Rejected(denied.code) }
            catch (_: Exception) { HandoffCallerCapture.Rejected(HandoffCallerCode.LOOKUP_DENIED) }
        }
    }
}

private data class HandoffInstallation(
    val packageName: String, val uid: Int, val version: Long, val firstInstall: Long, val lastUpdate: Long,
    val multipleSigners: Boolean, val currentSigners: List<String>, val signingHistory: List<String>,
)
private data class HandoffInstallationPair(val receiver: HandoffInstallation, val counterpart: HandoffInstallation)
private class HandoffIdentityDenied(val code: HandoffCallerCode) : IllegalStateException(code.name)

private fun handoffOwningProcess(context: Context): Boolean =
    context.packageName in setOf("io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop") &&
        !context.isDeviceProtectedStorage && !context.applicationContext.isDeviceProtectedStorage &&
        context.applicationInfo.uid == Process.myUid() && Application.getProcessName() == context.packageName

private fun readHandoffInstallations(context: Context, uid: Int): HandoffInstallationPair {
    fun policy() {
        val code = HandoffCaller.verifyTransactionUid(context, uid)
        if (code != HandoffCallerCode.VERIFIED) throw HandoffIdentityDenied(code)
    }
    fun read(name: String, expectedUid: Int): HandoffInstallation {
        val info = try { context.packageManager.getPackageInfo(name,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())) }
        catch (_: PackageManager.NameNotFoundException) { throw HandoffIdentityDenied(HandoffCallerCode.PACKAGE_UNAVAILABLE) }
        val app = info.applicationInfo ?: throw HandoffIdentityDenied(HandoffCallerCode.PACKAGE_UNAVAILABLE)
        if (info.packageName != name || app.uid != expectedUid || !app.enabled || info.sharedUserId != null)
            throw HandoffIdentityDenied(HandoffCallerCode.IDENTITY_CHANGED)
        val signing = info.signingInfo ?: throw HandoffIdentityDenied(HandoffCallerCode.PACKAGE_UNAVAILABLE)
        fun digests(values: Array<android.content.pm.Signature>?): List<String> {
            if (values == null || values.isEmpty() || values.size > 128) throw HandoffIdentityDenied(HandoffCallerCode.LOOKUP_DENIED)
            val result = values.map { signature ->
                val bytes = signature.toByteArray()
                if (bytes.isEmpty() || bytes.size > 65_536) throw HandoffIdentityDenied(HandoffCallerCode.LOOKUP_DENIED)
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            }.distinct().sorted()
            return Collections.unmodifiableList(ArrayList(result))
        }
        val multiple = signing.hasMultipleSigners()
        return HandoffInstallation(name, app.uid, info.longVersionCode, info.firstInstallTime, info.lastUpdateTime,
            multiple, digests(signing.apkContentsSigners),
            if (multiple) emptyList() else digests(signing.signingCertificateHistory))
    }
    val receiver = context.packageName
    val counterpart = if (receiver == "io.github.baitian6641.cloudy.console") "io.github.baitian6641.cloudy.desktop"
        else "io.github.baitian6641.cloudy.console"
    fun pair() = HandoffInstallationPair(read(receiver, Process.myUid()), read(counterpart, uid))
    policy()
    val first = pair()
    policy()
    if (pair() != first) throw HandoffIdentityDenied(HandoffCallerCode.IDENTITY_CHANGED)
    // Repeat actual policy after snapshot reads; observed package changes deny conservatively.
    // PM is not atomic with future launch and cannot detect privileged identical-metadata rollback.
    policy()
    return first
}
