package dev.liquidglass.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ListeningMode(val label: String) {
    Off("关闭"),
    Transparency("通透模式"),
    Adaptive("自适应"),
    NoiseCancellation("主动降噪"),
}

@Immutable
data class ListeningModeColors(
    val track: Color,
    val selection: Color,
    val content: Color,
    val heading: Color,
    val separator: Color,
    val press: Color,
)

/** Geometry is estimated from the supplied screenshot, not attributed to Figma. */
object ListeningModeDefaults {
    val BarHeight = 68.dp
    val SelectionInset = 4.dp
    val IconSize = 40.dp
    val MinimumTouchTarget = 48.dp
    val LightColors = ListeningModeColors(
        Color(0xFFE3E3E8), Color.White, Color.Black,
        Color(0xFF626264), Color(0xFFAAAAB0), Color.Black.copy(alpha = 0.08f),
    )
    val DarkColors = ListeningModeColors(
        Color(0xFF1C1C1E), Color(0xFF4B4B50), Color.White,
        Color(0xFF929295), Color(0xFF4B4B50), Color.White.copy(alpha = 0.10f),
    )

    fun showSeparator(boundary: Int, selected: ListeningMode): Boolean {
        require(boundary in 0..2)
        return boundary != selected.ordinal && boundary + 1 != selected.ordinal
    }

    internal fun contentWidth(availableWidth: Dp): Dp =
        availableWidth.coerceAtLeast(MinimumTouchTarget * ListeningMode.entries.size)

    internal fun separatorFraction(boundary: Int, direction: LayoutDirection): Float {
        require(boundary in 0..2)
        val fraction = (boundary + 1) / 4f
        return if (direction == LayoutDirection.Ltr) fraction else 1f - fraction
    }
}

/**
 * Controlled four-way selector. Labels remain outside the capsule and are tappable.
 * No backdrop, headset service or global preference is owned by this component.
 */
@Composable
fun ListeningModeSelector(
    selectedMode: ListeningMode,
    onModeSelected: (ListeningMode) -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = !MaterialTheme.colors.isLight,
    colors: ListeningModeColors = if (darkTheme) ListeningModeDefaults.DarkColors else ListeningModeDefaults.LightColors,
    title: String? = "听音模式",
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    labelStyle: TextStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 18.sp),
    icon: @Composable (ListeningMode, Color, Modifier) -> Unit = { mode, color, iconModifier ->
        ListeningModeIcon(mode, color, iconModifier)
    },
) {
    val haptics = LocalHapticFeedback.current
    val inputModeManager = LocalInputModeManager.current
    val position by animateFloatAsState(
        targetValue = selectedMode.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
        label = "listening-mode-selection",
    )
    Column(modifier.alpha(if (enabled) 1f else 0.45f)) {
        if (title != null) {
            Text(title, Modifier.padding(horizontal = 18.dp).semantics { heading() }, color = colors.heading,
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp,
                    lineHeight = 22.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val contentWidth = ListeningModeDefaults.contentWidth(maxWidth)
            // Preserve the normal geometry; only a narrower-than-192dp host scrolls.
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), enabled = contentWidth > maxWidth)) {
                Box(Modifier.width(contentWidth).selectableGroup().focusGroup()) {
                    val cellWidth = contentWidth / ListeningMode.entries.size
                    Box(Modifier.fillMaxWidth().height(ListeningModeDefaults.BarHeight)
                        .clip(CircleShape).background(colors.track)) {
                        Canvas(Modifier.fillMaxSize()) {
                            for (boundary in 0..2) {
                                if (ListeningModeDefaults.showSeparator(boundary, selectedMode)) {
                                    val x = size.width * ListeningModeDefaults.separatorFraction(boundary, layoutDirection)
                                    drawLine(colors.separator, Offset(x, 12.dp.toPx()),
                                        Offset(x, size.height - 12.dp.toPx()), strokeWidth = 1.dp.toPx())
                                }
                            }
                        }
                    }
                    Box(Modifier.offset {
                        IntOffset((cellWidth.toPx() * position + ListeningModeDefaults.SelectionInset.toPx()).roundToInt(),
                            ListeningModeDefaults.SelectionInset.roundToPx())
                    }.width((cellWidth - ListeningModeDefaults.SelectionInset * 2).coerceAtLeast(0.dp))
                        .height(ListeningModeDefaults.BarHeight - ListeningModeDefaults.SelectionInset * 2)
                        .background(colors.selection, CircleShape))
                    Row(Modifier.fillMaxWidth()) {
                        ListeningMode.entries.forEach { mode ->
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            val focused by interaction.collectIsFocusedAsState()
                            val iconScale by animateFloatAsState(if (pressed) 0.94f else 1f,
                                spring(dampingRatio = 1f, stiffness = 700f), label = "listening-mode-press")
                            Column(Modifier.weight(1f)
                                .sizeIn(minWidth = ListeningModeDefaults.MinimumTouchTarget,
                                    minHeight = ListeningModeDefaults.MinimumTouchTarget)
                                .semantics { contentDescription = mode.label }
                                .selectable(selected = mode == selectedMode, enabled = enabled,
                                    role = Role.RadioButton, interactionSource = interaction, indication = null,
                                    onClick = {
                                        if (mode != selectedMode) {
                                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            onModeSelected(mode)
                                        }
                                    }), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height(ListeningModeDefaults.BarHeight),
                                    contentAlignment = Alignment.Center) {
                                    if (pressed) Box(Modifier.fillMaxSize().padding(ListeningModeDefaults.SelectionInset)
                                        .background(colors.press, CircleShape))
                                    if (focused && inputModeManager.inputMode == InputMode.Keyboard) {
                                        Box(Modifier.fillMaxSize().padding(ListeningModeDefaults.SelectionInset)
                                            .border(2.dp, colors.content, CircleShape))
                                    }
                                    icon(mode, colors.content, Modifier.size(ListeningModeDefaults.IconSize).clearAndSetSemantics { }
                                        .graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(mode.label, Modifier.fillMaxWidth().clearAndSetSemantics { }, color = colors.content,
                                    style = labelStyle, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Scalable vector silhouettes drawn in a 40x40 viewport, not font glyphs/emoji. */
@Composable
fun ListeningModeIcon(mode: ListeningMode, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val factor = minOf(size.width, size.height) / 40f
        scale(factor, factor, pivot = Offset.Zero) {
            val adaptive = mode == ListeningMode.Adaptive
            val head = if (adaptive) Offset(16f, 17f) else Offset(20f, 15f)
            drawOval(color, head - Offset(6.4f, 7.2f), Size(12.8f, 14.4f))
            val bodyCenter = if (adaptive) 16f else 20f
            val body = Path().apply {
                moveTo(bodyCenter - 13.5f, 37f)
                cubicTo(bodyCenter - 14.8f, 31.5f, bodyCenter - 6.7f, 25f, bodyCenter, 25f)
                cubicTo(bodyCenter + 6.7f, 25f, bodyCenter + 14.8f, 31.5f, bodyCenter + 13.5f, 37f)
                cubicTo(bodyCenter + 13.3f, 38.4f, bodyCenter + 12.1f, 38.8f, bodyCenter + 10.5f, 38.8f)
                lineTo(bodyCenter - 10.5f, 38.8f)
                cubicTo(bodyCenter - 12.1f, 38.8f, bodyCenter - 13.3f, 38.4f, bodyCenter - 13.5f, 37f)
                close()
            }
            drawPath(body, color)
            when (mode) {
                ListeningMode.Off, ListeningMode.NoiseCancellation -> {
                    val ring = Path().apply {
                        moveTo(11.7f, 23.4f)
                        cubicTo(4.3f, 15.9f, 10.4f, 2.7f, 20f, 2.7f)
                        cubicTo(29.6f, 2.7f, 35.7f, 15.9f, 28.3f, 23.4f)
                    }
                    drawPath(ring, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                }
                ListeningMode.Transparency -> {
                    repeat(11) { index ->
                        val angle = (145 + index * 25) * Math.PI / 180
                        drawCircle(color, 1.9f, Offset(20f + cos(angle).toFloat() * 12f,
                            15f + sin(angle).toFloat() * 13f))
                    }
                }
                ListeningMode.Adaptive -> {
                    fun sparkle(x: Float, y: Float, radius: Float) {
                        val path = Path().apply {
                            moveTo(x, y - radius)
                            cubicTo(x + radius * .2f, y - radius * .2f, x + radius * .2f, y - radius * .2f, x + radius, y)
                            cubicTo(x + radius * .2f, y + radius * .2f, x + radius * .2f, y + radius * .2f, x, y + radius)
                            cubicTo(x - radius * .2f, y + radius * .2f, x - radius * .2f, y + radius * .2f, x - radius, y)
                            cubicTo(x - radius * .2f, y - radius * .2f, x - radius * .2f, y - radius * .2f, x, y - radius)
                            close()
                        }
                        drawPath(path, color)
                    }
                    sparkle(30f, 10f, 7f)
                    sparkle(24f, 3f, 3.5f)
                }
            }
        }
    }
}
