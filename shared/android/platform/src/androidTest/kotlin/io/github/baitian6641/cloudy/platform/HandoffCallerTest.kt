package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Standalone platform tests: local denial and real PM reads, not sibling Binder acceptance. */
@RunWith(AndroidJUnit4::class)
class HandoffCallerTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun productionEntryRejectsLocalCallsWithoutAnIncomingTransaction() {
        // Binder documents that a thread outside an incoming transaction sees its own process UID.
        assertEquals(Process.myUid(), Binder.getCallingUid())
        assertEquals(HandoffCallerCode.SELF_CALL, HandoffCaller.verifyIncoming(context))
    }

    @Test fun explicitOwnUidIsRejectedBeforePackageLookup() {
        assertEquals(HandoffCallerCode.SELF_CALL,
            HandoffCaller.verifyTransactionUid(context, Process.myUid()))
    }

    @Test fun standaloneTestPackageCannotDeclareItselfAReceiver() {
        assertTrue(context.packageName !in setOf(
            "io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop",
        ))
        // Explicit test input is not an observed caller or authentication success.
        assertEquals(HandoffCallerCode.UNKNOWN_RECEIVER,
            HandoffCaller.verifyTransactionUid(context, Process.myUid() + 1))
    }

    @Test fun spoofedReceiverNameCannotReplaceTheRealProcessIdentity() {
        listOf("io.github.baitian6641.cloudy.console", "io.github.baitian6641.cloudy.desktop").forEach { name ->
            val spoofed = object : ContextWrapper(context) {
                override fun getPackageName(): String = name
            }
            val result = HandoffCaller.verifyTransactionUid(spoofed, Process.myUid() + 1)
            // Products may be absent or hidden from this standalone test APK. If visible, their
            // actual PM UID cannot become the standalone test process UID through a context name.
            assertTrue(result in setOf(HandoffCallerCode.PACKAGE_UNAVAILABLE,
                HandoffCallerCode.RECEIVER_MISMATCH, HandoffCallerCode.LOOKUP_DENIED))
        }
    }

    @Test fun realPackageManagerReportsTheLocalInstalledIdentityAndSignature() {
        val packages = context.packageManager
        val info = packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        assertEquals(context.packageName, info.packageName)
        assertEquals(Process.myUid(), checkNotNull(info.applicationInfo).uid)
        assertTrue(checkNotNull(packages.getPackagesForUid(Process.myUid())).contains(context.packageName))
        assertEquals(PackageManager.SIGNATURE_MATCH, packages.checkSignatures(Process.myUid(), Process.myUid()))
    }
}
