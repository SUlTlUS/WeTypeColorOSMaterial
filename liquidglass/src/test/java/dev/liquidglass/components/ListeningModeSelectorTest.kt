package dev.liquidglass.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ListeningModeSelectorTest {
    @Test fun normalWidthsRemainIdenticalAndNarrowHostsKeep48dpTargets() {
        listOf(192, 320, 366, 402, 840).forEach { width ->
            assertEquals(width.dp, ListeningModeDefaults.contentWidth(width.dp))
        }
        listOf(0, 80, 160, 191).forEach { width ->
            assertEquals(192.dp, ListeningModeDefaults.contentWidth(width.dp))
        }
        assertEquals(48.dp, ListeningModeDefaults.MinimumTouchTarget)
    }
    @Test fun separatorLocationsMirrorWithTheRadioItemsInRtl() {
        assertEquals(listOf(.25f, .5f, .75f), (0..2).map {
            ListeningModeDefaults.separatorFraction(it, LayoutDirection.Ltr)
        })
        assertEquals(listOf(.75f, .5f, .25f), (0..2).map {
            ListeningModeDefaults.separatorFraction(it, LayoutDirection.Rtl)
        })
        ListeningMode.entries.forEach { mode ->
            val separators = (0..2).filter { ListeningModeDefaults.showSeparator(it, mode) }
            separators.forEach { boundary ->
                assertEquals(1f, ListeningModeDefaults.separatorFraction(boundary, LayoutDirection.Ltr) +
                    ListeningModeDefaults.separatorFraction(boundary, LayoutDirection.Rtl), 0f)
            }
        }
    }
    @Test fun referenceOrderAndLabels() {
        assertEquals(listOf("关闭", "通透模式", "自适应", "主动降噪"), ListeningMode.entries.map { it.label })
    }
    @Test fun separatorsDoNotTouchSelectedCapsule() {
        assertEquals(listOf(true, true, false), (0..2).map { ListeningModeDefaults.showSeparator(it, ListeningMode.NoiseCancellation) })
        assertEquals(listOf(false, true, true), (0..2).map { ListeningModeDefaults.showSeparator(it, ListeningMode.Off) })
        assertEquals(listOf(false, false, true), (0..2).map { ListeningModeDefaults.showSeparator(it, ListeningMode.Transparency) })
        assertEquals(listOf(true, false, false), (0..2).map { ListeningModeDefaults.showSeparator(it, ListeningMode.Adaptive) })
    }
    @Test fun referencePaletteAndInset() {
        assertEquals(Color.White, ListeningModeDefaults.LightColors.selection)
        assertEquals(Color(0xFF4B4B50), ListeningModeDefaults.DarkColors.selection)
        assertEquals(Color.Black, ListeningModeDefaults.LightColors.content)
        assertEquals(Color.White, ListeningModeDefaults.DarkColors.content)
        assertEquals(68f, ListeningModeDefaults.BarHeight.value)
        assertEquals(4f, ListeningModeDefaults.SelectionInset.value)
    }
}
