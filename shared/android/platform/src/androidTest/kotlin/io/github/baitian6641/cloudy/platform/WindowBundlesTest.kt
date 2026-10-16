package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.domain.WindowRestore
import io.github.baitian6641.cloudy.domain.WindowRestoreProblem
import io.github.baitian6641.cloudy.domain.WindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Bundle/Parcel boundary, not a JVM framework stub or device-input verdict. */
@RunWith(AndroidJUnit4::class)
class WindowBundlesTest {
    private val key = "io.github.baitian6641.cloudy.window.v1"

    private fun saved(): Bundle = Bundle().also {
        WindowBundles.save(it, WindowState.create(WindowOwner.CONSOLE, "Local 日本語 😀"))
    }

    private fun record(bundle: Bundle): Bundle = checkNotNull(bundle.getBundle(key))

    private fun parcelRoundTrip(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            checkNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    @Test fun missingLocalRecordDoesNotImportUnrelatedState() {
        assertNull(WindowBundles.restore(null, WindowOwner.CONSOLE))
        assertNull(WindowBundles.restore(Bundle(), WindowOwner.CONSOLE))
        val unrelated = Bundle().apply {
            putString("owner", "desktop")
            putString("name", "External value")
        }
        assertNull(WindowBundles.restore(unrelated, WindowOwner.CONSOLE))
    }

    @Test fun realParcelRoundTripPreservesOnlyTheValidLocalSnapshot() {
        val state = WindowState.create(WindowOwner.CONSOLE, "Local 日本語 😀")
        val source = Bundle().apply {
            putString("unrelated", "framework state retained separately")
            WindowBundles.save(this, state)
        }
        assertEquals(setOf("schema", "owner", "id", "name"), record(source).keySet())
        val decoded = parcelRoundTrip(source)
        val restored = WindowBundles.restore(decoded, WindowOwner.CONSOLE)
        assertTrue(restored is WindowRestore.Restored)
        assertEquals(state.snapshot(), (restored as WindowRestore.Restored).state.snapshot())
        assertEquals("framework state retained separately", decoded.getString("unrelated"))
    }

    @Test fun schemaAndOwningAppMustMatchBeforeRestoration() {
        val source = saved()
        assertEquals(WindowRestore.Rejected(WindowRestoreProblem.WRONG_OWNER),
            WindowBundles.restore(parcelRoundTrip(source), WindowOwner.DESKTOP))
        record(source).putInt("schema", 2)
        assertEquals(WindowRestore.Rejected(WindowRestoreProblem.UNKNOWN_SCHEMA),
            WindowBundles.restore(parcelRoundTrip(source), WindowOwner.CONSOLE))
    }

    @Test fun malformedOuterAndFieldTypesNeverPartiallyRestoreIdentity() {
        val wrongOuter = Bundle().apply { putString(key, "not a Bundle") }
        assertTrue(WindowBundles.restore(parcelRoundTrip(wrongOuter), WindowOwner.CONSOLE)
            is WindowRestore.Rejected)
        val mutations: List<(Bundle) -> Unit> = listOf(
            { it.putString("schema", "1") },
            { it.putInt("owner", 1) },
            { it.putInt("id", 1) },
            { it.putInt("name", 1) },
            { it.remove("schema") },
            { it.remove("owner") },
            { it.remove("id") },
            { it.remove("name") },
        )
        for (mutate in mutations) {
            val source = saved()
            mutate(record(source))
            // Android typed getters may return defaults or throw; either path must reject.
            assertTrue(WindowBundles.restore(parcelRoundTrip(source), WindowOwner.CONSOLE)
                is WindowRestore.Rejected)
        }
    }

    @Test fun invalidSerializedIdOrMultilineNameCannotBecomeCommittedState() {
        val badId = saved()
        record(badId).putString("id", "1-1-1-1-1")
        assertEquals(WindowRestore.Rejected(WindowRestoreProblem.INVALID_ID),
            WindowBundles.restore(parcelRoundTrip(badId), WindowOwner.CONSOLE))
        val badName = saved()
        record(badName).putString("name", "first\u2028second")
        assertEquals(WindowRestore.Rejected(WindowRestoreProblem.INVALID_NAME),
            WindowBundles.restore(parcelRoundTrip(badName), WindowOwner.CONSOLE))
    }
}
