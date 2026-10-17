package dev.liquidglass.ios27

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import kotlin.math.roundToInt

/** Only input policy changes. Source coordinates, typography and drawing stay intact. */
@Composable
fun Ios27AndroidDesignBehavior(designDensity: Density, content: @Composable () -> Unit) {
    val deviceDensity = LocalDensity.current
    val nativeConfiguration = LocalViewConfiguration.current
    val ratio = deviceDensity.density / designDensity.density
    val configuration = remember(nativeConfiguration, ratio) {
        object : ViewConfiguration by nativeConfiguration {
            override val minimumTouchTargetSize = DpSize(
                nativeConfiguration.minimumTouchTargetSize.width * ratio,
                nativeConfiguration.minimumTouchTargetSize.height * ratio,
            )
        }
    }
    CompositionLocalProvider(
        LocalDensity provides designDensity,
        LocalViewConfiguration provides configuration,
        content = content,
    )
}

/** A traversal boundary, not a drawn panel, padding or new backdrop. */
fun Modifier.ios27AndroidPane(title: String, onDismiss: (() -> Unit)? = null): Modifier = semantics {
    paneTitle = title
    isTraversalGroup = true
    if (onDismiss != null) dismiss { onDismiss(); true }
}

internal fun androidProgressValue(requested: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0): Float? {
    if (!requested.isFinite()) return null
    val clamped = requested.coerceIn(range.start, range.endInclusive)
    if (steps <= 0) return clamped
    val spacing = (range.endInclusive - range.start) / (steps + 1)
    return (range.start + ((clamped - range.start) / spacing).roundToInt() * spacing)
        .coerceIn(range.start, range.endInclusive)
}

internal fun androidProgressStep(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, direction: Int): Float =
    (value + (range.endInclusive - range.start) / (if (steps > 0) steps + 1 else 20) * direction)
        .coerceIn(range.start, range.endInclusive)

/** Adds TalkBack/keyboard adjustment without changing the custom pointer renderer. */
fun Modifier.ios27AndroidProgress(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueDescription: String? = null,
    onFinished: () -> Unit = {},
    onValueChange: (Float) -> Unit,
): Modifier = composed {
    require(range.start.isFinite() && range.endInclusive.isFinite() && range.endInclusive > range.start)
    require(steps >= 0)
    fun update(requested: Float): Boolean {
        val next = androidProgressValue(requested, range, steps) ?: return false
        if (next == value) return false
        onValueChange(next)
        onFinished()
        return true
    }
    this.onKeyEvent { event ->
        val direction = when (event.key) {
            Key.DirectionLeft, Key.DirectionDown -> -1
            Key.DirectionRight, Key.DirectionUp -> 1
            else -> 0
        }
        val supported = direction != 0 || event.key == Key.MoveHome || event.key == Key.MoveEnd
        if (!supported) false
        else {
            if (event.type == KeyEventType.KeyDown) {
                update(when (event.key) {
                    Key.MoveHome -> range.start
                    Key.MoveEnd -> range.endInclusive
                    else -> androidProgressStep(value, range, steps, direction)
                })
            }
            true
        }
    }.focusable().clearAndSetSemantics {
        contentDescription = label
        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(range.start, range.endInclusive), range, steps)
        stateDescription = valueDescription ?: "${((value - range.start) / (range.endInclusive - range.start) * 100).toInt()}%"
        setProgress { update(it) }
    }
}

/** Remove covered content from the accessibility tree, without changing rendering/hit testing. */
fun Modifier.ios27AndroidBackgroundBlocked(blocked: Boolean): Modifier =
    if (blocked) clearAndSetSemantics { } else this
