package dev.cloudy.qualification.usb

/** Correlates creator-owned immutable extras; stale broadcasts cannot consume a newer request. */
internal class PermissionRequests {
    data class Request(val generation: Long, val deviceId: Int, val nonce: String)
    private var pending: Request? = null
    fun begin(generation: Long, deviceId: Int, nonce: String): Request = Request(generation, deviceId, nonce).also { pending = it }
    fun cancel() { pending = null }
    fun isPending(request: Request): Boolean = pending == request
    fun complete(generation: Long, deviceId: Int, nonce: String?, currentGeneration: Long, selectedId: Int?): Request? {
        val request = pending ?: return null
        if (generation != request.generation || deviceId != request.deviceId || nonce != request.nonce ||
            currentGeneration != request.generation || selectedId != request.deviceId) return null
        pending = null
        return request
    }
}
