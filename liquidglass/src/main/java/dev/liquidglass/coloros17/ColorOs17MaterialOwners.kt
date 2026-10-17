package dev.liquidglass.coloros17

import java.util.IdentityHashMap

/** Configuration changes may overlap old/new sessions sharing one foreign singleton. */
internal class ColorOs17MaterialOwners {
    private val owners = IdentityHashMap<Any, Int>()

    fun acquire(manager: Any, register: () -> Unit) {
        val count = owners[manager] ?: 0
        if (count == 0) register()
        owners[manager] = count + 1
    }

    fun release(manager: Any, unregister: () -> Unit) {
        val count = owners.getValue(manager)
        if (count == 1) {
            owners.remove(manager)
            unregister()
        } else owners[manager] = count - 1
    }
}
