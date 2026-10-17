package dev.liquidglass.ios27

import androidx.compose.ui.unit.Density

/**
 * Bounded settings-page scale. Width/height must be measured in SYSTEM dp,
 * before safe-area padding and outside any design-density provider.
 * This is a host adaptation, not a change to the original Figma coordinates.
 */
fun ios27WindowDensity(systemDensity: Density, widthDp: Float, heightDp: Float): Density {
    val shortSide = minOf(widthDp, heightDp)
    val scale = (shortSide / 402f).coerceIn(0.85f, 1f)
    return Density(systemDensity.density * scale, systemDensity.fontScale)
}
