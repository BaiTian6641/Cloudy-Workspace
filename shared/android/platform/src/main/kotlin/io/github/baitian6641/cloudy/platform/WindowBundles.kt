package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.domain.WindowRestore
import io.github.baitian6641.cloudy.domain.WindowRestoreProblem
import io.github.baitian6641.cloudy.domain.WindowSnapshot
import io.github.baitian6641.cloudy.domain.WindowState

/** Only small, validated local UI metadata crosses Android's saved-state boundary. */
internal object WindowBundles {
    private const val KEY = "io.github.baitian6641.cloudy.window.v1"

    fun save(destination: Bundle, state: WindowState) {
        val snapshot = state.snapshot()
        destination.putBundle(KEY, Bundle().apply {
            putInt("schema", snapshot.schemaVersion)
            putString("owner", snapshot.owner)
            putString("id", snapshot.windowId)
            putString("name", snapshot.name)
        })
    }

    fun restore(source: Bundle?, owner: WindowOwner): WindowRestore? {
        if (source == null) return null
        return try {
            if (!source.containsKey(KEY)) return null
            val record = source.getBundle(KEY)
                ?: return WindowRestore.Rejected(WindowRestoreProblem.UNKNOWN_SCHEMA)
            WindowState.restore(owner, WindowSnapshot(
                schemaVersion = record.getInt("schema", -1),
                owner = record.getString("owner") ?: "",
                windowId = record.getString("id") ?: "",
                name = record.getString("name") ?: "",
            ))
        } catch (_: RuntimeException) {
            // Malformed parcels/types never partially restore identity or replay an operation.
            WindowRestore.Rejected(WindowRestoreProblem.UNKNOWN_SCHEMA)
        }
    }
}
