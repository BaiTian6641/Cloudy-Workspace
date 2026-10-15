package dev.cloudy.qualification.baseline

import org.junit.Assert.assertEquals
import org.junit.Test

class ListenerRegistryTest {
    private data class Subscription(val window: String, val listener: String)

    @Test
    fun stoppingOneActivityRemovesItsListenerWithoutThrowingOrRemovingTheOther() {
        val registry = ListenerRegistry<Subscription>()
        val first = Subscription("window-a", "activity-a")
        val second = Subscription("window-b", "activity-b")
        registry.add(first)
        registry.add(second)
        registry.removeMatching { it.listener == "activity-a" }
        assertEquals(listOf(second), registry.snapshot())
        // onDestroy follows onStop; repeated removal must remain harmless.
        registry.removeMatching { it.listener == "activity-a" }
        assertEquals(listOf(second), registry.snapshot())
    }

    @Test
    fun listenerRemovalDoesNotMutateAnExistingDeliverySnapshot() {
        val registry = ListenerRegistry<Subscription>()
        val first = Subscription("window-a", "activity-a")
        registry.add(first)
        val delivered = registry.snapshot()
        registry.removeMatching { true }
        registry.add(Subscription("window-a", "replacement-activity"))
        assertEquals(listOf(first), delivered)
        assertEquals("replacement-activity", registry.snapshot().single().listener)
    }
}
