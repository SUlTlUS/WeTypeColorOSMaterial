package dev.liquidglass.components

import org.junit.Assert.*
import org.junit.Test

class ColorOs17BlurPolicyTest {
    @Test fun marketingVersionIsNotAndroidApiOrOplusSdk() {
        for (version in listOf("V17.0", "17.0.0", "ColorOS 17.0", "  V17.1  "))
            assertTrue(version, ColorOs17BlurPolicy.isColorOs17(version, ""))
        for (version in listOf("", "V16.0", "V18.0", "V170.0", "40", "Android 17"))
            assertFalse(version, ColorOs17BlurPolicy.isColorOs17(version, ""))
        assertTrue(ColorOs17BlurPolicy.isColorOs17("", "V17.0"))
        assertFalse(ColorOs17BlurPolicy.isColorOs17("V16.0", "V17.0"))
    }

    @Test fun vendorSdkThresholdMatchesExtractedGuard() {
        assertFalse(ColorOs17BlurPolicy.supportedSdk(32, 99))
        assertFalse(ColorOs17BlurPolicy.supportedSdk(33, 33))
        assertTrue(ColorOs17BlurPolicy.supportedSdk(33, 34))
        assertTrue(ColorOs17BlurPolicy.supportedSdk(40, 2))
    }

    @Test fun switchesAndUnknownAnimationFailClosed() {
        assertTrue(ColorOs17BlurPolicy.enabled(1, 0, 1))
        assertTrue(ColorOs17BlurPolicy.enabled(1, 0, 2))
        for (level in listOf(null, -1, 0, 3)) assertFalse(ColorOs17BlurPolicy.enabled(1, 0, level))
        assertFalse(ColorOs17BlurPolicy.enabled(0, 0, 2))
        assertFalse(ColorOs17BlurPolicy.enabled(1, 1, 2))
    }

    @Test fun rawParametersAndGradientDirectionArePreserved() {
        assertEquals(30f, ColorOs17BlurPolicy.START_RADIUS, 0f)
        assertEquals(0f, ColorOs17BlurPolicy.END_RADIUS, 0f)
        assertEquals(3f, ColorOs17BlurPolicy.SPEED, 0f)
        assertArrayEquals(floatArrayOf(0f, .4f, 1f), ColorOs17BlurPolicy.positions(true), 0f)
        assertArrayEquals(floatArrayOf(0f, .7f, 1f), ColorOs17BlurPolicy.alphas(true), 0f)
        assertArrayEquals(floatArrayOf(0f, .4f), ColorOs17BlurPolicy.alphas(false), 0f)
        assertEquals(0f, ColorOs17BlurPolicy.strength(Float.NaN), 0f)
        assertEquals(0f, ColorOs17BlurPolicy.strength(-1f), 0f)
        assertEquals(1f, ColorOs17BlurPolicy.strength(2f), 0f)
    }

    @Test fun overlayFollowsMeasuredHeaderInsteadOfStretchingAcross128Dp() {
        assertEquals(88f, ColorOs17BlurPolicy.overlayHeight(80f, false), 0f)
        assertEquals(104f, ColorOs17BlurPolicy.overlayHeight(80f, true), 0f)
        assertEquals(48f, ColorOs17BlurPolicy.overlayHeight(24f, true), 0f)
        assertEquals(128f, ColorOs17BlurPolicy.overlayHeight(null, true), 0f)
        assertEquals(128f, ColorOs17BlurPolicy.overlayHeight(Float.NaN, false), 0f)
        assertEquals(30f, ColorOs17BlurPolicy.SCROLL_REVEAL_DISTANCE_DP, 0f)
    }
}
