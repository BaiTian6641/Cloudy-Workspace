package dev.cloudy.qualification.rdp

import java.util.Collections

/** Only authored operation names, class identifiers and allowlisted integer slots survive. */
internal class RdpCallbackFailure private constructor(
    val operation: String,
    val exceptionClass: String,
    val elapsedMillis: Long,
    private val dimensions: Map<String, Int>,
) {
    fun journalFields(): Map<String, Any> = linkedMapOf<String, Any>(
        "operation" to operation, "exceptionClass" to exceptionClass, "elapsedMillis" to elapsedMillis,
    ).apply { putAll(dimensions) }

    fun summary() = "Callback $operation: $exceptionClass"

    companion object {
        private val slots = mapOf(
            "settings" to listOf("width", "height", "bpp"),
            "resize" to listOf("width", "height", "bpp"),
            "graphics-update" to listOf("x", "y", "width", "height"),
            "pointer-set" to listOf("width", "height", "hotX", "hotY"),
            "pointer-position" to listOf("x", "y"),
        )
        private val otherOperations = setOf(
            "pre-connect", "connected", "native-failure", "disconnecting", "disconnected",
            "owner-action", "authenticate", "verify-x509", "unsupported-gateway", "unexpected-certificate-route",
            "changed-certificate-rejected", "unsupported-experimental", "unsupported-clipboard",
            "pointer-null", "pointer-default", "unsupported-rail",
        )
        private val classPattern = Regex("[A-Za-z_$][A-Za-z0-9_$]{0,79}")

        fun create(operation: String, exceptionClass: String, values: IntArray, elapsedMillis: Long = 0): RdpCallbackFailure? {
            val names = slots[operation]
            if (names == null && operation !in otherOperations) return null
            if (names != null && names.size != values.size) return null
            // An anonymous/oversized/non-identifier class name conveys no reliable class.
            // Never truncate arbitrary text into a journal identifier.
            val boundedClass = exceptionClass.takeIf { classPattern.matches(it) } ?: "Throwable"
            val fields = names?.mapIndexed { index, name -> name to values[index] }?.toMap() ?: emptyMap()
            return RdpCallbackFailure(operation, boundedClass, elapsedMillis.coerceAtLeast(0), Collections.unmodifiableMap(fields))
        }
    }
}

/** Capture is synchronous with the failure; persistence stays on the owner actor. */
internal class RdpFailureTracker {
    internal class Token
    private var token: Token? = null
    private var instance = 0L
    private var active = false
    private var first: RdpCallbackFailure? = null

    @Synchronized fun begin(instance: Long = 0): Token {
        check(!active)
        this.instance = instance
        return Token().also { token = it; active = true; first = null }
    }

    @Synchronized fun bind(expected: Token, instance: Long) {
        check(token === expected && active && this.instance == 0L && instance != 0L)
        this.instance = instance
    }

    @Synchronized fun capture(
        instance: Long,
        operation: String,
        exceptionClass: String,
        values: IntArray,
        elapsedMillis: Long = 0,
        onCaptured: () -> Unit,
    ): Token? {
        if (instance == 0L || instance != this.instance) return null
        return captureFirst(operation, exceptionClass, values, elapsedMillis, onCaptured)
    }

    @Synchronized fun captureOwner(
        expected: Token?, exceptionClass: String, elapsedMillis: Long, onCaptured: () -> Unit,
    ): Token? {
        if (expected == null || token !== expected) return null
        return captureFirst("owner-action", exceptionClass, intArrayOf(), elapsedMillis, onCaptured)
    }

    private fun captureFirst(
        operation: String, exceptionClass: String, values: IntArray, elapsedMillis: Long, onCaptured: () -> Unit,
    ): Token? {
        val current = token ?: return null
        if (!active || first != null) return null
        val failure = RdpCallbackFailure.create(operation, exceptionClass, values, elapsedMillis) ?: return null
        first = failure
        // Cancellation publication shares the begin/retire lock. A retired callback
        // cannot set the next attempt's cancellation flag after its token changes.
        onCaptured()
        return current
    }

    @Synchronized fun isActive(expected: Token) = token === expected && active
    @Synchronized fun snapshot(expected: Token?): RdpCallbackFailure? = if (token === expected) first else null
    @Synchronized fun retire(expected: Token?) {
        if (token === expected) active = false
        // Keep the immutable cause readable through final journal persistence and reap.
    }
}
