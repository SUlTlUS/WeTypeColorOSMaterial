package dev.liquidglass.ios27

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import com.zeaze.tianyinwallpaper.catalog.components.LiquidBottomTabsRegular
import com.zeaze.tianyinwallpaper.catalog.components.LiquidBottomTabsStyle
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButton
import com.zeaze.tianyinwallpaper.catalog.components.LiquidButtonStyle

/** Map the existing track material onto the existing button renderer; no new glass shader. */
internal fun splitSearchButtonStyle(track: LiquidBottomTabsStyle): LiquidButtonStyle =
    LiquidButtonStyle.Default.copy(
        blurRadius = track.trackBlurRadius,
        lensRadiusX = track.trackLensRadius,
        lensRadiusY = track.trackLensRadius,
    )

/**
 * Framework split-search adapter. Both siblings sample the same page backdrop
 * and use the same track material. Legacy extracted glass sources stay intact.
 * Geometry and the button's own press/drag animation are retained.
 */
@Composable
fun Ios27BottomTabsSplitSearch(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    onSearchClick: () -> Unit,
    backdrop: Backdrop?,
    tabsCount: Int,
    isLightTheme: Boolean,
    itemWidth: Dp = 118.dp,
    itemSpacing: Dp = 0.dp,
    searchWidth: Dp = 62.dp,
    searchHeight: Dp = 62.dp,
    searchGap: Dp = 16.dp,
    searchContentDescription: String = "Search",
    modifier: Modifier = Modifier,
    style: LiquidBottomTabsStyle = LiquidBottomTabsStyle.default(isLightTheme),
    searchContent: @Composable () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val searchModifier = Modifier.size(searchWidth, searchHeight).semantics {
        contentDescription = searchContentDescription
        role = Role.Button
    }
    Row(
        modifier.height(style.trackHeight),
        horizontalArrangement = Arrangement.spacedBy(searchGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        LiquidBottomTabsRegular(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = tabsCount,
            isLightTheme = isLightTheme,
            itemWidth = itemWidth,
            itemSpacing = itemSpacing,
            style = style,
            content = content,
        )
        if (backdrop != null) {
            LiquidButton(
                onClick = onSearchClick,
                backdrop = backdrop,
                modifier = searchModifier,
                buttonHeight = searchHeight,
                style = splitSearchButtonStyle(style),
                surfaceColor = style.containerColor,
                content = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        searchContent()
                    }
                },
            )
        } else {
            Box(
                searchModifier.clip(Capsule()).background(style.fallbackContainerColor)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center,
            ) { searchContent() }
        }
    }
}
