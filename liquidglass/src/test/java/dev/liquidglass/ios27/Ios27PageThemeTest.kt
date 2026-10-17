package dev.liquidglass.ios27

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.*
import org.junit.Test

class Ios27PageThemeTest {
    @Test fun completePagePaletteMatchesLocalVariables() {
        val light = ios27PageColors(false)
        val dark = ios27PageColors(true)
        assertEquals(Color(0xFF3C3C43).copy(alpha = 0.6f), light.secondaryLabel)
        assertEquals(Color(0xFFEBEBF5).copy(alpha = 0.7f), dark.secondaryLabel)
        assertEquals(Color(0xFF767680).copy(alpha = 0.12f), light.pressed)
        assertEquals(Color(0xFF767680).copy(alpha = 0.24f), dark.pressed)
        assertEquals(Color(0xFF0088FF), light.accent)
        assertEquals(Color(0xFF0091FF), dark.accent)
    }
    @Test fun pageFontDoesNotDependOnMaterialTypography() {
        assertEquals(FontFamily.SansSerif, Ios27Tokens.Type.Body.fontFamily)
        assertEquals(FontFamily.SansSerif, Ios27Tokens.Type.LargeTitle.fontFamily)
    }

    @Test fun sectionAndImageVariantsMatchRecordedKitDimensions() {
        assertEquals(45f, Ios27Tokens.ProminentHeaderHeight, 0f)
        assertEquals(48f, Ios27Tokens.ExtraProminentHeaderHeight, 0f)
        assertEquals(32f, Ios27Tokens.SectionFooterHeight, 0f)
        assertEquals(30f, Ios27Tokens.IconSize, 0f)
        assertEquals(7f, Ios27Tokens.IconRadius, 0f)
        assertEquals(44f, Ios27Tokens.TallIconSize, 0f)
        assertEquals(11f, Ios27Tokens.TallIconRadius, 0f)
    }

    @Test fun lightGroupedBackgroundsMatchReference() {
        val palette = ios27PageColors(false)
        assertEquals(Color(0xFFF2F2F7), palette.page)
        assertEquals(Color.White, palette.group)
        assertEquals(Color.Black, palette.label)
    }

    @Test fun lightPlainPagesUseWhiteBackground() {
        val palette = ios27PageColors(false, Ios27PageSurface.Plain)
        assertEquals(Color.White, palette.page)
        assertEquals(Color.White, palette.group)
        assertEquals(Color.Black, palette.label)
    }

    @Test fun darkPlainPagesStayBlack() {
        val palette = ios27PageColors(true, Ios27PageSurface.Plain)
        assertEquals(Color.Black, palette.page)
        assertEquals(Color(0xFF1C1C1E), palette.group)
    }

    @Test fun darkGroupedBackgroundsAreNotLightSurfaces() {
        val palette = ios27PageColors(true)
        assertEquals(Color.Black, palette.page)
        assertEquals(Color(0xFF1C1C1E), palette.group)
        assertNotEquals(palette.page, palette.group)
        assertEquals(Color.White, palette.label)
        assertNotEquals(ios27PageColors(false).secondaryLabel, palette.secondaryLabel)
    }

    @Test fun latestRowsUse52And68RatherThanStaleSpecHeights() {
        assertEquals(52f, Ios27Tokens.RowHeight, 0f)
        assertEquals(68f, Ios27Tokens.TallRowHeight, 0f)
        assertEquals(16f, Ios27Tokens.RowPadding, 0f)
        assertEquals(16f, Ios27Tokens.ContentMargin, 0f)
        assertEquals(1f, Ios27Tokens.SeparatorHeight, 0f)
    }

    @Test fun separatorColorsMatchLocalFigmaInsteadOfNonOpaqueTokens() {
        assertEquals(Color(0xFFE6E6E6), ios27PageColors(false).separator)
        assertEquals(Color(0xFF363638), ios27PageColors(true).separator)
        assertEquals(Color(0xFF1A1A1A), ios27PageColors(true, Ios27PageSurface.SliderReference).separator)
    }

    @Test fun sliderReferenceDoesNotOverrideOrdinaryGroupedPages() {
        val reference = ios27PageColors(true, Ios27PageSurface.SliderReference)
        assertEquals(Color.Black, reference.group)
        assertEquals(Color.Black, reference.page)
        assertNotEquals(reference.group, ios27PageColors(true).group)
        assertEquals(ios27PageColors(false), ios27PageColors(false, Ios27PageSurface.SliderReference))
    }

    @Test fun layoutHierarchyUsesReferenceSpacingScale() {
        assertEquals(26f, Ios27Tokens.GroupRadius, 0f)
        assertEquals(10f, Ios27Tokens.GroupGap, 0f)
        assertEquals(54f, Ios27Tokens.CompactHeaderHeight, 0f)
        assertEquals(42f, Ios27Tokens.SectionHeaderHeight, 0f)
        assertEquals(34f, Ios27Tokens.Type.LargeTitle.fontSize.value, 0f)
        assertEquals(17f, Ios27Tokens.Type.Body.fontSize.value, 0f)
    }
}
