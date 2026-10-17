package dev.liquidglass.ios27

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Offsets from the row's measured edges, not extra layout space. */
@Immutable
data class Ios27PressMaskEdges(val top: Dp = 0.dp, val bottom: Dp = 0.dp)

internal val LocalIos27RowPressMaskEdges = staticCompositionLocalOf { Ios27PressMaskEdges() }

internal fun listPressMaskEdges(thickness: Dp, topSeparator: Boolean, bottomSeparator: Boolean) =
    Ios27PressMaskEdges(
        top = if (topSeparator) thickness / 2 else 0.dp,
        bottom = if (bottomSeparator) thickness / 2 else 0.dp,
    )

internal fun listPressMaskRect(width: Float, height: Float, top: Float, bottom: Float): Rect =
    Rect(0f, top.coerceAtMost(height), width, (height + bottom).coerceAtLeast(top))

internal fun DrawScope.drawListPressMask(shape: Shape, edges: Ios27PressMaskEdges, color: Color, alpha: Float) {
    val bounds = listPressMaskRect(size.width, size.height, edges.top.toPx(), edges.bottom.toPx())
    val path = Path().apply {
        addOutline(shape.createOutline(Size(bounds.width, bounds.height), layoutDirection, this@drawListPressMask))
        translate(Offset(0f, bounds.top))
    }
    drawPath(path, color, alpha = alpha)
}

/** Only a full-row target opts in; embedded buttons keep their own control bounds. */
@Composable
fun Modifier.ios27ListPressedClickable(
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = ios27PressedClickable(
    shape = shape, enabled = enabled, role = role,
    maskEdges = LocalIos27RowPressMaskEdges.current,
    onClick = onClick,
)

/** Preserve a caller-owned swipe/press interaction while sharing the row mask geometry. */
@Composable
fun Modifier.ios27ListPressMask(pressed: Boolean, shape: Shape): Modifier {
    val edges = LocalIos27RowPressMaskEdges.current
    val color = LocalIos27PageColors.current.pressed
    return drawWithContent {
        if (pressed) drawListPressMask(shape, edges, color, 1f)
        drawContent()
    }
}
