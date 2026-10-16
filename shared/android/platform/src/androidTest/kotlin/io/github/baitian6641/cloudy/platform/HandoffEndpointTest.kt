package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Constructed declaration policy and local rejection only; no sibling bind/transaction pass. */
@RunWith(AndroidJUnit4::class)
class HandoffEndpointTest {
    private val receiver = "io.github.baitian6641.cloudy.desktop"
    private val uid = 23456
    private fun service() = ServiceInfo().apply {
        packageName = receiver
        name = HandoffEndpoint.SERVICE_CLASS
        processName = receiver
        enabled = true
        exported = true
        permission = receiver + ".permission.FILES_HANDOFF"
        applicationInfo = ApplicationInfo().apply {
            packageName = receiver
            processName = receiver
            this.uid = this@HandoffEndpointTest.uid
            enabled = true
        }
    }
    @Suppress("DEPRECATION") // Test-only construction of parsed manifest metadata, never an access check.
    private fun permission(level: Int = PermissionInfo.PROTECTION_SIGNATURE) = PermissionInfo().apply {
        name = receiver + ".permission.FILES_HANDOFF"
        packageName = receiver
        protectionLevel = level
    }
    private fun evaluate(service: ServiceInfo = service(), permission: PermissionInfo = permission()) =
        HandoffEndpoint.verifyDeclaration(service, permission, receiver, uid)

    @Test fun exactConstructedDeclarationPassesOnlyItsPolicyCheck() {
        assertEquals(HandoffEndpointCode.VERIFIED, evaluate())
        // Constructed facts do not mint a captured incoming caller or establish a real bind.
        assertEquals(HandoffEndpoint.SERVICE_CLASS, service().name)
    }

    @Test fun aliasWrongPackageAndUidCannotSelectAnEndpoint() {
        assertEquals(HandoffEndpointCode.SERVICE_MISMATCH, evaluate(service().apply { name += "Other" }))
        assertEquals(HandoffEndpointCode.SERVICE_MISMATCH, evaluate(service().apply { packageName = "other.package" }))
        assertEquals(HandoffEndpointCode.SERVICE_MISMATCH, evaluate(service().apply { applicationInfo!!.uid++ }))
        assertEquals(HandoffEndpointCode.SERVICE_MISMATCH, evaluate(service().apply { applicationInfo!!.packageName = "other.package" }))
        assertEquals(HandoffEndpointCode.SERVICE_MISMATCH, evaluate(service().apply { applicationInfo = null }))
    }

    @Test fun disabledOrPrivateComponentsDeny() {
        assertEquals(HandoffEndpointCode.SERVICE_DISABLED, evaluate(service().apply { enabled = false }))
        assertEquals(HandoffEndpointCode.SERVICE_DISABLED, evaluate(service().apply { applicationInfo!!.enabled = false }))
        assertEquals(HandoffEndpointCode.SERVICE_NOT_EXPORTED, evaluate(service().apply { exported = false }))
    }

    @Test fun namedOrIsolatedProcessCannotOwnTheDefaultProcessRoute() {
        assertEquals(HandoffEndpointCode.PROCESS_MISMATCH, evaluate(service().apply { processName += ":remote" }))
        assertEquals(HandoffEndpointCode.PROCESS_MISMATCH, evaluate(service().apply { applicationInfo!!.processName += ":remote" }))
        assertEquals(HandoffEndpointCode.PROCESS_MISMATCH, evaluate(service().apply { flags = flags or ServiceInfo.FLAG_ISOLATED_PROCESS }))
    }

    @Test fun permissionMustBelongToExactReceiverAndExactComponent() {
        assertEquals(HandoffEndpointCode.PERMISSION_MISMATCH, evaluate(service().apply { permission = null }))
        assertEquals(HandoffEndpointCode.PERMISSION_MISMATCH, evaluate(permission = permission().apply { name += "OTHER" }))
        assertEquals(HandoffEndpointCode.PERMISSION_MISMATCH, evaluate(permission = permission().apply { packageName = "other.package" }))
    }

    @Test fun normalDangerousAndSignatureOrSystemAreNotSignatureBase() {
        for (level in listOf(PermissionInfo.PROTECTION_NORMAL, PermissionInfo.PROTECTION_DANGEROUS, 3)) {
            assertEquals(HandoffEndpointCode.PERMISSION_NOT_SIGNATURE, evaluate(permission = permission(level)))
        }
    }

    @Test fun actualMainEntryDeniesBeforePackageManagerWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val noLookup = object : ContextWrapper(context) {
            override fun getPackageManager(): PackageManager = throw AssertionError("MAIN_PACKAGE_LOOKUP")
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(HandoffEndpointCode.WRONG_THREAD, (HandoffEndpoint.verify(noLookup) as HandoffEndpointResult.Rejected).code)
        }
    }

    @Test fun standalonePackageIsNotAProductSenderAndResultIsRedacted() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName !in setOf("io.github.baitian6641.cloudy.console", receiver))
        assertEquals(HandoffEndpointCode.UNKNOWN_SENDER, (HandoffEndpoint.verify(context) as HandoffEndpointResult.Rejected).code)
        assertEquals("Rejected(code=PERMISSION_MISMATCH)",
            HandoffEndpointResult.Rejected(HandoffEndpointCode.PERMISSION_MISMATCH).toString())
    }
}
