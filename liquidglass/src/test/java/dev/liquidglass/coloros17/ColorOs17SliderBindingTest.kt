package dev.liquidglass.coloros17

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorOs17SliderBindingTest {
    @Test fun dragPreviewsThenCommitsOnlyAtRelease() {
        val events = mutableListOf<String>()
        val binding = ColorOs17SliderBinding({ events += "value=$it" }, { events += "save" })
        binding.start()
        binding.change(0, true)
        binding.change(500, true)
        binding.change(1000, true)
        assertEquals(listOf("value=0", "value=500", "value=1000"), events)
        binding.stop()
        assertEquals(listOf("value=0", "value=500", "value=1000", "save"), events)
    }

    @Test fun programmaticSynchronizationNeverNotifiesOrSaves() {
        val events = mutableListOf<String>()
        val binding = ColorOs17SliderBinding({ events += "value=$it" }, { events += "save" })
        binding.change(500, false)
        binding.start()
        binding.change(0, false)
        assertEquals(emptyList<String>(), events)
    }

    @Test fun keyboardAndAccessibilityChangesSaveWithoutTouchRelease() {
        val events = mutableListOf<String>()
        val binding = ColorOs17SliderBinding({ events += "value=$it" }, { events += "save" })
        binding.change(600, true)
        assertEquals(listOf("value=600", "save"), events)
    }
}
