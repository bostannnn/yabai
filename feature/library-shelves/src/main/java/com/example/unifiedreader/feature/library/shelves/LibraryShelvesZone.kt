package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryCoverSize
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LocalLibraryCoverGeneration
import com.example.unifiedreader.feature.library.LibraryReadingCard
import com.example.unifiedreader.feature.library.libraryGridColumns
import com.example.unifiedreader.feature.library.readingCardBesideWidth

/**
 * The library home's top: the reading card for the book read last, and the shelves as tabs over
 * one strip of covers. With six grid columns or more the two sit side by side, the card over two
 * columns and the strip's covers Small; otherwise the small card spans the width above the tabs.
 */
@Composable
fun LibraryShelvesZone(
    browseContextKey: String = "library-shelves",
    state: LibraryShelvesState,
    gridMetrics: LibraryGridMetrics = LibraryGridMetrics(),
    itemActionContext: LibraryItemActionContext = LibraryItemActionContext(),
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onBrowseShelf: (LibraryShelfBrowserKind) -> Unit = {},
    // How long the reading card's book has left at the reader's pace; null when unknown.
    readingTimeLeftMillis: (ReadableItem, ItemProgressSummary) -> Long? = { _, _ -> null },
    modifier: Modifier = Modifier,
) {
    if (!state.hasContent) return

    val reading = state.continueReading.firstOrNull()
    // A reading order under way can take the card: its next book instead of the one read last.
    val orderCard = itemActionContext.readingOrderCard
    val cardItemId = orderCard?.item?.id ?: reading?.item?.id
    val timeLeftMillis = remember(reading, orderCard, readingTimeLeftMillis) {
        orderCard?.let { card -> card.progress?.let { progress -> readingTimeLeftMillis(card.item, progress) } }
            ?: reading?.let { shelfItem -> readingTimeLeftMillis(shelfItem.item, shelfItem.progress) }
    }
    val tabs = remember(state, cardItemId) { libraryShelfTabCounts(state, readingCardItemId = cardItemId) }
    var selectedTabName by rememberSaveable(browseContextKey) { mutableStateOf(LibraryShelfTab.InProgress.name) }
    val selectedTab = tabs.firstOrNull { (tab, _) -> tab.name == selectedTabName }?.first ?: tabs.firstOrNull()?.first
    // Keys carry the cover version, so they are rebuilt when covers are reset or refreshed.
    val coverGeneration = LocalLibraryCoverGeneration.current
    val previewCoverTargets = remember(state, gridMetrics, coverPrefetchKeyProvider, coverGeneration) {
        shelfPreviewCoverPrefetchTargets(
            sections = listOf(
                state.continueReading.map(ContinueReadingShelfItem::item),
                state.nextInSeries.map(NextInSeriesShelfItem::item),
                state.recentlyFinished.map(RecentlyFinishedShelfItem::item),
            ),
            itemLimitPerSection = ShelfPreviewItemLimit,
            variant = gridMetrics.coverImageVariant,
            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
        )
    }

    LibraryShelvesPreviewCoverPrefetcher(
        browseContextKey = browseContextKey,
        coverLoadingEnabled = true,
        targets = previewCoverTargets,
        variant = gridMetrics.coverImageVariant,
        onCoverVisible = onCoverVisible,
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // The zone spans the grid's full width, so its columns give the grid's cover width.
        val columns = with(LocalDensity.current) { libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics) }
        val cardWidth = columns.readingCardBesideWidth(gridMetrics.spacing)
        val beside = cardItemId != null && selectedTab != null && cardWidth != null
        // Beside the card the strip is Small, whatever the cover size, so the card stays small too.
        val covers = if (beside) {
            ShelfCoverLayout(width = ShelfBesideCardCoverWidth, imageVariant = gridMetrics.coverImageVariant, showSupportingText = false)
        } else {
            ShelfCoverLayout(width = columns.cellWidth, imageVariant = gridMetrics.coverImageVariant)
        }
        val card = @Composable { cardModifier: Modifier ->
            if (orderCard != null) {
                LibraryReadingCard(
                    item = orderCard.item,
                    progress = orderCard.progress,
                    overline = orderCard.overline,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    imageVariant = gridMetrics.coverImageVariant,
                    onContinue = orderCard.onRead,
                    continueLabel = orderCard.readLabel,
                    actionContext = itemActionContext,
                    timeLeftMillis = timeLeftMillis,
                    footnote = orderCard.thenLine,
                    tall = beside,
                    modifier = cardModifier,
                )
            } else reading?.let { shelfItem ->
                LibraryReadingCard(
                    item = shelfItem.item,
                    progress = shelfItem.progress,
                    overline = "Continue reading",
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    imageVariant = gridMetrics.coverImageVariant,
                    onContinue = { onItemSelected(shelfItem.item) },
                    actionContext = itemActionContext,
                    timeLeftMillis = timeLeftMillis,
                    tall = beside,
                    modifier = cardModifier,
                )
            }
        }
        val band = @Composable { bandModifier: Modifier ->
            if (selectedTab != null) {
                LibraryShelfTabsBand(
                    state = state,
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> selectedTabName = tab.name },
                    readingCardItemId = cardItemId,
                    itemActionContext = itemActionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    covers = covers,
                    spacing = gridMetrics.spacing,
                    onItemSelected = onItemSelected,
                    onSeeAll = onBrowseShelf,
                    modifier = bandModifier,
                )
            }
        }
        if (beside && cardWidth != null) {
            CardBesideBand(
                cardWidth = cardWidth,
                spacing = gridMetrics.spacing,
                card = { card(Modifier) },
                band = { band(Modifier) },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(StackedSpacing)) {
                card(Modifier.fillMaxWidth())
                band(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The card at [cardWidth] and the band in the rest of the width, [spacing] apart, the card as tall
 * as the band so their bottoms line up. The band is measured first and the card given its height.
 */
@Composable
internal fun CardBesideBand(
    cardWidth: Dp,
    spacing: Dp,
    card: @Composable () -> Unit,
    band: @Composable () -> Unit,
) {
    Layout(contents = listOf(card, band)) { (cardMeasurables, bandMeasurables), constraints ->
        val cardPx = cardWidth.roundToPx()
        val gapPx = spacing.roundToPx()
        val bandPx = (constraints.maxWidth - cardPx - gapPx).coerceAtLeast(0)
        val bandPlaceables = bandMeasurables.map { it.measure(Constraints(minWidth = bandPx, maxWidth = bandPx)) }
        val height = bandPlaceables.maxOfOrNull { it.height } ?: 0
        val cardPlaceables = cardMeasurables.map { it.measure(Constraints.fixed(cardPx, height)) }
        layout(constraints.maxWidth, height) {
            cardPlaceables.forEach { it.place(0, 0) }
            bandPlaceables.forEach { it.place(cardPx + gapPx, 0) }
        }
    }
}

internal val StackedSpacing = 24.dp

/** The strip beside the reading card: Small covers, so the card beside it is about 236 dp tall. */
internal val ShelfBesideCardCoverWidth: Dp = LibraryCoverSize.Small.cellSizeDp.dp
