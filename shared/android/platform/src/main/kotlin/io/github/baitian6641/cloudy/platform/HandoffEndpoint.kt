package io.github.baitian6641.cloudy.platform

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ServiceInfo
import android.os.Looper
import android.os.Process

enum class HandoffEndpointCode {
    VERIFIED, WRONG_THREAD, UNKNOWN_SENDER, WRONG_OWNER, COUNTERPART_UNAVAILABLE,
    CALLER_POLICY_REJECTED, SERVICE_MISMATCH, SERVICE_DISABLED, SERVICE_NOT_EXPORTED,
    PROCESS_MISMATCH, PERMISSION_MISMATCH, PERMISSION_NOT_SIGNATURE, PERMISSION_DENIED, LOOKUP_DENIED,
}

sealed interface HandoffEndpointResult {
    /** A binding target, not proof of a remote transaction or authority to navigate. */
    class Verified internal constructor(val component: ComponentName, val permission: String) : HandoffEndpointResult {
        override fun toString(): String = "HandoffEndpointResult.Verified(redacted)"
    }
    data class Rejected(val code: HandoffEndpointCode) : HandoffEndpointResult
}

/** Off-main sender preflight. Repeat on delayed use; package lookup and bind cannot be atomic. */
object HandoffEndpoint {
    const val SERVICE_CLASS = "io.github.baitian6641.cloudy.platform.CloudyFilesHandoffService"
    private const val CONSOLE = "io.github.baitian6641.cloudy.console"
    private const val DESKTOP = "io.github.baitian6641.cloudy.desktop"

    fun verify(context: Context): HandoffEndpointResult {
        if (Looper.myLooper() == Looper.getMainLooper()) return rejected(HandoffEndpointCode.WRONG_THREAD)
        val sender = context.packageName
        val receiver = when (sender) {
            CONSOLE -> DESKTOP
            DESKTOP -> CONSOLE
            else -> return rejected(HandoffEndpointCode.UNKNOWN_SENDER)
        }
        return try {
            if (context.isDeviceProtectedStorage || context.applicationContext.isDeviceProtectedStorage ||
                context.applicationInfo.uid != Process.myUid() || Application.getProcessName() != sender)
                return rejected(HandoffEndpointCode.WRONG_OWNER)
            val packages = context.packageManager
            val info = packages.getPackageInfo(receiver, PackageManager.PackageInfoFlags.of(0))
            val receiverApp = info.applicationInfo ?: return rejected(HandoffEndpointCode.COUNTERPART_UNAVAILABLE)
            if (HandoffCaller.verifyTransactionUid(context, receiverApp.uid) != HandoffCallerCode.VERIFIED)
                return rejected(HandoffEndpointCode.CALLER_POLICY_REJECTED)
            val component = ComponentName(receiver, SERVICE_CLASS)
            val service = packages.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0))
            val permission = receiver + ".permission.FILES_HANDOFF"
            val declaredPermission = packages.getPermissionInfo(permission, 0)
            val policy = verifyDeclaration(service, declaredPermission, receiver, receiverApp.uid)
            if (policy != HandoffEndpointCode.VERIFIED) return rejected(policy)
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                return rejected(HandoffEndpointCode.PERMISSION_DENIED)
            if (!settingAllows(packages.getApplicationEnabledSetting(receiver)) ||
                !settingAllows(packages.getComponentEnabledSetting(component))) return rejected(HandoffEndpointCode.SERVICE_DISABLED)
            // A final current-package check catches observed replacement/signature/UID changes.
            // This is not an atomic PackageManager-to-bind installation lease.
            if (HandoffCaller.verifyTransactionUid(context, receiverApp.uid) != HandoffCallerCode.VERIFIED)
                return rejected(HandoffEndpointCode.CALLER_POLICY_REJECTED)
            HandoffEndpointResult.Verified(component, permission)
        } catch (_: PackageManager.NameNotFoundException) { rejected(HandoffEndpointCode.COUNTERPART_UNAVAILABLE) }
        catch (_: Exception) { rejected(HandoffEndpointCode.LOOKUP_DENIED) }
    }

    /** Pure declaration policy for authored boundary tests; cannot mint a captured Binder caller. */
    internal fun verifyDeclaration(service: ServiceInfo, permission: PermissionInfo,
                                   receiver: String, uid: Int): HandoffEndpointCode {
        if (receiver !in setOf(CONSOLE, DESKTOP)) return HandoffEndpointCode.SERVICE_MISMATCH
        val app = service.applicationInfo ?: return HandoffEndpointCode.SERVICE_MISMATCH
        if (service.packageName != receiver || service.name != SERVICE_CLASS || app.packageName != receiver || app.uid != uid)
            return HandoffEndpointCode.SERVICE_MISMATCH
        if (!app.enabled || !service.enabled) return HandoffEndpointCode.SERVICE_DISABLED
        if (!service.exported) return HandoffEndpointCode.SERVICE_NOT_EXPORTED
        if (service.processName != receiver || app.processName != receiver ||
            service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0) return HandoffEndpointCode.PROCESS_MISMATCH
        val expected = receiver + ".permission.FILES_HANDOFF"
        if (service.permission != expected || permission.name != expected || permission.packageName != receiver)
            return HandoffEndpointCode.PERMISSION_MISMATCH
        if (permission.protection != PermissionInfo.PROTECTION_SIGNATURE)
            return HandoffEndpointCode.PERMISSION_NOT_SIGNATURE
        return HandoffEndpointCode.VERIFIED
    }

    private fun settingAllows(setting: Int) = setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
        setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    private fun rejected(code: HandoffEndpointCode): HandoffEndpointResult = HandoffEndpointResult.Rejected(code)
}
