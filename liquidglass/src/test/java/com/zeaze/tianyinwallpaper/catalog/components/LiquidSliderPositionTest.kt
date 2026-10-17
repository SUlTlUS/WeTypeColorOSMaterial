package com.zeaze.tianyinwallpaper.catalog.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidSliderPositionTest {
    @Test fun endpointsAlignWithTrack() {
        assertEquals(0f, sliderThumbTranslation(300f, 40f, 0f, true), 0f)
        assertEquals(260f, sliderThumbTranslation(300f, 40f, 1f, true), 0f)
        assertEquals(130f, sliderThumbTranslation(300f, 40f, 0.5f, true), 0f)
    }

    @Test fun rtlMirrorsFromStartEdge() {
        assertEquals(0f, sliderThumbTranslation(300f, 40f, 0f, false), 0f)
        assertEquals(-260f, sliderThumbTranslation(300f, 40f, 1f, false), 0f)
        assertEquals(-130f, sliderThumbTranslation(300f, 40f, 0.5f, false), 0f)
    }

    @Test fun springOvershootAndResizingStayInsideTrack() {
        for (width in listOf(40f, 120f, 300f, 800f)) {
            for (progress in listOf(-0.2f, 0f, 0.1f, 0.5f, 0.9f, 1f, 1.2f)) {
                val x = sliderThumbTranslation(width, 40f, progress, true)
                assertTrue(x >= 0f && x + 40f <= width)
            }
        }
        assertEquals(0f, sliderThumbTranslation(0f, 40f, 0.5f, true), 0f)
    }
}
