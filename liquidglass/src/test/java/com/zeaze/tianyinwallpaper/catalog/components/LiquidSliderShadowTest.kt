package com.zeaze.tianyinwallpaper.catalog.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidSliderShadowTest {
    @Test fun idleMatchesReferenceLayers() {
        val ambient = sliderAmbientShadow(0f)
        val contact = sliderContactShadow(0f)
        assertEquals(13f, ambient.radius.value, 0f)
        assertEquals(6f, ambient.offset.y.value, 0f)
        assertEquals(0.12f, ambient.color.alpha, 0.002f)
        assertEquals(1f, ambient.alpha, 0f)
        assertEquals(4f, contact.radius.value, 0f)
        assertEquals(0.5f, contact.offset.y.value, 0f)
        assertEquals(0.12f, contact.color.alpha, 0.002f)
    }

    @Test fun pressedPreservesOriginalShadow() {
        assertEquals(0f, sliderAmbientShadow(1f).alpha, 0f)
        val contact = sliderContactShadow(1f)
        assertEquals(4f, contact.radius.value, 0f)
        assertEquals(4f / 6f, contact.offset.y.value, 0f)
        assertEquals(0.05f, contact.color.alpha, 0.002f)
    }

    @Test fun springOvershootIsClamped() {
        assertEquals(sliderContactShadow(0f), sliderContactShadow(-0.1f))
        assertEquals(sliderContactShadow(1f), sliderContactShadow(1.1f))
        assertEquals(0.5f, sliderAmbientShadow(0.5f).alpha, 0f)
    }
}
