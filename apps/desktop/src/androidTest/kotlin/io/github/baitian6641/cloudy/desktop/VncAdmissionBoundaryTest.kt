package io.github.baitian6641.cloudy.desktop

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real model/main-looper refusal boundaries. No catalog read, native allocation or network fixture.
 * These cases qualify refusal/wiping only, never an accepted connection or native retirement.
 */
@RunWith(AndroidJUnit4::class)
class VncAdmissionBoundaryTest {
    private fun onMain(body: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try { body() } catch (error: Throwable) { failure.set(error) }
        }
        failure.get()?.let { throw it }
    }
    private fun model(store: ViewModelStore): VncSessionModel {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        return ViewModelProvider(store, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass == VncSessionModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return VncSessionModel(app) as T
            }
        })[VncSessionModel::class.java]
    }
    @Test fun missingCatalogCannotAdmitAndTransferredBuffersAreWiped() = onMain {
        val store = ViewModelStore()
        try {
            val model = model(store)
            val attachment = Any()
            assertTrue(model.attach(attachment))
            model.foreground(attachment, true)
            val pin = ByteArray(32) { 7 }
            val username = byteArrayOf(117)
            val password = byteArrayOf(112)
            model.connect(attachment, "3da56dc6-255f-4cdd-801b-5d7722d782ce", 1,
                pin, username, password, 30)
            assertTrue(pin.all { it == 0.toByte() })
            assertTrue(username.all { it == 0.toByte() })
            assertTrue(password.all { it == 0.toByte() })
            assertEquals(VncIssue.STALE_PROFILE, model.state.issue)
            assertNull(model.state.engineState)
        } finally { store.clear() }
    }
    @Test fun userEndedMissingCatalogStillRefusesAndWipesTransferredBuffers() = onMain {
        val store = ViewModelStore()
        try {
            val model = model(store)
            val attachment = Any()
            assertTrue(model.attach(attachment))
            model.foreground(attachment, true)
            val pin = ByteArray(32) { 7 }
            val username = byteArrayOf(117)
            val password = byteArrayOf(112)
            model.connectUserEnded(attachment, "3da56dc6-255f-4cdd-801b-5d7722d782ce", 1,
                pin, username, password)
            assertTrue(pin.all { it == 0.toByte() })
            assertTrue(username.all { it == 0.toByte() })
            assertTrue(password.all { it == 0.toByte() })
            assertEquals(VncIssue.STALE_PROFILE, model.state.issue)
            assertNull(model.state.engineState)
        } finally { store.clear() }
    }
    @Test fun retiredAttachmentCannotSubmitIntoReplacement() = onMain {
        val store = ViewModelStore()
        try {
            val model = model(store)
            val previous = Any()
            val replacement = Any()
            assertTrue(model.attach(previous))
            model.foreground(previous, true)
            model.detach(previous)
            assertTrue(model.attach(replacement))
            model.foreground(replacement, true)
            val pin = ByteArray(32) { 9 }
            val username = byteArrayOf(117)
            val password = byteArrayOf(112)
            model.connect(previous, "3da56dc6-255f-4cdd-801b-5d7722d782ce", 1,
                pin, username, password, 30)
            assertTrue(pin.all { it == 0.toByte() })
            assertTrue(username.all { it == 0.toByte() })
            assertTrue(password.all { it == 0.toByte() })
            assertNull(model.state.attempt)
            assertNull(model.state.engineState)
        } finally { store.clear() }
    }
}
