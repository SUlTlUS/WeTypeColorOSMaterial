package dev.liquidglass.ios27

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

/**
 * iOS-style pressed mask for ordinary native controls.
 *
 * Keeps the hit target supplied by the caller, suppresses Android's radial ripple,
 * and draws one uniform pressed overlay clipped to the visible control shape.
 * Glass controls keep their own InteractiveHighlight implementation instead.
 */
@Composable
fun Modifier.ios27PressedClickable(
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    pressedColor: Color = LocalIos27PageColors.current.pressed,
    onClick: () -> Unit,
): Modifier = ios27PressedClickable(shape, enabled, role, pressedColor, maskEdges = null, onClick = onClick)

@Composable
fun Modifier.ios27PressedClickable(
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    pressedColor: Color = LocalIos27PageColors.current.pressed,
    maskEdges: Ios27PressMaskEdges?,
    onClick: () -> Unit,
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(interactions) {
        // A tap inside one frame can collapse a collected Boolean to false.
        // Handle the events directly so its release fades from the pressed mask.
        interactions.interactions.filterIsInstance<PressInteraction>().collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> opacity.snapTo(1f)
                is PressInteraction.Release -> opacity.animateTo(0f, tween(120))
                is PressInteraction.Cancel -> opacity.snapTo(0f)
            }
        }
    }
    // A list mask can extend to the next separator's center; an outer clip would
    // cut that half-line extension. Clip content separately in that case.
    val contentClip = if (maskEdges == null) Modifier.clip(shape) else Modifier
    return then(contentClip)
        .clickable(
            interactionSource = interactions,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
        .drawWithContent {
            if (maskEdges == null) {
                drawContent()
                if (opacity.value > 0f) drawRect(pressedColor, alpha = opacity.value)
            } else {
                val contentPath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
                clipPath(contentPath) { this@drawWithContent.drawContent() }
                if (opacity.value > 0f) drawListPressMask(shape, maskEdges, pressedColor, opacity.value)
            }
        }
}
