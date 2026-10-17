package dev.liquidglass.ios27

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Insets belong to the host (WindowInsets.safeDrawing), never copied iPhone constants. */
@Composable
fun Ios27PageContent(
    modifier: Modifier = Modifier,
    horizontalContentPadding: Dp = Ios27Tokens.ContentMargin.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = Ios27Tokens.ReadableWidth.dp).fillMaxWidth()
                .padding(horizontal = horizontalContentPadding),
            verticalArrangement = Arrangement.spacedBy(Ios27Tokens.GroupGap.dp),
            content = content,
        )
    }
}

@Composable
fun Ios27PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = LocalIos27PageColors.current
    if (compact) {
        Box(modifier.fillMaxWidth().heightIn(min = Ios27Tokens.CompactHeaderHeight.dp).padding(bottom = 10.dp),
            contentAlignment = Alignment.Center) {
            Text(title, style = Ios27Tokens.Type.Headline, color = colors.label,
                modifier = Modifier.semantics { heading() })
        }
        return
    }
    Column(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Ios27Tokens.Type.LargeTitle.copy(fontWeight = FontWeight.Bold),
            color = colors.label, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = Ios27Tokens.Type.Subheadline, color = colors.secondaryLabel)
    }
}

enum class Ios27SectionEmphasis { Nested, Prominent, ExtraProminent }

private val LocalIos27RowPressShape = staticCompositionLocalOf {
    RoundedCornerShape(ZeroCornerSize)
}

/**
 * Reusable row host. Callers provide only row content; the reference inset and
 * every-between-rows separator are applied here, so the first row can never
 * accidentally receive a top divider.
 */
@Composable
fun <T> Ios27ListRows(
    items: List<T>,
    modifier: Modifier = Modifier,
    showSeparator: (index: Int) -> Boolean = { true },
    roundedPressMask: Boolean = true,
    row: @Composable (T) -> Unit,
) {
    val radius = Ios27Tokens.GroupRadius.dp
    Column(modifier.fillMaxWidth().semantics { collectionInfo = CollectionInfo(items.size, 1) }) {
        items.forEachIndexed { index, item ->
            val pressShape = when {
                !roundedPressMask -> RoundedCornerShape(ZeroCornerSize)
                items.size == 1 -> RoundedCornerShape(radius)
                index == 0 -> RoundedCornerShape(topStart = radius, topEnd = radius)
                index == items.lastIndex -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
                else -> RoundedCornerShape(ZeroCornerSize)
            }
            val topSeparator = index > 0 && showSeparator(index)
            val bottomSeparator = index < items.lastIndex && showSeparator(index + 1)
            val maskEdges = listPressMaskEdges(Ios27Tokens.SeparatorRenderHeight.dp, topSeparator, bottomSeparator)
            CompositionLocalProvider(
                LocalIos27RowPressShape provides pressShape,
                LocalIos27RowPressMaskEdges provides maskEdges,
            ) {
                Box(
                    Modifier.fillMaxWidth().semantics {
                        collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                    }
                        .then(if (index == 0 || !showSeparator(index)) Modifier else Modifier.ios27RowTopSeparator()),
                ) {
                    row(item)
                }
            }
        }
    }
}

/** One opaque rounded group. Interior rows are joined, not individually pill-shaped. */
@Composable
fun Ios27ListSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    emphasis: Ios27SectionEmphasis = Ios27SectionEmphasis.Nested,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalIos27PageColors.current
    val headerHeight = when (emphasis) {
        Ios27SectionEmphasis.Nested -> Ios27Tokens.SectionHeaderHeight
        Ios27SectionEmphasis.Prominent -> Ios27Tokens.ProminentHeaderHeight
        Ios27SectionEmphasis.ExtraProminent -> Ios27Tokens.ExtraProminentHeaderHeight
    }
    // Android style mapping; heights come from the kit's three header variants.
    val headerStyle = when (emphasis) {
        Ios27SectionEmphasis.Nested -> Ios27Tokens.Type.Footnote
        Ios27SectionEmphasis.Prominent -> Ios27Tokens.Type.Headline
        Ios27SectionEmphasis.ExtraProminent -> Ios27Tokens.Type.Title3
    }.copy(fontWeight = LocalIos27ListTypography.current.sectionWeight)
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Box(Modifier.fillMaxWidth().heightIn(min = headerHeight.dp)
                .padding(horizontal = Ios27Tokens.RowPadding.dp, vertical = Ios27Tokens.SectionHeaderPadding.dp),
                contentAlignment = Alignment.CenterStart) {
                Text(title, style = headerStyle,
                    color = if (emphasis == Ios27SectionEmphasis.Nested) colors.secondaryLabel else colors.label,
                    modifier = Modifier.semantics { heading() })
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Ios27Tokens.GroupRadius.dp))
            .background(colors.group), content = content)
        if (footer != null) Text(footer, color = colors.secondaryLabel, style = Ios27Tokens.Type.Footnote,
            modifier = Modifier.fillMaxWidth().heightIn(min = Ios27Tokens.SectionFooterHeight.dp)
                .padding(start = Ios27Tokens.RowPadding.dp, end = Ios27Tokens.RowPadding.dp, top = 8.dp, bottom = 6.dp))
    }
}

@Composable
fun Ios27ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    disclosure: Boolean = onClick != null,
) {
    val colors = LocalIos27PageColors.current
    val pressShape = LocalIos27RowPressShape.current
    val clickModifier = if (onClick != null) {
        Modifier.ios27ListPressedClickable(
            shape = pressShape,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    } else Modifier
    // Minimums, not fixed heights: multiline Chinese and Android fontScale may grow the row.
    Row(modifier.fillMaxWidth()
        .heightIn(min = (if (subtitle == null) Ios27Tokens.RowHeight else Ios27Tokens.TallRowHeight).dp)
        .then(clickModifier)
        .padding(horizontal = Ios27Tokens.RowPadding.dp, vertical = Ios27Tokens.RowVerticalPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Ios27Tokens.LeadingGap.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = if (enabled) colors.label else colors.secondaryLabel, style = LocalIos27ListTypography.current.title)
            if (subtitle != null) Text(subtitle, color = colors.secondaryLabel, style = Ios27Tokens.Type.Subheadline)
        }
        if (detail != null || trailing != null || disclosure) Spacer(Modifier.width(Ios27Tokens.TrailingGap.dp))
        if (detail != null) {
            Text(detail, modifier = Modifier.widthIn(max = 120.dp), color = colors.secondaryLabel,
                style = Ios27Tokens.Type.Body, textAlign = TextAlign.End)
            if (trailing != null || disclosure) Spacer(Modifier.width(8.dp))
        }
        trailing?.invoke()
        if (disclosure) Ios27DisclosureIndicator()
    }
}

@Composable
fun Ios27ListDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = Ios27Tokens.SeparatorInset.dp)) {
        Spacer(Modifier.fillMaxWidth().height(Ios27Tokens.SeparatorRenderHeight.dp)
            .background(LocalIos27PageColors.current.separator))
    }
}

/** Top separator inside the row's existing bounds, preserving its 52dp height. */
@Composable
fun Modifier.ios27RowTopSeparator(): Modifier {
    val color = LocalIos27PageColors.current.separator
    return drawWithContent {
        drawContent()
        val inset = Ios27Tokens.SeparatorInset.dp.toPx()
        drawRect(color, topLeft = Offset(inset, 0f),
            size = Size((size.width - 2 * inset).coerceAtLeast(0f), Ios27Tokens.SeparatorRenderHeight.dp.toPx()))
    }
}

@Composable
fun Ios27IconTile(
    color: Color,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    // Reference list-row rounded-image slot, not a glass button.
    Box(modifier.size((if (tall) Ios27Tokens.TallIconSize else Ios27Tokens.IconSize).dp)
        .clip(RoundedCornerShape((if (tall) Ios27Tokens.TallIconRadius else Ios27Tokens.IconRadius).dp)).background(color),
        contentAlignment = Alignment.Center, content = content)
}
