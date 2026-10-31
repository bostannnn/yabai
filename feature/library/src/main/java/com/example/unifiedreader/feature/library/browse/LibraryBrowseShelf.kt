package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryEraBar
import com.example.unifiedreader.feature.library.libraryCoverPlaceholderSpec
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.appCount
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first

/**
 * When as a shelf: one pile per century or decade in time order, its books lying flat, as many as
 * the square root of its count allows, each spine coloured like the title cover of a book in the
 * pile. The count is above, the label under the shelf. A pile picks its stretch of time; once one is
 * picked the others fade. Narrow windows scroll the shelf sideways, starting at the picked or the
 * tallest pile.
 */
@Composable
internal fun LibraryBrowseShelf(
    bars: List<LibraryEraBar>,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickedLabels = picked.filter { it.row == LibraryBrowseRow.WHEN }.map { it.value.lowercase() }.toSet()
    val anyPicked = bars.any { bar -> bar.slot.label.lowercase() in pickedLabels }
    val tallest = sqrt(bars.maxOf { it.count }.toFloat())
    AppPanel(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier.padding(start = AppLayout.inset, top = AppLayout.inset, end = AppLayout.inset, bottom = ShelfBottomInset),
        ) {
            val gaps = PileGap * (bars.size - 1)
            val pileWidth = min(PileMaxWidth, max(PileMinWidth, (maxWidth - gaps) / bars.size))
            val shelfWidth = pileWidth * bars.size + gaps
            val scroll = rememberScrollState()
            val focus = bars.indexOfFirst { bar -> bar.slot.label.lowercase() in pickedLabels }
                .takeIf { it >= 0 } ?: bars.indices.maxByOrNull { index -> bars[index].count } ?: 0
            val focusCenter = with(LocalDensity.current) { ((pileWidth + PileGap) * focus + pileWidth / 2).roundToPx() }
            val viewport = with(LocalDensity.current) { maxWidth.roundToPx() }
            LaunchedEffect(bars, focus) {
                snapshotFlow { scroll.maxValue }.first { it > 0 }
                scroll.scrollTo((focusCenter - viewport / 2).coerceIn(0, scroll.maxValue))
            }
            Column(
                modifier = Modifier
                    .shelfEdgeFade(scroll)
                    .horizontalScroll(scroll),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(PileGap), verticalAlignment = Alignment.Bottom) {
                    bars.forEach { bar ->
                        val chip = LibraryBrowseChip(LibraryBrowseRow.WHEN, bar.slot.label)
                        val isPicked = bar.slot.label.lowercase() in pickedLabels
                        LibraryBrowsePile(
                            bar = bar,
                            spines = (PileMaxBooks * sqrt(bar.count.toFloat()) / tallest).roundToInt().coerceAtLeast(1),
                            picked = isPicked,
                            faded = anyPicked && !isPicked,
                            width = pileWidth,
                            onClick = { onToggle(chip) },
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(top = ShelfLineTop, bottom = ShelfLineBottom)
                        .width(shelfWidth)
                        .height(ShelfLineHeight)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PileGap)) {
                    bars.forEach { bar ->
                        val isPicked = bar.slot.label.lowercase() in pickedLabels
                        Text(
                            text = bar.slot.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, hyphens = Hyphens.Auto),
                            fontWeight = if (isPicked) FontWeight.Bold else null,
                            color = if (isPicked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            minLines = 2,
                            modifier = Modifier
                                .width(pileWidth)
                                .clip(RoundedCornerShape(LabelCorner))
                                .background(if (isPicked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .alpha(if (anyPicked && !isPicked) FadedLabelAlpha else 1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryBrowsePile(
    bar: LibraryEraBar,
    spines: Int,
    picked: Boolean,
    faded: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val colors = remember(bar.books, spines) {
        List(spines) { index ->
            bar.books.getOrNull(index % bar.books.size.coerceAtLeast(1))
                ?.let { item -> libraryCoverPlaceholderSpec(item).palette.container }
        }
    }
    val fallback = MaterialTheme.colorScheme.outline
    val label = bar.slot.label
    Column(
        modifier = Modifier
            .width(width)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, ${bookCountLabel(bar.count)}"
                selected = picked
            }
            .alpha(if (faded) FadedPileAlpha else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpineGap),
    ) {
        Text(
            text = appCount(bar.count),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(bottom = CountGap),
        )
        colors.forEachIndexed { index, color ->
            val shape = SpineShapes[(index + bar.slot.order) % SpineShapes.size]
            Box(
                modifier = Modifier
                    .offset(x = shape.shift)
                    .fillMaxWidth(shape.width)
                    .height(SpineHeight)
                    .clip(RoundedCornerShape(SpineCorner))
                    .background(color ?: fallback),
            )
        }
    }
}

/**
 * When in the 320 pane: the shelf turned on its side, a row per pile with its books standing, the
 * picked row selected.
 */
@Composable
internal fun LibraryBrowseShelfList(
    bars: List<LibraryEraBar>,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
) {
    val tallest = sqrt(bars.maxOf { it.count }.toFloat())
    Column {
        bars.forEach { bar ->
            val chip = LibraryBrowseChip(LibraryBrowseRow.WHEN, bar.slot.label)
            val spines = (StandingMaxBooks * sqrt(bar.count.toFloat()) / tallest).roundToInt().coerceAtLeast(1)
            val colors = remember(bar.books, spines) {
                List(spines) { index ->
                    bar.books.getOrNull(index % bar.books.size.coerceAtLeast(1))
                        ?.let { item -> libraryCoverPlaceholderSpec(item).palette.container }
                }
            }
            val fallback = MaterialTheme.colorScheme.outline
            LibraryBrowseFacetRow(
                name = bar.slot.label,
                count = bar.count,
                selected = picked.any { it.sameAs(chip) },
                onClick = { onToggle(chip) },
                middle = {
                    Row(
                        modifier = Modifier.height(StandingHeight),
                        horizontalArrangement = Arrangement.spacedBy(SpineGap),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        colors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .width(StandingWidth)
                                    .height(StandingHeights[(index + bar.slot.order) % StandingHeights.size])
                                    .clip(RoundedCornerShape(SpineCorner))
                                    .background(color ?: fallback),
                            )
                        }
                    }
                },
                nameWidth = EraNameWidth,
                height = EraRowHeight,
            )
        }
    }
}

/** Fades the shelf out at an edge that has more to scroll to. */
private fun Modifier.shelfEdgeFade(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = ShelfEdgeFadeWidth.toPx()
        if (scroll.canScrollBackward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (scroll.canScrollForward) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private class SpineShape(val width: Float, val shift: Dp)

// Books in a pile don't line up: each sits a little wider or narrower and off to one side.
private val SpineShapes = listOf(
    SpineShape(0.86f, (-2).dp),
    SpineShape(0.94f, 1.dp),
    SpineShape(0.80f, 3.dp),
    SpineShape(0.91f, (-1).dp),
    SpineShape(0.98f, 0.dp),
    SpineShape(0.83f, 2.dp),
    SpineShape(0.89f, (-3).dp),
)
private val StandingHeights = listOf(13.dp, 16.dp, 11.dp, 15.dp, 17.dp, 12.dp, 14.dp)
private const val PileMaxBooks = 14
private const val StandingMaxBooks = 26
private const val FadedPileAlpha = 0.3f
private const val FadedLabelAlpha = 0.55f
// Wide enough for "Prehis-tory" in two lines; more piles than fit scroll sideways.
private val PileMinWidth = 40.dp
private val PileMaxWidth = 56.dp
private val PileGap = 4.dp
private val SpineHeight = 7.dp
private val SpineGap = 1.dp
private val SpineCorner = 1.5.dp
private val CountGap = 4.dp
private val ShelfLineHeight = 6.dp
private val ShelfLineTop = 1.dp
private val ShelfLineBottom = 6.dp
private val ShelfBottomInset = 14.dp
private val LabelCorner = 8.dp
private val ShelfEdgeFadeWidth = 32.dp
private val StandingWidth = 4.dp
private val StandingHeight = 18.dp
private val EraNameWidth = 92.dp
private val EraRowHeight = 32.dp
