package io.github.baitian6641.cloudy.platform

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.baitian6641.cloudy.design.CloudyAppearance
import io.github.baitian6641.cloudy.design.CloudyDensity
import io.github.baitian6641.cloudy.design.CloudyDisplayPreferences
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real private preferences in unique test-owned files; not a process-loss or disk-failure proof. */
@RunWith(AndroidJUnit4::class)
class CloudyDisplayPreferencesStorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ownedNames = mutableListOf<String>()

    private fun name(): String = "display-test-${UUID.randomUUID()}".also { ownedNames += it }
    private fun file(name: String) = File(context.dataDir, "shared_prefs/$name.xml")
    private fun success(result: DisplayStorageResult): DisplayStorageResult.Success {
        assertTrue(result is DisplayStorageResult.Success)
        return result as DisplayStorageResult.Success
    }

    @After fun removeOnlyOwnedPreferenceFixtures() {
        // Each random name is exclusive to this case; never touch the production preference name.
        ownedNames.forEach { value ->
            assertTrue(value.startsWith("display-test-"))
            assertNotEquals(CloudyDisplaySettings.PREFERENCES_NAME, value)
            context.deleteSharedPreferences(value)
            assertFalse(file(value).exists())
            assertFalse(File(file(value).path + ".bak").exists())
        }
    }

    @Test fun defaultsDoNotWriteAndEachConfirmedChoicePreservesTheOtherField() {
        val name = name()
        val store = CloudyDisplayPreferencesStorage(context, name)
        assertEquals(CloudyDisplayPreferences(), success(store.load()).preferences)
        assertFalse(file(name).exists())
        val dark = success(store.setAppearance(CloudyAppearance.DARK))
        assertEquals(CloudyDisplayPreferences(CloudyAppearance.DARK), dark.preferences)
        assertFalse(dark.recovered)
        assertTrue(file(name).isFile)
        assertTrue(file(name).readText().contains(">DARK<"))
        val compact = success(store.setDensity(CloudyDensity.COMPACT_POINTER))
        assertEquals(CloudyDisplayPreferences(CloudyAppearance.DARK, CloudyDensity.COMPACT_POINTER), compact.preferences)
        assertTrue(file(name).readText().contains(">COMPACT_POINTER<"))
        // Android may cache this preferences object: reopening is deliberately not process proof.
        assertEquals(compact.preferences, success(CloudyDisplayPreferencesStorage(context, name).load()).preferences)
        assertEquals(CloudyDensity.COMPACT_POINTER,
            success(store.setAppearance(CloudyAppearance.SYSTEM)).preferences.density)
    }

    @Test fun invalidKnownValuesAreVisibleAndRetainedUntilThatSpecificChoiceIsSaved() {
        val name = name()
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertTrue(preferences.edit().putString("appearance", "FUTURE_MODE")
            .putInt("density", 7).putString("unrelated", "retain").commit())
        val before = file(name).readBytes()
        val store = CloudyDisplayPreferencesStorage(context, name)
        val recovered = success(store.load())
        assertTrue(recovered.recovered)
        assertEquals(CloudyDisplayPreferences(), recovered.preferences)
        assertArrayEquals(before, file(name).readBytes())
        val changed = success(store.setAppearance(CloudyAppearance.LIGHT))
        assertTrue(changed.recovered)
        assertEquals(CloudyAppearance.LIGHT, changed.preferences.appearance)
        assertEquals(7, preferences.all["density"])
        assertEquals("retain", preferences.all["unrelated"])
        val repaired = success(store.setDensity(CloudyDensity.STANDARD_POINTER))
        assertFalse(repaired.recovered)
        assertEquals(CloudyAppearance.LIGHT, repaired.preferences.appearance)
        assertEquals("retain", preferences.all["unrelated"])
    }

    @Test fun mainThreadAndDeviceProtectedContextsRejectBeforeOpeningTheFixture() {
        val name = name()
        lateinit var result: DisplayStorageResult
        instrumentation.runOnMainSync { result = CloudyDisplayPreferencesStorage(context, name).load() }
        assertEquals(DisplayStorageCode.WRONG_THREAD, (result as DisplayStorageResult.Failure).code)
        assertFalse(file(name).exists())
        val protected = context.createDeviceProtectedStorageContext()
        val refused = CloudyDisplayPreferencesStorage(protected, name).setAppearance(CloudyAppearance.DARK)
        assertEquals(DisplayStorageCode.WRONG_STORAGE, (refused as DisplayStorageResult.Failure).code)
        assertFalse(File(protected.dataDir, "shared_prefs/$name.xml").exists())
        assertFalse(file(name).exists())
    }

    @Test fun mismatchedContextAndUnsafeFixtureNameCannotOpenPreferences() {
        val name = name()
        val wrong = object : ContextWrapper(context) {
            override fun getPackageName(): String = "invalid.display.owner"
        }
        val result = CloudyDisplayPreferencesStorage(wrong, name).load()
        assertEquals(DisplayStorageCode.WRONG_OWNER, (result as DisplayStorageResult.Failure).code)
        assertFalse(file(name).exists())
        assertThrows(IllegalArgumentException::class.java) {
            CloudyDisplayPreferencesStorage(context, "../outside")
        }
    }
}
