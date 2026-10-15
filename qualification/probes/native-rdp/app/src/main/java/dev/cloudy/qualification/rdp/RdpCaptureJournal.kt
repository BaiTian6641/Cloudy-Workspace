package dev.cloudy.qualification.rdp

/** Bounded synchronous UI evidence; the owner drains it even if native reap wins its queue. */
internal class RdpCaptureJournal {
    private var attemptId = ""
    private var active = false
    private val edges = mutableListOf<Map<String, Any>>()
    private var omitted = 0

    @Synchronized fun begin(attemptId: String) {
        require(attemptId.isNotEmpty())
        this.attemptId = attemptId; active = true; edges.clear(); omitted = 0
    }

    @Synchronized fun capture(attemptId: String, edge: String, reason: String, elapsedMillis: Long,
                              inputEvents: Long, relativeEvents: Long): Int? {
        if (!active || attemptId.isEmpty() || this.attemptId != attemptId || edge !in EDGES || reason !in REASONS) return null
        if (elapsedMillis < 0 || inputEvents < 0 || relativeEvents !in 0..inputEvents) return null
        if (edges.size == MAX_EDGES) { if (omitted < Int.MAX_VALUE) omitted++; return -1 }
        edges.add(linkedMapOf("sequence" to edges.size, "edge" to edge, "reason" to reason,
            "elapsedMillis" to elapsedMillis, "inputEvents" to inputEvents, "relativeInputEvents" to relativeEvents))
        return edges.lastIndex
    }

    @Synchronized fun acknowledgePending(elapsedMillis: Long, inputEvents: Long, relativeEvents: Long) {
        for (index in edges.indices) {
            val edge = edges[index]
            if ("ownerElapsedMillis" in edge || elapsedMillis < edge.getValue("elapsedMillis") as Long ||
                inputEvents < edge.getValue("inputEvents") as Long || relativeEvents < edge.getValue("relativeInputEvents") as Long) continue
            // Main-thread snapshots describe the actual callback. These additional
            // counters describe the later owner observation, never the same instant.
            edges[index] = edge + mapOf("ownerElapsedMillis" to elapsedMillis,
                "ownerInputEvents" to inputEvents, "ownerRelativeInputEvents" to relativeEvents)
        }
    }

    @Synchronized fun snapshot() = edges.map { it.toMap() }
    @Synchronized fun omitted() = omitted
    @Synchronized fun retire() { active = false }

    companion object {
        const val MAX_EDGES = 32
        private val EDGES = setOf("capture-requested", "capture-confirmed", "capture-timeout", "release-requested", "capture-released", "capture-lost", "capture-rejected")
        private val REASONS = setOf("operator", "focus-loss", "window-focus-loss", "connection-loss", "detached", "system", "cancel", "timeout")
    }
}
