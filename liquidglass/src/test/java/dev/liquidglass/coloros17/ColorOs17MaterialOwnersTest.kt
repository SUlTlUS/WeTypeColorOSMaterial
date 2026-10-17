package dev.liquidglass.coloros17

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorOs17MaterialOwnersTest {
    @Test fun rotatingSessionKeepsSharedObserversUntilBothAreClosed() {
        val owners = ColorOs17MaterialOwners()
        val manager = Any()
        val events = mutableListOf<String>()
        owners.acquire(manager) { events += "register" }
        owners.acquire(manager) { events += "unexpected registration" }
        owners.release(manager) { events += "unexpected unregister" }
        assertEquals(listOf("register"), events)
        owners.release(manager) { events += "unregister" }
        assertEquals(listOf("register", "unregister"), events)
    }

    @Test fun reopeningAfterAllSessionsCloseRegistersAgain() {
        val owners = ColorOs17MaterialOwners()
        val manager = Any()
        var registrations = 0
        repeat(2) {
            owners.acquire(manager) { registrations++ }
            owners.release(manager) {}
        }
        assertEquals(2, registrations)
    }

    @Test fun differentForeignSingletonsHaveIndependentLifetimes() {
        val owners = ColorOs17MaterialOwners()
        val first = Any()
        val second = Any()
        val closed = mutableListOf<Any>()
        owners.acquire(first) {}
        owners.acquire(second) {}
        owners.release(first) { closed += first }
        assertEquals(listOf(first), closed)
        owners.release(second) { closed += second }
        assertEquals(listOf(first, second), closed)
    }
}
