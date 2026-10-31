package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseFacet
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryEraBar
import com.example.unifiedreader.core.model.LibraryPlaceGroup
import com.example.unifiedreader.core.model.libraryEraPeriods
import com.example.unifiedreader.feature.library.LibraryGridHeading
import com.example.unifiedreader.feature.library.LibraryGridHeadingText
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import kotlin.math.sqrt
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Browse with nothing picked: each shown row under its name and how many books have it. Set in
 * groups its places by region, When is a timeline with its periods under it, the rest are chips.
 */
@Composable
internal fun LibraryBrowseLanding(
    view: LibraryBrowseView,
    rows: List<LibraryBrowseRow>,
    contentTopInset: Dp,
    onPick: (LibraryBrowseChip) -> Unit,
    onScrolledChanged: (Boolean) -> Unit,
    prompts: LibraryBrowseLandingPrompts = LibraryBrowseLandingPrompts(),
) {
    val listState = rememberLazyListState()
    val latestOnScrolledChanged by rememberUpdatedState(onScrolledChanged)
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollBackward }
            .distinctUntilChanged()
            .collect { scrolled -> latestOnScrolledChanged(scrolled) }
    }
    val gutter = appGutter()
    val shownRows = rows.filter { row -> view.result.facets[row].orEmpty().isNotEmpty() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = contentTopInset + AppLayout.contentTop,
            bottom = AppLayout.contentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
    ) {
        val checkGuesses = view.tagPrompt as? LibraryBrowseTagPrompt.CheckGuesses
        val onCheckGuesses = prompts.onCheckGuesses
        if (checkGuesses != null && onCheckGuesses != null) {
            item(key = "check-guesses") {
                LibraryBrowseCheckGuessesPanel(prompt = checkGuesses, onCheck = onCheckGuesses, onNotNow = prompts.onNotNow)
            }
        }
        shownRows.forEach { row ->
            val facets = view.result.facets[row].orEmpty()
            item(key = row.name) {
                Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                    LibraryGridHeadingText(
                        heading = LibraryGridHeading(title = row.label, count = bookCountLabel(view.coverage[row] ?: 0)),
                    )
                    val newTags = view.tagPrompt as? LibraryBrowseTagPrompt.NewTags
                    if (row == LibraryBrowseRow.ABOUT && newTags != null) {
                        LibraryBrowseNewTagsLine(prompt = newTags, onSort = prompts.onSortNewTags)
                    }
                    when (row) {
                        LibraryBrowseRow.SET_IN -> LibraryBrowsePlaces(groups = view.places, onPick = onPick, onSortTag = prompts.onSortTag)
                        LibraryBrowseRow.WHEN -> LibraryBrowseTimeline(
                            bars = view.timeline,
                            periods = libraryEraPeriods(facets),
                            onPick = onPick,
                            onSortTag = prompts.onSortTag,
                        )
                        else -> LibraryBrowseChipFlow(
                            row = row,
                            facets = facets,
                            picked = emptyList(),
                            onToggle = onPick,
                            foldedCount = LandingFoldedChipCount,
                            moreLabel = { "All ${facets.size}" },
                            onSortTag = prompts.onSortTag,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Set in's regions as panels in columns, three on Expanded windows, two on Medium, one on phones.
 * Each column stacks its panels at their own height (the regions go across, then down), so a long
 * region never leaves a gap beside a short one.
 */
@Composable
private fun LibraryBrowsePlaces(
    groups: List<LibraryPlaceGroup>,
    onPick: (LibraryBrowseChip) -> Unit,
    onSortTag: ((LibraryBrowseChip) -> Unit)?,
) {
    val columns = when (currentAppWindowWidth()) {
        AppWindowWidth.Compact -> 1
        AppWindowWidth.Medium -> 2
        AppWindowWidth.Expanded -> 3
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
        repeat(columns) { column ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
            ) {
                groups.filterIndexed { index, _ -> index % columns == column }.forEach { group ->
                    LibraryBrowsePlacePanel(group = group, onPick = onPick, onSortTag = onSortTag)
                }
            }
        }
    }
}

@Composable
private fun LibraryBrowsePlacePanel(
    group: LibraryPlaceGroup,
    onPick: (LibraryBrowseChip) -> Unit,
    onSortTag: ((LibraryBrowseChip) -> Unit)?,
) {
    AppPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppLayout.inset),
            verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap), verticalAlignment = Alignment.CenterVertically) {
                AppGroupLabel(text = group.region.label)
                Text(
                    text = appCount(group.count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LibraryBrowseChipFlow(
                row = LibraryBrowseRow.SET_IN,
                facets = group.places,
                picked = emptyList(),
                onToggle = onPick,
                foldedCount = RegionFoldedChipCount,
                stateKey = group.region.name,
                onSortTag = onSortTag,
            )
        }
    }
}

/**
 * When as a timeline: one bar per decade or century in time order, as tall as the square root of
 * its count (a few old books still show beside many recent ones), the count above and the label
 * under it; a bar picks its stretch of time. Narrow windows scroll it sideways. The periods that
 * aren't a bar ("World War II", "20th century") follow as chips.
 */
@Composable
private fun LibraryBrowseTimeline(
    bars: List<LibraryEraBar>,
    periods: List<LibraryBrowseFacet>,
    onPick: (LibraryBrowseChip) -> Unit,
    onSortTag: ((LibraryBrowseChip) -> Unit)?,
) {
    AppPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppLayout.inset),
            verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
        ) {
            if (bars.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val gaps = TimelineGap * (bars.size - 1)
                    val slotWidth = max(TimelineMinSlotWidth, (maxWidth - gaps) / bars.size)
                    val tallest = sqrt(bars.maxOf { it.count }.toFloat())
                    val scroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .timelineEdgeFade(scroll)
                            .horizontalScroll(scroll),
                        horizontalArrangement = Arrangement.spacedBy(TimelineGap),
                    ) {
                        bars.forEach { bar ->
                            LibraryBrowseTimelineBar(
                                bar = bar,
                                height = TimelineBarHeight * (sqrt(bar.count.toFloat()) / tallest),
                                width = slotWidth,
                                onClick = { onPick(LibraryBrowseChip(LibraryBrowseRow.WHEN, bar.slot.label)) },
                            )
                        }
                    }
                }
            }
            if (periods.isNotEmpty()) {
                LibraryBrowseChipFlow(
                    row = LibraryBrowseRow.WHEN,
                    facets = periods,
                    picked = emptyList(),
                    onToggle = onPick,
                    foldedCount = PeriodFoldedChipCount,
                    stateKey = "periods",
                    onSortTag = onSortTag,
                )
            }
        }
    }
}

/** Fades the timeline out at an edge that has more to scroll to. */
private fun Modifier.timelineEdgeFade(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = TimelineEdgeFadeWidth.toPx()
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

@Composable
private fun LibraryBrowseTimelineBar(
    bar: LibraryEraBar,
    height: Dp,
    width: Dp,
    onClick: () -> Unit,
) {
    val label = bar.slot.label
    Column(
        modifier = Modifier
            .width(width)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label, ${bookCountLabel(bar.count)}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TimelineLabelGap),
    ) {
        Text(
            text = appCount(bar.count),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TimelineBarHeight),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(max(TimelineMinBarHeight, height))
                    .clip(MaterialTheme.shapes.extraSmall.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, hyphens = Hyphens.Auto),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
        )
    }
}

private val TimelineBarHeight = 96.dp
private val TimelineMinBarHeight = 4.dp
// Wide enough for "Prehis-tory" in two lines. 21 bars fit a landscape tablet; narrower windows
// scroll them, the edge fading while there is more.
private val TimelineMinSlotWidth = 40.dp
private val TimelineGap = 4.dp
private val TimelineEdgeFadeWidth = 32.dp
private val TimelineLabelGap = 4.dp
private const val LandingFoldedChipCount = 20
private const val RegionFoldedChipCount = 6
private const val PeriodFoldedChipCount = 10

/** What the landing page can do about unsorted tags: open Settings › Tags, hide the panel, sort a held chip. */
internal class LibraryBrowseLandingPrompts(
    val onCheckGuesses: (() -> Unit)? = null,
    val onNotNow: () -> Unit = {},
    val onSortNewTags: (() -> Unit)? = null,
    val onSortTag: ((LibraryBrowseChip) -> Unit)? = null,
)
