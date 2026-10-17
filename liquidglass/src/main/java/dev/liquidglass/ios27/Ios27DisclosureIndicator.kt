package dev.liquidglass.ios27

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Source-extracted iOS 27 navigation and accessory indicators.
 *
 * The normalized vector outlines and source boxes come from the shipped Figma
 * examples rather than hand-drawn approximations:
 * - Back: 12740-24013
 * - Picker previous/next/disclosure: 12740-24135
 * - DrillIn / Disclosure / PopupMenu / DragHandle: 12740-24203
 * - Find previous/next: 12740-24329
 * - Submenu: 12740-24173
 * - Glass / Secondary: 12740-35190
 */
enum class Ios27IndicatorVariant {
    Back,
    DrillIn,
    Disclosure,
    Submenu,
    Glass,
    Secondary,
    PickerDisclosure,
    PickerPrevious,
    PickerNext,
    FindPrevious,
    FindNext,
    PopupMenu,
}

/** Compatibility name kept for callers that only use disclosure-style arrows. */
typealias Ios27DisclosureVariant = Ios27IndicatorVariant

private data class IndicatorSource(
    val width: Float,
    val height: Float,
    val x: Float,
    val y: Float,
    val glyphSize: Float,
    val mirrorInRtl: Boolean = false,
    val path: () -> Path,
)

@Composable
fun Ios27IndicatorGlyph(
    variant: Ios27IndicatorVariant,
    modifier: Modifier = Modifier,
    color: Color = LocalIos27PageColors.current.secondaryLabel,
) {
    val source = indicatorSource(variant)
    val path = remember(variant) { source.path() }
    val rtl = source.mirrorInRtl && LocalLayoutDirection.current == LayoutDirection.Rtl

    Canvas(modifier.then(Modifier.size(source.width.dp, source.height.dp))) {
        val factor = size.width / source.width
        withTransform({
            if (rtl) scale(-1f, 1f, center)
            scale(factor, factor, Offset.Zero)
            translate(source.x, source.y)
            scale(source.glyphSize, -source.glyphSize, Offset.Zero)
        }) {
            drawPath(path, color)
        }
    }
}

@Composable
fun Ios27DisclosureIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalIos27PageColors.current.secondaryLabel,
    variant: Ios27DisclosureVariant = Ios27DisclosureVariant.DrillIn,
) {
    Ios27IndicatorGlyph(variant = variant, modifier = modifier, color = color)
}

/** Source 34x50 reorder accessory with the 22x25 glyph at x=12, y=12.5. */
@Composable
fun Ios27DragIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalIos27PageColors.current.secondaryLabel.copy(alpha = 0.5f),
) {
    val path = remember { dragHandlePath() }
    Canvas(modifier.then(Modifier.size(34.dp, 50.dp))) {
        val pt = density
        withTransform({
            translate(12.48291015625f * pt, (12.5f + 18.54296875f) * pt)
            scale(17f * pt, -17f * pt, Offset.Zero)
        }) {
            drawPath(path, color)
        }
    }
}

private fun indicatorSource(variant: Ios27IndicatorVariant): IndicatorSource = when (variant) {
    Ios27IndicatorVariant.Back -> IndicatorSource(
        width = 17f,
        height = 20f,
        x = 0.30712890625f,
        y = 16.04296875f,
        glyphSize = 17f,
        mirrorInRtl = true,
        path = ::backChevronPath,
    )
    Ios27IndicatorVariant.DrillIn -> IndicatorSource(
        width = 8f,
        height = 22f,
        x = 0f,
        y = 17.04296875f,
        glyphSize = 17f,
        mirrorInRtl = true,
        path = ::listChevronPath,
    )
    Ios27IndicatorVariant.Disclosure -> IndicatorSource(
        // Source INSTANCE is 8x32; its 11x18 TEXT child starts at (-3, 7).
        width = 8f,
        height = 32f,
        x = -2.63818359375f,
        y = 22.04296875f,
        glyphSize = 17f,
        mirrorInRtl = true,
        path = ::listChevronPath,
    )
    Ios27IndicatorVariant.Submenu -> IndicatorSource(
        width = 14f,
        height = 20f,
        x = 2.3564453125f,
        y = 15.33203125f,
        glyphSize = 15f,
        mirrorInRtl = true,
        path = ::submenuChevronPath,
    )
    Ios27IndicatorVariant.Glass -> IndicatorSource(
        width = 12f,
        height = 18f,
        x = 0.29443359375f,
        y = 14.33203125f,
        glyphSize = 15f,
        mirrorInRtl = true,
        path = ::glassChevronPath,
    )
    Ios27IndicatorVariant.Secondary -> IndicatorSource(
        width = 11f,
        height = 18f,
        x = 0f,
        y = 14.33203125f,
        glyphSize = 15f,
        mirrorInRtl = true,
        path = ::secondaryChevronPath,
    )
    Ios27IndicatorVariant.PickerDisclosure -> IndicatorSource(
        width = 9f,
        height = 18f,
        x = 0f,
        y = 13.62109375f,
        glyphSize = 13f,
        mirrorInRtl = true,
        path = ::submenuChevronPath,
    )
    Ios27IndicatorVariant.PickerPrevious -> IndicatorSource(
        width = 15f,
        height = 24f,
        x = 0.0390625f,
        y = 19.109375f,
        glyphSize = 20f,
        mirrorInRtl = true,
        path = ::pickerPreviousChevronPath,
    )
    Ios27IndicatorVariant.PickerNext -> IndicatorSource(
        width = 15f,
        height = 24f,
        x = 0.0390625f,
        y = 19.109375f,
        glyphSize = 20f,
        mirrorInRtl = true,
        path = ::pickerNextChevronPath,
    )
    Ios27IndicatorVariant.FindPrevious -> IndicatorSource(
        width = 22f,
        height = 36f,
        x = 1.669921875f,
        y = 24.04296875f,
        glyphSize = 17f,
        path = ::findPreviousChevronPath,
    )
    Ios27IndicatorVariant.FindNext -> IndicatorSource(
        width = 22f,
        height = 36f,
        x = 1.669921875f,
        y = 24.04296875f,
        glyphSize = 17f,
        path = ::findNextChevronPath,
    )
    Ios27IndicatorVariant.PopupMenu -> IndicatorSource(
        width = 13f,
        height = 22f,
        x = 0.9970703125f,
        y = 17.04296875f,
        glyphSize = 17f,
        path = ::popupMenuIndicatorPath,
    )
}

private fun pathFromCommands(data: FloatArray): Path {
    val path = Path()
    var i = 0
    while (i < data.size) {
        when (data[i++].toInt()) {
            0 -> path.close()
            1 -> {
                path.moveTo(data[i], data[i + 1])
                i += 2
            }
            2 -> {
                path.lineTo(data[i], data[i + 1])
                i += 2
            }
            3 -> {
                path.quadraticBezierTo(data[i], data[i + 1], data[i + 2], data[i + 3])
                i += 4
            }
            4 -> {
                path.cubicTo(
                    data[i], data[i + 1], data[i + 2],
                    data[i + 3], data[i + 4], data[i + 5],
                )
                i += 6
            }
        }
    }
    return path
}

private fun backChevronPath() = pathFromCommands(floatArrayOf(
    0f, 1f, 0.07666f, 0.35254f, 3f, 0.07715f, 0.36963f, 0.0835f, 0.38379f,
    3f, 0.08984f, 0.39795f, 0.10303f, 0.41113f, 2f, 0.59229f, 0.88477f,
    3f, 0.61328f, 0.90576f, 0.64404f, 0.90576f, 3f, 0.66455f, 0.90576f,
    0.68115f, 0.896f, 3f, 0.69824f, 0.88623f, 0.70801f, 0.86963f,
    3f, 0.71826f, 0.85303f, 0.71826f, 0.83252f, 3f, 0.71826f, 0.80225f,
    0.69531f, 0.7793f, 2f, 0.25293f, 0.35303f, 2f, 0.69531f, -0.07373f,
    3f, 0.71826f, -0.09717f, 0.71826f, -0.12695f, 3f, 0.71826f, -0.14795f,
    0.70801f, -0.16455f, 3f, 0.69824f, -0.18115f, 0.68115f, -0.19092f,
    3f, 0.66455f, -0.20117f, 0.64404f, -0.20117f, 3f, 0.61328f, -0.20117f,
    0.59229f, -0.17969f, 2f, 0.10303f, 0.29395f, 3f, 0.08936f, 0.30713f,
    0.08301f, 0.32129f, 3f, 0.07666f, 0.33594f, 0.07666f, 0.35254f, 0f,
))

private fun pickerPreviousChevronPath() = pathFromCommands(floatArrayOf(
    0f, 1f, 0.06055f, 0.35254f, 3f, 0.06055f, 0.36523f, 0.06494f, 0.37598f,
    3f, 0.06982f, 0.38672f, 0.07959f, 0.39648f, 2f, 0.45996f, 0.76855f,
    3f, 0.47656f, 0.78467f, 0.5f, 0.78467f, 3f, 0.51611f, 0.78467f,
    0.52881f, 0.77686f, 3f, 0.54199f, 0.76953f, 0.5498f, 0.75684f,
    3f, 0.55762f, 0.74414f, 0.55762f, 0.72803f, 3f, 0.55762f, 0.70508f,
    0.53955f, 0.68701f, 2f, 0.19678f, 0.35254f, 2f, 0.53955f, 0.01807f,
    3f, 0.55762f, 0f, 0.55762f, -0.02344f, 3f, 0.55762f, -0.03906f,
    0.5498f, -0.05176f, 3f, 0.54199f, -0.06494f, 0.52881f, -0.07227f,
    3f, 0.51611f, -0.08008f, 0.5f, -0.08008f, 3f, 0.47656f, -0.08008f,
    0.45996f, -0.06348f, 2f, 0.07959f, 0.30859f, 3f, 0.06982f, 0.31836f,
    0.06494f, 0.3291f, 3f, 0.06055f, 0.33984f, 0.06055f, 0.35254f, 0f,
))

private fun findPreviousChevronPath() = pathFromCommands(floatArrayOf(
    0f, 1f, 0.1377f, 0.20947f, 2f, 0.51563f, 0.59619f, 3f, 0.5293f,
    0.61084f, 0.54883f, 0.61084f, 3f, 0.55811f, 0.61084f, 0.56641f, 0.60693f,
    3f, 0.5752f, 0.60303f, 0.58154f, 0.59619f, 2f, 0.95947f, 0.20947f,
    3f, 0.97266f, 0.19678f, 0.97266f, 0.17773f, 3f, 0.97266f, 0.16504f,
    0.9668f, 0.15479f, 3f, 0.96094f, 0.14502f, 0.95068f, 0.13916f,
    3f, 0.94092f, 0.1333f, 0.92822f, 0.1333f, 3f, 0.90869f, 0.1333f,
    0.89648f, 0.146f, 2f, 0.52246f, 0.52783f, 2f, 0.57471f, 0.52783f,
    2f, 0.20068f, 0.146f, 3f, 0.18896f, 0.1333f, 0.16895f, 0.1333f,
    3f, 0.15625f, 0.1333f, 0.146f, 0.13916f, 3f, 0.13623f, 0.14502f,
    0.13037f, 0.15479f, 3f, 0.12451f, 0.16504f, 0.12451f, 0.17773f,
    3f, 0.12451f, 0.18701f, 0.12793f, 0.19531f, 3f, 0.13135f, 0.20361f,
    0.1377f, 0.20947f, 0f,
))

private fun findNextChevronPath() = pathFromCommands(floatArrayOf(
    0f, 1f, 0.54883f, 0.09229f, 3f, 0.53027f, 0.09229f, 0.51563f, 0.10645f,
    2f, 0.1377f, 0.49316f, 3f, 0.13135f, 0.49951f, 0.12793f, 0.50732f,
    3f, 0.12451f, 0.51563f, 0.12451f, 0.5249f, 3f, 0.12451f, 0.5376f,
    0.13037f, 0.54785f, 3f, 0.13623f, 0.55811f, 0.146f, 0.56396f,
    3f, 0.15625f, 0.56982f, 0.16895f, 0.56982f, 3f, 0.1875f, 0.56982f,
    0.20068f, 0.55713f, 2f, 0.57471f, 0.1748f, 2f, 0.52246f, 0.1748f,
    2f, 0.89648f, 0.55713f, 3f, 0.91016f, 0.56982f, 0.92822f, 0.56982f,
    3f, 0.94092f, 0.56982f, 0.95068f, 0.56396f, 3f, 0.96094f, 0.55811f,
    0.9668f, 0.54785f, 3f, 0.97266f, 0.5376f, 0.97266f, 0.5249f,
    3f, 0.97266f, 0.50684f, 0.95947f, 0.49365f, 2f, 0.58154f, 0.10645f,
    3f, 0.5752f, 0.09961f, 0.56641f, 0.0957f, 3f, 0.55811f, 0.09229f,
    0.54883f, 0.09229f, 0f,
))

private fun popupMenuIndicatorPath() = pathFromCommands(floatArrayOf(
    0f, 1f, 0.35303f, 0.72461f, 3f, 0.36084f, 0.72461f, 0.36816f, 0.72119f,
    3f, 0.37598f, 0.71826f, 0.38232f, 0.71191f, 2f, 0.59668f, 0.50684f,
    3f, 0.60156f, 0.50146f, 0.60449f, 0.49463f, 3f, 0.60791f, 0.48779f,
    0.60791f, 0.47803f, 3f, 0.60791f, 0.46094f, 0.59619f, 0.44922f,
    3f, 0.58496f, 0.4375f, 0.56787f, 0.4375f, 3f, 0.56055f, 0.4375f,
    0.55322f, 0.43994f, 3f, 0.5459f, 0.44287f, 0.53857f, 0.44922f,
    2f, 0.35303f, 0.63135f, 2f, 0.16699f, 0.44922f, 3f, 0.16016f, 0.44238f,
    0.15234f, 0.43994f, 3f, 0.14502f, 0.4375f, 0.13818f, 0.4375f,
    3f, 0.12061f, 0.4375f, 0.10889f, 0.44922f, 3f, 0.09766f, 0.46094f,
    0.09766f, 0.47803f, 3f, 0.09766f, 0.48779f, 0.10059f, 0.49463f,
    3f, 0.104f, 0.50146f, 0.10889f, 0.50684f, 2f, 0.32373f, 0.71191f,
    3f, 0.33008f, 0.71826f, 0.3374f, 0.72119f, 3f, 0.34473f, 0.72461f,
    0.35303f, 0.72461f, 0f,
    1f, 0.35303f, -0.01953f, 3f, 0.34473f, -0.01953f, 0.3374f, -0.01611f,
    3f, 0.33008f, -0.01318f, 0.32373f, -0.00684f, 2f, 0.10889f, 0.19824f,
    3f, 0.104f, 0.20361f, 0.10059f, 0.21045f, 3f, 0.09766f, 0.21729f,
    0.09766f, 0.22705f, 3f, 0.09766f, 0.24414f, 0.10889f, 0.25586f,
    3f, 0.12061f, 0.26758f, 0.13818f, 0.26758f, 3f, 0.14502f, 0.26758f,
    0.15234f, 0.26514f, 3f, 0.16016f, 0.2627f, 0.16699f, 0.25586f,
    2f, 0.35303f, 0.07373f, 2f, 0.53857f, 0.25586f, 3f, 0.5459f, 0.26221f,
    0.55322f, 0.26465f, 3f, 0.56055f, 0.26758f, 0.56787f, 0.26758f,
    3f, 0.58496f, 0.26758f, 0.59619f, 0.25586f, 3f, 0.60791f, 0.24414f,
    0.60791f, 0.22705f, 3f, 0.60791f, 0.21729f, 0.60449f, 0.21045f,
    3f, 0.60156f, 0.20361f, 0.59668f, 0.19824f, 2f, 0.38232f, -0.00684f,
    3f, 0.37598f, -0.01318f, 0.36816f, -0.01611f, 3f, 0.36084f, -0.01953f,
    0.35303f, -0.01953f, 0f,
))

private fun dragHandlePath() = pathFromCommands(floatArrayOf(
    0f, 1f, 0.16113f, 0.10156f, 3f, 0.146f, 0.10156f, 0.13525f, 0.1123f,
    3f, 0.12451f, 0.12354f, 0.12451f, 0.13916f, 3f, 0.12451f, 0.1543f,
    0.13525f, 0.16504f, 3f, 0.146f, 0.17578f, 0.16113f, 0.17578f,
    2f, 1.0752f, 0.17578f, 3f, 1.09033f, 0.17578f, 1.10107f, 0.16504f,
    3f, 1.1123f, 0.1543f, 1.1123f, 0.13916f, 3f, 1.1123f, 0.12354f,
    1.10107f, 0.1123f, 3f, 1.09033f, 0.10156f, 1.0752f, 0.10156f,
    2f, 0.16113f, 0.10156f, 0f,
    1f, 0.16113f, 0.31494f, 3f, 0.146f, 0.31494f, 0.13525f, 0.32568f,
    3f, 0.12451f, 0.33643f, 0.12451f, 0.35156f, 3f, 0.12451f, 0.36719f,
    0.13525f, 0.37793f, 3f, 0.146f, 0.38916f, 0.16113f, 0.38916f,
    2f, 1.0752f, 0.38916f, 3f, 1.09033f, 0.38916f, 1.10107f, 0.37793f,
    3f, 1.1123f, 0.36719f, 1.1123f, 0.35156f, 3f, 1.1123f, 0.33643f,
    1.10107f, 0.32568f, 3f, 1.09033f, 0.31494f, 1.0752f, 0.31494f,
    2f, 0.16113f, 0.31494f, 0f,
    1f, 0.16113f, 0.52783f, 3f, 0.146f, 0.52783f, 0.13525f, 0.53857f,
    3f, 0.12451f, 0.54932f, 0.12451f, 0.56445f, 3f, 0.12451f, 0.58008f,
    0.13525f, 0.59082f, 3f, 0.146f, 0.60156f, 0.16113f, 0.60156f,
    2f, 1.0752f, 0.60156f, 3f, 1.09033f, 0.60156f, 1.10107f, 0.59082f,
    3f, 1.1123f, 0.58008f, 1.1123f, 0.56445f, 3f, 1.1123f, 0.54932f,
    1.10107f, 0.53857f, 3f, 1.09033f, 0.52783f, 1.0752f, 0.52783f,
    2f, 0.16113f, 0.52783f, 0f,
))

private fun listChevronPath() = Path().apply {
    moveTo(0.55811f, 0.35254f)
    quadraticBezierTo(0.55811f, 0.33984f, 0.55322f, 0.32910f)
    quadraticBezierTo(0.54834f, 0.31836f, 0.53809f, 0.30811f)
    lineTo(0.24561f, 0.01855f)
    quadraticBezierTo(0.22949f, 0.00244f, 0.20654f, 0.00244f)
    quadraticBezierTo(0.18262f, 0.00244f, 0.16602f, 0.01855f)
    quadraticBezierTo(0.14941f, 0.03516f, 0.14941f, 0.05859f)
    quadraticBezierTo(0.14941f, 0.08203f, 0.16748f, 0.10010f)
    lineTo(0.42383f, 0.35254f)
    lineTo(0.16748f, 0.60498f)
    quadraticBezierTo(0.14941f, 0.62305f, 0.14941f, 0.64648f)
    quadraticBezierTo(0.14941f, 0.66992f, 0.16602f, 0.68604f)
    quadraticBezierTo(0.18262f, 0.70264f, 0.20654f, 0.70264f)
    quadraticBezierTo(0.22949f, 0.70264f, 0.24561f, 0.68652f)
    lineTo(0.53809f, 0.39697f)
    quadraticBezierTo(0.55811f, 0.37793f, 0.55811f, 0.35254f)
    close()
}

private fun submenuChevronPath() = Path().apply {
    moveTo(0.57471f, 0.35254f)
    quadraticBezierTo(0.57471f, 0.33740f, 0.56885f, 0.32422f)
    quadraticBezierTo(0.56348f, 0.31152f, 0.55078f, 0.29883f)
    lineTo(0.26318f, 0.01563f)
    quadraticBezierTo(0.24463f, -0.00293f, 0.21777f, -0.00293f)
    quadraticBezierTo(0.19043f, -0.00293f, 0.17090f, 0.01611f)
    quadraticBezierTo(0.15137f, 0.03516f, 0.15137f, 0.06250f)
    quadraticBezierTo(0.15137f, 0.08984f, 0.17285f, 0.11084f)
    lineTo(0.41992f, 0.35303f)
    lineTo(0.17285f, 0.59424f)
    quadraticBezierTo(0.15137f, 0.61523f, 0.15137f, 0.64258f)
    quadraticBezierTo(0.15137f, 0.66992f, 0.17090f, 0.68896f)
    quadraticBezierTo(0.19043f, 0.70801f, 0.21777f, 0.70801f)
    quadraticBezierTo(0.24463f, 0.70801f, 0.26318f, 0.68945f)
    lineTo(0.55078f, 0.40625f)
    quadraticBezierTo(0.57471f, 0.38281f, 0.57471f, 0.35254f)
    close()
}

private fun glassChevronPath() = Path().apply {
    moveTo(0.70166f, 0.35254f)
    quadraticBezierTo(0.70166f, 0.33789f, 0.69580f, 0.32471f)
    quadraticBezierTo(0.69043f, 0.31201f, 0.67822f, 0.30029f)
    lineTo(0.30273f, -0.06738f)
    quadraticBezierTo(0.28369f, -0.08594f, 0.25635f, -0.08594f)
    quadraticBezierTo(0.23877f, -0.08594f, 0.22363f, -0.07715f)
    quadraticBezierTo(0.20850f, -0.06836f, 0.19922f, -0.05371f)
    quadraticBezierTo(0.19043f, -0.03906f, 0.19043f, -0.02051f)
    quadraticBezierTo(0.19043f, 0.00635f, 0.21094f, 0.02734f)
    lineTo(0.54541f, 0.35254f)
    lineTo(0.21094f, 0.67773f)
    quadraticBezierTo(0.19043f, 0.69824f, 0.19043f, 0.72559f)
    quadraticBezierTo(0.19043f, 0.74365f, 0.19922f, 0.75830f)
    quadraticBezierTo(0.20850f, 0.77344f, 0.22363f, 0.78223f)
    quadraticBezierTo(0.23877f, 0.79102f, 0.25635f, 0.79102f)
    quadraticBezierTo(0.28369f, 0.79102f, 0.30273f, 0.77246f)
    lineTo(0.67822f, 0.40479f)
    quadraticBezierTo(0.68994f, 0.39307f, 0.69580f, 0.38037f)
    quadraticBezierTo(0.70166f, 0.36768f, 0.70166f, 0.35254f)
    close()
}

private fun pickerNextChevronPath() = Path().apply {
    moveTo(0.68604f, 0.35254f)
    quadraticBezierTo(0.68604f, 0.33984f, 0.68115f, 0.32910f)
    quadraticBezierTo(0.67627f, 0.31836f, 0.66650f, 0.30859f)
    lineTo(0.28613f, -0.06348f)
    quadraticBezierTo(0.27002f, -0.08008f, 0.24609f, -0.08008f)
    quadraticBezierTo(0.23047f, -0.08008f, 0.21729f, -0.07227f)
    quadraticBezierTo(0.20410f, -0.06494f, 0.19629f, -0.05176f)
    quadraticBezierTo(0.18896f, -0.03906f, 0.18896f, -0.02344f)
    quadraticBezierTo(0.18896f, 0f, 0.20654f, 0.01807f)
    lineTo(0.54932f, 0.35254f)
    lineTo(0.20654f, 0.68701f)
    quadraticBezierTo(0.18896f, 0.70508f, 0.18896f, 0.72803f)
    quadraticBezierTo(0.18896f, 0.74414f, 0.19629f, 0.75684f)
    quadraticBezierTo(0.20410f, 0.76953f, 0.21729f, 0.77686f)
    quadraticBezierTo(0.23047f, 0.78467f, 0.24609f, 0.78467f)
    quadraticBezierTo(0.27002f, 0.78467f, 0.28613f, 0.76855f)
    lineTo(0.66650f, 0.39648f)
    quadraticBezierTo(0.67627f, 0.38672f, 0.68115f, 0.37598f)
    quadraticBezierTo(0.68604f, 0.36523f, 0.68604f, 0.35254f)
    close()
}

private fun secondaryChevronPath() = Path().apply {
    moveTo(0.66455f, 0.35254f)
    quadraticBezierTo(0.66455f, 0.34326f, 0.66064f, 0.33496f)
    quadraticBezierTo(0.65723f, 0.32715f, 0.65039f, 0.32031f)
    lineTo(0.26367f, -0.05811f)
    quadraticBezierTo(0.25049f, -0.07129f, 0.23145f, -0.07129f)
    quadraticBezierTo(0.21924f, -0.07129f, 0.20898f, -0.06543f)
    quadraticBezierTo(0.19873f, -0.05957f, 0.19287f, -0.04932f)
    quadraticBezierTo(0.18701f, -0.03955f, 0.18701f, -0.02686f)
    quadraticBezierTo(0.18701f, -0.00879f, 0.19971f, 0.00488f)
    lineTo(0.55518f, 0.35254f)
    lineTo(0.19971f, 0.70020f)
    quadraticBezierTo(0.18701f, 0.71387f, 0.18701f, 0.73193f)
    quadraticBezierTo(0.18701f, 0.74463f, 0.19287f, 0.75439f)
    quadraticBezierTo(0.19873f, 0.76465f, 0.20898f, 0.77051f)
    quadraticBezierTo(0.21924f, 0.77637f, 0.23145f, 0.77637f)
    quadraticBezierTo(0.25049f, 0.77637f, 0.26367f, 0.76367f)
    lineTo(0.65039f, 0.38477f)
    quadraticBezierTo(0.65723f, 0.37793f, 0.66064f, 0.36963f)
    quadraticBezierTo(0.66455f, 0.36182f, 0.66455f, 0.35254f)
    close()
}
