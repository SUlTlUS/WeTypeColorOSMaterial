package com.zeaze.tianyinwallpaper.ui.commom

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import com.zeaze.tianyinwallpaper.backdrop.drawPlainBackdrop
import com.zeaze.tianyinwallpaper.backdrop.effects.blur
import com.zeaze.tianyinwallpaper.backdrop.effects.runtimeShaderEffect
import dev.liquidglass.components.ColorOs17ProgressiveBlurOrFallback
import dev.liquidglass.components.ColorOs17BlurPolicy
import dev.liquidglass.components.LocalProgressiveBlurSurface
import androidx.compose.ui.graphics.isSpecified

@Composable
fun ProgressiveBlurContent(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    isLightTheme: Boolean = !isSystemInDarkTheme(),
    strength: Float = 1f,
    surfaceColor: Color = LocalProgressiveBlurSurface.current,
    appBarHeight: Dp? = null,
    scrollDriven: Boolean = false,
    visibility: Float = 1f,
) {
    val surface = if (surfaceColor.isSpecified) surfaceColor
        else if (isLightTheme) Color.White else Color(0xFF202020)
    val height = ColorOs17BlurPolicy.overlayHeight(appBarHeight?.value, scrollDriven).dp
    ColorOs17ProgressiveBlurOrFallback(modifier, isLightTheme, strength, visibility, height, surface) {
        FallbackProgressiveBlurContent(
            modifier = Modifier.alpha(ColorOs17BlurPolicy.strength(strength) * ColorOs17BlurPolicy.strength(visibility)),
            backdrop = backdrop,
            isLightTheme = isLightTheme,
            surfaceColor = surface,
            overlayHeight = height,
        )
    }
}

@Composable
private fun FallbackProgressiveBlurContent(
    modifier: Modifier,
    backdrop: Backdrop?,
    isLightTheme: Boolean,
    surfaceColor: Color,
    overlayHeight: Dp,
) {
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val tintColor = if (isLightTheme) Color.White else Color(0xFF808080)
    val fallbackColor = surfaceColor.copy(alpha = if (isLightTheme) 0.75f else 0.7f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = (if (backdrop != null) {
                Modifier.drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        // iOS27 top material uses a stronger blur near the top edge,
                        // then fades the material out through the content area.
                        blur(16.dp.toPx())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            runtimeShaderEffect(
                                key = "AlphaMask",
                                shaderString = """
uniform shader content;

uniform float2 size;

half4 main(float2 coord) {
    // iOS-style top material: fully present at the toolbar edge,
    // progressively disappears toward the content instead of a hard band.
    float edge = clamp(coord.y / size.y, 0.0, 1.0);
    float alpha = 1.0 - smoothstep(0.0, 1.0, edge);
    return content.eval(coord) * alpha;
}
"""
                                ,
                                uniformShaderName = "content"
                            ) {
                                setFloatUniform("size", size.width, size.height)
                                //setColorUniform("tint", tintColor)
                                //setFloatUniform("tintIntensity", 0.8f)
                            }
                        }
                    }
                )
            } else {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(fallbackColor, fallbackColor.copy(alpha = 0f))
                    )
                )
            })
                .height(overlayHeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            /*BasicText(
                text = "alpha-masked progressive blur",
                style = TextStyle(contentColor, 16.sp)
            )*/
        }
    }
}

