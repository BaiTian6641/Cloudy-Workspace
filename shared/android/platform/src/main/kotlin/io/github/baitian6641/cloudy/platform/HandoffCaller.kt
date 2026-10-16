package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process

enum class HandoffCallerCode {
    VERIFIED, SELF_CALL, UNKNOWN_RECEIVER, RECEIVER_MISMATCH, PACKAGE_UNAVAILABLE,
    PACKAGE_DISABLED, UID_MISMATCH, AMBIGUOUS_UID, SIGNATURE_MISMATCH, LOOKUP_DENIED,
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
