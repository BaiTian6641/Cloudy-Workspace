package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.baitian6641.cloudy.domain.WindowOwner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Bundle/Parcel boundary; restoring uncertainty does not invoke a store or grant approval. */
@RunWith(AndroidJUnit4::class)
class SshTrustUiStateTest {
    @Test fun parcelRetainsOnlyOwningAppUncertaintyMetadata() {
        for (owner in WindowOwner.entries) for (pending in listOf(false, true)) {
            val state = Bundle().apply { SshTrustUiState.save(this, owner, pending) }
            val restored = parcel(state)
            assertEquals(setOf(KEY), restored.keySet())
            assertEquals(setOf("schema", "owner", "pending"), checkNotNull(restored.getBundle(KEY)).keySet())
            assertEquals(SshTrustUiRestore.Restored(pending), SshTrustUiState.restore(restored, owner))
        }
    }

    @Test fun missingStateDiffersFromWrongOwnerOrWrongOuterType() {
        assertEquals(SshTrustUiRestore.Missing, SshTrustUiState.restore(null, WindowOwner.CONSOLE))
        assertEquals(SshTrustUiRestore.Missing, SshTrustUiState.restore(Bundle(), WindowOwner.CONSOLE))
        val other = Bundle().apply { SshTrustUiState.save(this, WindowOwner.DESKTOP, true) }
        assertEquals(SshTrustUiRestore.Rejected, SshTrustUiState.restore(parcel(other), WindowOwner.CONSOLE))
        val malformed = Bundle().apply { putString(KEY, "not a record") }
        assertEquals(SshTrustUiRestore.Rejected, SshTrustUiState.restore(parcel(malformed), WindowOwner.CONSOLE))
    }

    @Test fun malformedVersionFieldsAndInjectedApprovalAreRejected() {
        val changes: List<(Bundle) -> Unit> = listOf(
            { it.putInt("schema", 2) },
            { it.putLong("schema", 1L) },
            { it.remove("pending") },
            { it.putString("pending", "true") },
            { it.putBoolean("approved", true) },
        )
        for (change in changes) {
            val state = Bundle().apply { SshTrustUiState.save(this, WindowOwner.CONSOLE, true) }
            change(checkNotNull(state.getBundle(KEY)))
            assertEquals(SshTrustUiRestore.Rejected, SshTrustUiState.restore(parcel(state), WindowOwner.CONSOLE))
        }
    }

    private fun parcel(source: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            source.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Bundle.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
    }

    companion object {
        private const val KEY = "io.github.baitian6641.cloudy.ssh-trust-ui.v1"
    }
}
