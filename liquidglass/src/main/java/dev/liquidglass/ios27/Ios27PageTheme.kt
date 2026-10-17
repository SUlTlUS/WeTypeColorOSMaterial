package dev.liquidglass.ios27

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class Ios27PageColors(
    val page: Color,
    val group: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val pressed: Color,
    val accent: Color,
)

enum class Ios27PageSurface { Grouped, Plain, SliderReference }

fun ios27PageColors(darkTheme: Boolean, surface: Ios27PageSurface = Ios27PageSurface.Grouped) = if (darkTheme) Ios27PageColors(
    Ios27Tokens.PageDark,
    if (surface == Ios27PageSurface.SliderReference) Ios27Tokens.GroupDark else Ios27Tokens.GroupedSurfaceDark,
    Ios27Tokens.LabelDark,
    Ios27Tokens.SecondaryLabelDark,
    // Linear Dodge: #1A1A1A separator over the #1C1C1E grouped surface.
    if (surface == Ios27PageSurface.SliderReference) Ios27Tokens.SeparatorDark else Color(0xFF363638),
    Ios27Tokens.PressedDark, Ios27Tokens.BlueDark,
) else Ios27PageColors(
    if (surface == Ios27PageSurface.Plain) Ios27Tokens.GroupLight else Ios27Tokens.PageLight,
    Ios27Tokens.GroupLight,
    Ios27Tokens.LabelLight,
    Ios27Tokens.SecondaryLabelLight,
    Ios27Tokens.SeparatorLight,
    Ios27Tokens.PressedLight,
    Ios27Tokens.BlueLight,
)

val LocalIos27PageColors = staticCompositionLocalOf { ios27PageColors(false) }

/** Only our page/list components read this local. It does not override MaterialTheme. */
@Composable
fun Ios27PageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    surface: Ios27PageSurface = Ios27PageSurface.Grouped,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalIos27PageColors provides ios27PageColors(darkTheme, surface), content = content)
}
