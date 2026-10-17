package dev.liquidglass.coloros17

import org.junit.Assert.*
import org.junit.Test

class ColorOs17ComponentCatalogTest {
    @Test fun keysAreUniqueAndSeparateFromFigmaExamples() {
        val entries = ColorOs17ComponentCatalog.entries
        assertEquals(29, entries.size)
        assertEquals(entries.size, entries.map { it.key }.distinct().size)
        assertTrue(entries.all { it.key.startsWith("coloros17-") })
        assertNull(ColorOs17ComponentCatalog.find("12740-24013"))
    }

    @Test fun allEntriesUseOriginalSettingsClasses() {
        assertTrue(ColorOs17ComponentCatalog.entries.all {
            it.className.startsWith("com.coui.appcompat.") &&
                it.category.startsWith("ColorOS 17 / ") &&
                it.sourcePath.endsWith(".java")
        })
        assertEquals("com/coui/appcompat/couiswitch/COUISwitch.java",
            ColorOs17ComponentCatalog.find("coloros17-switch")!!.sourcePath)
    }

    @Test fun popupKindsAreExplicit() {
        assertEquals(ColorOs17ComponentKind.Dialog, ColorOs17ComponentCatalog.find("coloros17-alert")!!.kind)
        assertEquals(ColorOs17ComponentKind.Sheet, ColorOs17ComponentCatalog.find("coloros17-sheet")!!.kind)
        assertEquals(ColorOs17ComponentKind.Menu, ColorOs17ComponentCatalog.find("coloros17-menu")!!.kind)
        assertEquals(ColorOs17ComponentKind.Preference, ColorOs17ComponentCatalog.find("coloros17-preference")!!.kind)
    }
}
