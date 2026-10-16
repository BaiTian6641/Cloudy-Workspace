package io.github.baitian6641.cloudy.domain

import java.util.UUID

enum class FilesResourceState { PRIVATE, SHARED, REVOKED }

enum class FilesResourceValidationCode {
    INVALID_ID, INVALID_GENERATION, INVALID_REVISION, INVALID_LABEL, INVALID_ROOT,
    INVALID_ACCOUNT, WRONG_OWNER, WRONG_PEER, INVALID_TRUST, INVALID_STATE,
}

class FilesResourceValidationException(val code: FilesResourceValidationCode) : IllegalArgumentException(code.name)

/** Four independent namespaces. These references are not bearer authorization or profile IDs. */
class FilesResourceReference private constructor(
    val hostId: String, val environmentId: String, val workspaceId: String, val resourceId: String,
) {
    override fun equals(other: Any?): Boolean = other is FilesResourceReference && hostId == other.hostId &&
        environmentId == other.environmentId && workspaceId == other.workspaceId && resourceId == other.resourceId
    override fun hashCode(): Int = listOf(hostId, environmentId, workspaceId, resourceId).hashCode()
    override fun toString(): String = "FilesResourceReference(redacted)"

    companion object {
        fun createNew(): FilesResourceReference {
            val ids = linkedSetOf<String>()
            while (ids.size < 4) ids += UUID.randomUUID().toString()
            val values = ids.toList()
            return FilesResourceReference(values[0], values[1], values[2], values[3])
        }

        fun restore(hostId: String, environmentId: String, workspaceId: String, resourceId: String): FilesResourceReference {
            val ids = listOf(hostId, environmentId, workspaceId, resourceId)
            // Registry-generated IDs are strictly v4 and distinct; the v1 envelope stays broader.
            if (ids.toSet().size != 4 || ids.any { !filesUuid.matches(it) }) {
                filesInvalid(FilesResourceValidationCode.INVALID_ID)
            }
            return FilesResourceReference(hostId, environmentId, workspaceId, resourceId)
        }
    }
}

/** Store lifetime and exact revision are compared by storage in its mutation transaction. */
class FilesResourceVersion private constructor(val generation: String, val resourceId: String, val revision: Long) {
    override fun equals(other: Any?): Boolean = other is FilesResourceVersion && generation == other.generation &&
        resourceId == other.resourceId && revision == other.revision
    override fun hashCode(): Int = listOf(generation, resourceId, revision).hashCode()
    override fun toString(): String = "FilesResourceVersion(redacted)"

    companion object {
        fun create(generation: String, resourceId: String, revision: Long): FilesResourceVersion {
            filesGeneration(generation)
            if (!filesUuid.matches(resourceId)) filesInvalid(FilesResourceValidationCode.INVALID_ID)
            filesRevision(revision)
            return FilesResourceVersion(generation, resourceId, revision)
        }
    }
}

/**
 * Receiver-private navigation metadata. No authenticated/listed proof is constructed here.
 * accountName is exact and remains private; the root neither confines browsing nor grants access.
 */
class FilesResourceDraft private constructor(
    val owner: WindowOwner, val label: String, val profile: SshObservationProfile,
    val acceptedTrust: SshTrustSnapshot, val navigationRoot: String, val accountName: String,
) {
    override fun equals(other: Any?): Boolean = other is FilesResourceDraft && owner == other.owner && label == other.label &&
        profile == other.profile && acceptedTrust == other.acceptedTrust && navigationRoot == other.navigationRoot &&
        accountName == other.accountName
    override fun hashCode(): Int = listOf(owner, label, profile, acceptedTrust, navigationRoot, accountName).hashCode()
    override fun toString(): String = "FilesResourceDraft(redacted)"

    companion object {
        const val MAX_ROOT_UNITS = 4096
        const val MAX_ACCOUNT_UNITS = 256

        fun create(owner: WindowOwner, label: String, profile: SshObservationProfile,
                   acceptedTrust: SshTrustSnapshot, navigationRoot: String, accountName: String): FilesResourceDraft {
            if (WindowState.nameProblem(label) != null) filesInvalid(FilesResourceValidationCode.INVALID_LABEL)
            if (profile.profile.owner != owner || acceptedTrust.owner != owner) filesInvalid(FilesResourceValidationCode.WRONG_OWNER)
            if (acceptedTrust.peer != profile.peer || profile.peer.route != SshTrustRoute.Direct) {
                filesInvalid(FilesResourceValidationCode.WRONG_PEER)
            }
            if (acceptedTrust.status !is SshTrustStatus.Accepted) filesInvalid(FilesResourceValidationCode.INVALID_TRUST)
            if (!filesText(navigationRoot, MAX_ROOT_UNITS)) filesInvalid(FilesResourceValidationCode.INVALID_ROOT)
            // Mirrors SshPasswordCredentials username validation without a domain -> ssh dependency.
            // Preserve every valid unit, including spaces/case/normalization form, for exact admission.
            if (!filesText(accountName, MAX_ACCOUNT_UNITS)) filesInvalid(FilesResourceValidationCode.INVALID_ACCOUNT)
            return FilesResourceDraft(owner, label.trim(), profile, acceptedTrust, navigationRoot, accountName)
        }
    }
}

/** Immutable one-to-one direct-files aggregate. Controller must verify actual listing/current bindings. */
class FilesResourceRegistration private constructor(
    val generation: String, val reference: FilesResourceReference, val revision: Long,
    val state: FilesResourceState, val draft: FilesResourceDraft,
) {
    val owner: WindowOwner get() = draft.owner
    val label: String get() = draft.label
    val profile: SshObservationProfile get() = draft.profile
    val acceptedTrust: SshTrustSnapshot get() = draft.acceptedTrust
    val navigationRoot: String get() = draft.navigationRoot
    val accountName: String get() = draft.accountName

    fun version(): FilesResourceVersion = FilesResourceVersion.create(generation, reference.resourceId, revision)

    fun share(): FilesResourceRegistration {
        if (state != FilesResourceState.PRIVATE) filesInvalid(FilesResourceValidationCode.INVALID_STATE)
        return advance(FilesResourceState.SHARED)
    }

    fun revoke(): FilesResourceRegistration {
        if (state == FilesResourceState.REVOKED) filesInvalid(FilesResourceValidationCode.INVALID_STATE)
        return advance(FilesResourceState.REVOKED)
    }

    private fun advance(next: FilesResourceState): FilesResourceRegistration {
        if (revision == Long.MAX_VALUE) filesInvalid(FilesResourceValidationCode.INVALID_REVISION)
        // No retarget/revise/re-enable operation: replacement enrollment gets four fresh IDs.
        return FilesResourceRegistration(generation, reference, revision + 1, next, draft)
    }

    override fun equals(other: Any?): Boolean = other is FilesResourceRegistration && generation == other.generation &&
        reference == other.reference && revision == other.revision && state == other.state && draft == other.draft
    override fun hashCode(): Int = listOf(generation, reference, revision, state, draft).hashCode()
    override fun toString(): String = "FilesResourceRegistration(redacted)"

    companion object {
        fun create(generation: String, draft: FilesResourceDraft): FilesResourceRegistration {
            filesGeneration(generation)
            return FilesResourceRegistration(generation, FilesResourceReference.createNew(), 1, FilesResourceState.PRIVATE, draft)
        }

        /** Restoration validates metadata; it does not authorize state transitions or side effects. */
        fun restore(generation: String, reference: FilesResourceReference, revision: Long,
                    state: FilesResourceState, draft: FilesResourceDraft): FilesResourceRegistration {
            filesGeneration(generation)
            filesRevision(revision)
            return FilesResourceRegistration(generation, reference, revision, state, draft)
        }
    }
}

private val filesUuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
private fun filesGeneration(value: String) {
    if (!filesUuid.matches(value)) filesInvalid(FilesResourceValidationCode.INVALID_GENERATION)
}
private fun filesRevision(value: Long) {
    if (value < 1) filesInvalid(FilesResourceValidationCode.INVALID_REVISION)
}
private fun filesText(value: String, maximum: Int): Boolean {
    if (value.length !in 1..maximum) return false
    var index = 0
    while (index < value.length) {
        val char = value[index++]
        if (Character.isISOControl(char) || char == '\u2028' || char == '\u2029' || Character.isLowSurrogate(char)) return false
        if (Character.isHighSurrogate(char) && (index >= value.length || !Character.isLowSurrogate(value[index++]))) return false
    }
    return true
}
private fun filesInvalid(code: FilesResourceValidationCode): Nothing = throw FilesResourceValidationException(code)
