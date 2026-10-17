package dev.liquidglass.ios27

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class Ios27WindowDensityTest {
    private val system = Density(3.5f, 1.3f)

    private fun scale(width: Float, height: Float) =
        ios27WindowDensity(system, width, height).density / system.density

    @Test fun phoneScaleComesFromSystemWindowDp() {
        assertEquals(0.904f, scale(402f * 0.904f, 800f), 0.000001f)
        assertEquals(360f / 402f, scale(360f, 800f), 0.000001f)
    }

    @Test fun widerPhonesApproachOneAndNeverEnlarge() {
        assertEquals(390f / 402f, scale(390f, 844f), 0.000001f)
        assertEquals(1f, scale(402f, 874f), 0f)
        assertEquals(1f, scale(440f, 900f), 0f)
    }

    @Test fun tabletKeepsSystemDensity() {
        assertEquals(system.density, ios27WindowDensity(system, 800f, 1280f).density, 0f)
    }

    @Test fun rotationUsesSameShortSide() {
        assertEquals(scale(360f, 800f), scale(800f, 360f), 0f)
        assertEquals(scale(800f, 1280f), scale(1280f, 800f), 0f)
    }

    @Test fun splitWindowRecomputesAndClampsAtLowerBound() {
        assertEquals(1f, scale(800f, 1280f), 0f)
        assertEquals(350f / 402f, scale(350f, 1280f), 0.000001f)
        assertEquals(0.85f, scale(300f, 1280f), 0.000001f)
        assertEquals(0.85f, scale(1280f, 300f), 0.000001f)
        assertEquals(0.85f, scale(0f, 800f), 0.000001f)
    }

    @Test fun systemFontScaleIsNeverResetOrMultiplied() {
        for (fontScale in listOf(0.85f, 1f, 1.3f, 2f)) {
            val native = Density(3.5f, fontScale)
            val adapted = ios27WindowDensity(native, 360f, 800f)
            assertEquals(native.fontScale, adapted.fontScale, 0f)
            assertEquals(native.density * (360f / 402f), adapted.density, 0.000001f)
        }
    }
}
