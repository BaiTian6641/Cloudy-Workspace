package io.github.baitian6641.cloudy.platform

import io.github.baitian6641.cloudy.domain.ConnectionProfile
import io.github.baitian6641.cloudy.domain.ConnectionProtocol
import io.github.baitian6641.cloudy.domain.SshPeerIdentity
import io.github.baitian6641.cloudy.domain.SshTrustStatus
import io.github.baitian6641.cloudy.domain.SshTrustSnapshot
import io.github.baitian6641.cloudy.domain.WindowOwner
import io.github.baitian6641.cloudy.identity.VaultProfile
import io.github.baitian6641.cloudy.identity.VaultRole

/** SSH pin state for one host row; other protocols manage trust inside their own session. */
internal enum class HomeTrust { NONE, ACCEPTED, REVOKED, UNKNOWN }

/** One host row: profile identity plus observed trust/credential state, never secrets. */
internal data class HomeRow(
    val profile: ConnectionProfile,
    val version: ConnectionProfileVersion,
    val trust: HomeTrust,
    /** True/false describe observed vault metadata; null means the vault index was unavailable. */
    val hasCredential: Boolean?,
) {
    override fun toString(): String = "HomeRow(redacted)"
}

/** Navigation selection; a tag entry filters to hosts carrying that tag. */
internal sealed interface HomeSelection {
    data object Favorites : HomeSelection
    data object Recents : HomeSelection
    data object All : HomeSelection
    data class Tag(val tag: String) : HomeSelection
}

/** Pure home-list organization over validated store snapshots; no I/O and no secrets. */
internal object WorkspaceHomeOrganizer {

    /**
     * Builds rows from observed snapshots. A null trust catalog or vault index means the read
     * failed; rows then report UNKNOWN/unavailable instead of masquerading as absent records.
     */
    fun rows(
        catalog: ConnectionProfileCatalog,
        trustCatalog: SshTrustCatalog?,
        vaultProfiles: List<VaultProfile>?,
    ): List<HomeRow> {
        val trustByPeer = trustCatalog?.snapshots?.associateBy { it.peer }
        return catalog.profiles.map { profile ->
            val trust = when {
                profile.protocol != ConnectionProtocol.SSH -> HomeTrust.NONE
                trustByPeer == null -> HomeTrust.UNKNOWN
                else -> when (peerOf(profile)?.let { trustByPeer[it] }?.status) {
                    is SshTrustStatus.Accepted -> HomeTrust.ACCEPTED
                    is SshTrustStatus.Revoked -> HomeTrust.REVOKED
                    null -> HomeTrust.NONE
                }
            }
            val credential = vaultProfiles?.any { saved ->
                saved.binding.role == VaultRole.TARGET &&
                    saved.binding.protocol.name == profile.protocol.name &&
                    saved.binding.host == profile.host && saved.binding.port == profile.port
            }
            HomeRow(profile, ConnectionProfileVersion.create(catalog.generation, profile.id, profile.revision),
                trust, credential)
        }
    }

    private fun peerOf(profile: ConnectionProfile): SshPeerIdentity? = try {
        SshPeerIdentity.create(profile.host, profile.port)
    } catch (_: IllegalArgumentException) { null }

    /** Tag navigation entries with row counts, ordered case-insensitively with exact ties broken. */
    fun tags(rows: List<HomeRow>): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        for (row in rows) for (tag in row.profile.tags) counts[tag] = (counts[tag] ?: 0) + 1
        return counts.entries.map { it.key to it.value }
            .sortedWith(compareBy({ it.first.lowercase() }, { it.first }))
    }

    fun filter(rows: List<HomeRow>, selection: HomeSelection): List<HomeRow> = when (selection) {
        HomeSelection.Favorites -> rows.filter { it.profile.favorite }
            .sortedWith(labelOrder)
        HomeSelection.Recents -> rows.filter { it.profile.lastUsedAt > 0 }
            .sortedWith(compareByDescending<HomeRow> { it.profile.lastUsedAt }.then(labelOrder))
        HomeSelection.All -> rows.sortedWith(labelOrder)
        is HomeSelection.Tag -> rows.filter { it.profile.tags.contains(selection.tag) }
            .sortedWith(labelOrder)
    }

    fun count(rows: List<HomeRow>, selection: HomeSelection): Int = filter(rows, selection).size

    /** The selected row, re-resolved after a refresh; a vanished identity clears the selection. */
    fun resolve(rows: List<HomeRow>, selectedId: String?): HomeRow? =
        rows.firstOrNull { it.profile.id == selectedId }

    private val labelOrder = compareBy<HomeRow>({ it.profile.label.lowercase() }, { it.profile.label },
        { it.profile.host }, { it.profile.port }, { it.profile.id })
}
