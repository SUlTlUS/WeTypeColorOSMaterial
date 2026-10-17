package dev.liquidglass.ios27

import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.catalog.components.LiquidBottomTabsStyle
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButtonStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class Ios27SplitSearchTabBarTest {
    @Test fun lightAndDarkSearchUseTheTrackOptics() {
        for (light in listOf(true, false)) {
            val track = LiquidBottomTabsStyle.default(light)
            val search = splitSearchButtonStyle(track)
            assertEquals(track.trackBlurRadius, search.blurRadius)
            assertEquals(track.trackLensRadius, search.lensRadiusX)
            assertEquals(track.trackLensRadius, search.lensRadiusY)
        }
    }

    @Test fun customTrackOpticsPropagateWithoutHardcodedCopies() {
        val track = LiquidBottomTabsStyle.default(true).copy(
            trackBlurRadius = 10.dp, trackLensRadius = 30.dp,
        )
        val search = splitSearchButtonStyle(track)
        assertEquals(10.dp, search.blurRadius)
        assertEquals(30.dp, search.lensRadiusX)
        assertEquals(30.dp, search.lensRadiusY)
    }

    @Test fun originalButtonMotionAndPaddingAreRetained() {
        val search = splitSearchButtonStyle(LiquidBottomTabsStyle.default(true))
        val original = LiquidButtonStyle.Default
        assertEquals(original.pressedExpansion, search.pressedExpansion)
        assertEquals(original.dragInitialDerivative, search.dragInitialDerivative)
        assertEquals(original.horizontalPadding, search.horizontalPadding)
        assertEquals(original.contentSpacing, search.contentSpacing)
    }
}
