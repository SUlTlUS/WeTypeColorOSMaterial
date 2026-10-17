package dev.liquidglass.ios27

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class Ios27ListTypographyTest {
    @Test fun hostMediumWeightPreservesTextMetricsAndFrameworkDefaults() {
        val original = Ios27ListTypography()
        val host = original.copy(
            title = original.title.copy(fontWeight = FontWeight.Medium),
            sectionWeight = FontWeight.Medium,
        )
        assertEquals(FontFamily.SansSerif, host.title.fontFamily)
        assertEquals(FontWeight.Medium, host.title.fontWeight)
        assertEquals(original.title.fontSize, host.title.fontSize)
        assertEquals(original.title.lineHeight, host.title.lineHeight)
        assertEquals(original.title.letterSpacing, host.title.letterSpacing)
        assertEquals(Ios27Tokens.Type.Body, Ios27ListTypography().title)
        assertEquals(FontWeight.SemiBold, Ios27ListTypography().sectionWeight)
    }
}
