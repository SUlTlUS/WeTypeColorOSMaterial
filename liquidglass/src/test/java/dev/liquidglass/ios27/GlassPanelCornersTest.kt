package dev.liquidglass.ios27

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.zeaze.tianyinwallpaper.backdrop.AmbientHighlightShaderString
import com.zeaze.tianyinwallpaper.backdrop.DefaultHighlightShaderString
import com.zeaze.tianyinwallpaper.backdrop.RoundedRectRefractionShaderString
import com.zeaze.tianyinwallpaper.backdrop.RoundedRectRefractionWithDispersionShaderString
import com.zeaze.tianyinwallpaper.backdrop.ShapeProvider
import org.junit.Assert.*
import org.junit.Test

class GlassPanelCornersTest {
    @Test fun shaderShapeRetainsCornerTypeWhileOutlineWrapperDoesNot() {
        val original = RoundedCornerShape(32.dp)
        val provider = ShapeProvider { original }
        assertSame(original, provider.innerShape)
        assertTrue(provider.innerShape is CornerBasedShape)
        assertFalse(provider.shape is CornerBasedShape)
    }

    @Test fun distinctSheetCornersSurviveTheProvider() {
        val provider = ShapeProvider {
            RoundedCornerShape(topStart = 32.dp, topEnd = 24.dp,
                bottomStart = 48.dp, bottomEnd = 60.dp)
        }
        val shape = provider.innerShape as CornerBasedShape
        val size = Size(772f, 1200f)
        val density = Density(2f)
        assertEquals(64f, shape.topStart.toPx(size, density), 0f)
        assertEquals(48f, shape.topEnd.toPx(size, density), 0f)
        assertEquals(96f, shape.bottomStart.toPx(size, density), 0f)
        assertEquals(120f, shape.bottomEnd.toPx(size, density), 0f)
    }

    @Test fun compiledShaderSourcesUseCenteredCoordinatesForRadiusLookup() {
        listOf(RoundedRectRefractionShaderString, RoundedRectRefractionWithDispersionShaderString,
            DefaultHighlightShaderString, AmbientHighlightShaderString).forEach { shader ->
            assertTrue(shader.contains("float radius = radiusAt(centeredCoord, cornerRadii);"))
            assertFalse(shader.contains("float radius = radiusAt(coord, cornerRadii);"))
        }
    }
}
