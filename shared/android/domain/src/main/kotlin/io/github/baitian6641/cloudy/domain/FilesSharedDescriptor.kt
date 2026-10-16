package io.github.baitian6641.cloudy.domain

/** Deliberately disclosed navigation metadata only; no endpoint, account or authority. */
class FilesSharedDescriptor private constructor(val reference: FilesResourceReference, val label: String) {
    override fun equals(other: Any?): Boolean = other is FilesSharedDescriptor && reference == other.reference && label == other.label
    override fun hashCode(): Int = 31 * reference.hashCode() + label.hashCode()
    override fun toString(): String = "FilesSharedDescriptor(redacted)"

    companion object {
        fun create(reference: FilesResourceReference, label: String): FilesSharedDescriptor {
            // Stored labels are already trimmed. A wire reply must not silently rewrite disclosure.
            require(WindowState.nameProblem(label) == null && label == label.trim()) { "INVALID_LABEL" }
            return FilesSharedDescriptor(reference, label)
        }
    }
}
