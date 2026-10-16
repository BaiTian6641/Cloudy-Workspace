package io.github.baitian6641.cloudy.platform

import android.os.Bundle
import io.github.baitian6641.cloudy.domain.WindowOwner

sealed interface SshTrustUiRestore {
    data object Missing : SshTrustUiRestore
    data class Restored(val pending: Boolean) : SshTrustUiRestore
    data object Rejected : SshTrustUiRestore
}

/** Bounded uncertainty metadata only. A saved record never contains a pin or mutation approval. */
object SshTrustUiState {
    private const val KEY = "io.github.baitian6641.cloudy.ssh-trust-ui.v1"
    private val keys = setOf("schema", "owner", "pending")

    fun save(destination: Bundle, owner: WindowOwner, pending: Boolean) {
        destination.putBundle(KEY, Bundle().apply {
            putInt("schema", 1)
            putString("owner", owner.wireId)
            putBoolean("pending", pending)
        })
    }

    fun restore(source: Bundle?, owner: WindowOwner): SshTrustUiRestore {
        if (source == null) return SshTrustUiRestore.Missing
        return try {
            if (!source.containsKey(KEY)) return SshTrustUiRestore.Missing
            val record = raw(source, KEY) as? Bundle ?: return SshTrustUiRestore.Rejected
            if (record.keySet() != keys || raw(record, "schema") != 1 ||
                raw(record, "owner") != owner.wireId || raw(record, "pending") !is Boolean) {
                return SshTrustUiRestore.Rejected
            }
            SshTrustUiRestore.Restored(raw(record, "pending") as Boolean)
        } catch (_: Exception) { SshTrustUiRestore.Rejected }
    }

    @Suppress("DEPRECATION")
    private fun raw(source: Bundle, key: String): Any? = source.get(key)
}
