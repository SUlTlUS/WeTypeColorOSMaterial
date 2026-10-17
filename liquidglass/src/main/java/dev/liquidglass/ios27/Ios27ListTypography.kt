package dev.liquidglass.ios27

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Host typography overrides; defaults preserve the framework list appearance. */
data class Ios27ListTypography(
    val title: TextStyle = Ios27Tokens.Type.Body,
    val sectionWeight: FontWeight = FontWeight.SemiBold,
)

val LocalIos27ListTypography = staticCompositionLocalOf { Ios27ListTypography() }
