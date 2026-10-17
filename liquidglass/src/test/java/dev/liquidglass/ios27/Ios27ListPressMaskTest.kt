package dev.liquidglass.ios27

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class Ios27ListPressMaskTest {
    @Test fun adjacentMasksMeetAtTheSeparatorCenter() {
        for (scale in listOf(1f, 2.5f, 3.164f)) {
            val height = 52f * scale
            val half = Ios27Tokens.SeparatorRenderHeight / 2f * scale
            val first = listPressMaskRect(370f * scale, height, 0f, half)
            val next = listPressMaskRect(370f * scale, height, half, half)
            assertEquals(height + half, first.bottom, 0.0001f)
            assertEquals(first.bottom, height + next.top, 0.0001f)
        }
    }

    @Test fun middleRowOwnsExactlyCenterToCenter() {
        val bounds = listPressMaskRect(370f, 68f, 1f, 1f)
        assertEquals(1f, bounds.top, 0f)
        assertEquals(69f, bounds.bottom, 0f)
        assertEquals(68f, bounds.height, 0f)
    }

    @Test fun groupOuterEdgesAreNotExtended() {
        val first = listPressMaskEdges(2.dp, false, true)
        val last = listPressMaskEdges(2.dp, true, false)
        assertEquals(0.dp, first.top)
        assertEquals(1.dp, first.bottom)
        assertEquals(1.dp, last.top)
        assertEquals(0.dp, last.bottom)
        val lastBounds = listPressMaskRect(370f, 52f, last.top.value, last.bottom.value)
        assertEquals(52f, lastBounds.bottom, 0f)
    }

    @Test fun singleRowsAndHiddenSwipeSeparatorsHaveNoOffsets() {
        assertEquals(Ios27PressMaskEdges(), listPressMaskEdges(2.dp, false, false))
        assertEquals(Ios27PressMaskEdges(0.dp, 1.dp), listPressMaskEdges(2.dp, false, true))
        assertEquals(Ios27PressMaskEdges(1.dp, 0.dp), listPressMaskEdges(2.dp, true, false))
    }

    @Test fun offsetsFollowActualRenderedThicknessNotLogicalHairline() {
        val edges = listPressMaskEdges(Ios27Tokens.SeparatorRenderHeight.dp, true, true)
        assertEquals(Ios27Tokens.SeparatorRenderHeight / 2f, edges.top.value, 0f)
        assertEquals(edges.top, edges.bottom)
    }
}
