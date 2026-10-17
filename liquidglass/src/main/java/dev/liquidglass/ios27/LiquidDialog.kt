package dev.liquidglass.ios27

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import com.zeaze.tianyinwallpaper.backdrop.Backdrop
import com.zeaze.tianyinwallpaper.backdrop.ShapeProvider
import com.zeaze.tianyinwallpaper.backdrop.backdrops.LayerBackdrop
import com.zeaze.tianyinwallpaper.backdrop.drawBackdrop
import com.zeaze.tianyinwallpaper.backdrop.effects.blur
import com.zeaze.tianyinwallpaper.backdrop.effects.colorControls
import com.zeaze.tianyinwallpaper.backdrop.effects.lens
import com.zeaze.tianyinwallpaper.backdrop.highlight.Highlight
import com.zeaze.tianyinwallpaper.backdrop.shadow.Shadow
import com.zeaze.tianyinwallpaper.backdrop.shadow.ShadowElement

/**
 * Reusable Liquid Glass dialog shell. The caller supplies only the dialog content;
 * the scrim, independent panel placement, glass material, edge highlight, and shadow
 * remain centralized here for all dialog-like examples.
 */
@Composable
fun LiquidDialog(
    backdrop: Backdrop?,
    onDismissRequest: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    cornerRadius: Dp = 34.dp,
    surfaceColor: Color,
    scrimColor: Color,
    dark: Boolean,
    description: String = "液态玻璃弹窗",
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidDialogLayer(
        backdrop = backdrop,
        onDismissRequest = onDismissRequest,
        panelModifier = modifier.size(width, height),
        alignment = alignment,
        offsetX = offsetX,
        offsetY = offsetY,
        shape = RoundedCornerShape(cornerRadius),
        surfaceColor = surfaceColor,
        scrimColor = scrimColor,
        dark = dark,
        description = description,
        content = content,
    )
}

/**
 * The structural hierarchy used by every LiquidDialog:
 *
 * Root
 * ├─ Scrim / outside-dismiss layer
 * └─ Panel glass layer
 *    └─ Content
 *
 * Sheet-style dialogs reuse this directly so they cannot accidentally introduce
 * a second clipping or glass container around the panel.
 */
@Composable
fun LiquidDialogLayer(
    backdrop: Backdrop?,
    onDismissRequest: () -> Unit,
    panelModifier: Modifier,
    shape: Shape,
    surfaceColor: Color,
    scrimColor: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    fallbackSurface: Color = surfaceColor,
    exportedBackdrop: LayerBackdrop? = null,
    description: String = "液态玻璃弹窗",
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().semantics { contentDescription = description }) {
        Box(
            Modifier.fillMaxSize().background(scrimColor).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest,
            ),
        )
        Box(
            Modifier.align(alignment)
                .offset(offsetX, offsetY)
                .then(panelModifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .liquidDialogSurface(
                    backdrop = backdrop,
                    shape = shape,
                    surface = surfaceColor,
                    fallbackSurface = fallbackSurface,
                    dark = dark,
                    exportedBackdrop = exportedBackdrop,
                ),
            content = content,
        )
    }
}

/** A fixed capsule action matching the buttons used by the original LiquidDialog. */
@Composable
fun LiquidDialogButton(
    text: String,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(color = textColor, fontSize = 16.sp),
) {
    Row(
        modifier
            .background(bgColor, Capsule())
            .ios27PressedClickable(
                shape = Capsule(),
                role = Role.Button,
                onClick = onClick,
            )
            .height(48.dp).semantics { contentDescription = text },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = textStyle)
    }
}

/**
 * The exact dialog shadow as an independent modifier. This is useful while a panel's
 * glass/body is temporarily clipped for an animation: the shadow can stay outside that
 * clip while retaining the same authored radius/offset/color as [liquidDialogSurface].
 */
fun Modifier.liquidDialogShadow(
    shape: Shape,
    alpha: Float = 1f,
): Modifier = then(
    ShadowElement(ShapeProvider { shape }) {
        Shadow(
            radius = 48.dp,
            offset = DpOffset(0.dp, 8.dp),
            color = Color.Black.copy(alpha = 0.25f),
            alpha = alpha.coerceIn(0f, 1f),
        )
    },
)

/**
 * Shared LiquidDialog glass panel surface.
 *
 * Dialog-like components that need their own geometry (for example a bottom sheet
 * with device-derived lower corners) should reuse this instead of duplicating the
 * backdrop/effect stack.
 */
fun Modifier.liquidDialogSurface(
    backdrop: Backdrop?,
    shape: Shape,
    surface: Color,
    fallbackSurface: Color = surface,
    dark: Boolean,
    saturation: Float = 1.15f,
    surfaceOverlay: Color? = null,
    surfaceOverlayBlendMode: BlendMode = BlendMode.SrcOver,
    exportedBackdrop: LayerBackdrop? = null,
    depthEffect: Boolean = true,
    showHighlight: Boolean = true,
    showShadow: Boolean = true,
): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < 33) {
        return clip(shape).background(fallbackSurface)
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(brightness = if (dark) 0f else 0.2f, saturation = saturation)
            blur(if (dark) 8.dp.toPx() else 16.dp.toPx())
            lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = depthEffect)
        },
        highlight = {
            if (showHighlight) {
                Highlight.Default.copy(width = 0.75.dp, blurRadius = 1.dp, alpha = 1f)
            } else {
                null
            }
        },
        shadow = {
            if (showShadow) {
                Shadow(
                    radius = 48.dp,
                    offset = DpOffset(0.dp, 8.dp),
                    color = Color.Black.copy(alpha = 0.25f),
                )
            } else {
                null
            }
        },
        exportedBackdrop = exportedBackdrop,
        onDrawSurface = {
            drawRect(surface)
            surfaceOverlay?.let { drawRect(it, blendMode = surfaceOverlayBlendMode) }
        },
    )
}
