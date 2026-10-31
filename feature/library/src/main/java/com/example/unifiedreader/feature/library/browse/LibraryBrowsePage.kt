package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseResult
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryEraBar
import com.example.unifiedreader.core.model.LibraryPlaceCountry
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryBrowse
import com.example.unifiedreader.core.model.libraryBrowseValues
import com.example.unifiedreader.core.model.libraryEraTimeline
import com.example.unifiedreader.core.model.libraryPlaceCountries
import com.example.unifiedreader.core.model.libraryBrowseBooks
import com.example.unifiedreader.core.model.libraryBrowseLabel
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.isSeriesContainer
import com.example.unifiedreader.feature.library.libraryGridCoverImageVariant
import com.example.unifiedreader.feature.library.tags.LibraryTagSortSheet
import com.example.unifiedreader.feature.library.tags.LibraryTagSortTarget
import com.example.unifiedreader.feature.library.tags.LocalLibraryTagSorting
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * A source's books by what they are about. With nothing picked, the landing page: each row in the
 * shape of what is in it (country cards, a shelf of piles, genre covers, your reading, chips). With
 * something picked, the books that have all of it, and each row counted among those books: a 320 dp
 * pane beside the grid on Expanded windows, one column with the rows as tabs on narrower ones.
 */
@Composable
fun LibraryBrowsePage(
    source: SourceType,
    items: List<ReadableItem>,
    request: LibraryBrowseRequest,
    onRequestChanged: (LibraryBrowseRequest) -> Unit,
    showRows: Boolean,
    onShowRowsChanged: (Boolean) -> Unit,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    gridCellSizeDp: Int,
    contentTopInset: Dp,
    onClose: () -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onScrolledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = rememberLibraryBrowseRows(source)
    val shownRows = rows.settings.shown
    val progress = actionContext.progressByItemId
    val tags = LocalLibraryTagSorting.current
    // Null until your tag sorting has loaded: Browse waits for it rather than show every tag in About.
    val sorting = tags.sortingFor(source)
    // Thousands of books to sort into chips, so not on the main thread. The last view stays up
    // until the next is ready, so the chips and the grid change together.
    val view by produceState<LibraryBrowseView?>(null, items, request.picked, shownRows, progress, sorting) {
        val known = sorting ?: return@produceState
        value = withContext(Dispatchers.Default) {
            libraryBrowseView(
                items = items,
                picked = request.picked,
                rows = shownRows,
                sorting = known,
                readingStatus = { item -> libraryBrowseReadingStatus(progress[item.id]) },
                lastReadAt = { item -> progress[item.id]?.lastReadAtEpochMillis ?: 0L },
            )
        }
    }
    // How many books have each row's field, for Rows: the source's, whatever is picked.
    val coverage by produceState(emptyMap<LibraryBrowseRow, Int>(), items, progress, sorting) {
        val known = sorting ?: return@produceState
        value = withContext(Dispatchers.Default) {
            libraryBrowseCoverage(items, known) { item -> libraryBrowseReadingStatus(progress[item.id]) }
        }
    }
    // The tag a held chip opens the sort sheet for.
    var sortTarget by remember(source) { mutableStateOf<LibraryTagSortTarget?>(null) }
    var showPlaces by rememberSaveable(source) { mutableStateOf(false) }
    val books = remember(coverImageUrlProvider, progress) { LibraryBrowseBooks(coverImageUrlProvider, progress) }
    val latestRequest by rememberUpdatedState(request)
    val actions = LibraryBrowseActions(
        onPick = { chip -> onRequestChanged(latestRequest.adding(chip)) },
        onToggle = { chip -> onRequestChanged(latestRequest.toggling(chip)) },
        onRemove = { chip -> onRequestChanged(latestRequest.removing(chip)) },
        onClear = { onRequestChanged(LibraryBrowseRequest()) },
        // A series opens as a screen of its own, so Browse steps aside for it.
        onItemSelected = { item ->
            if (item.isSeriesContainer) onClose()
            onItemSelected(item)
        },
        onSortTag = { chip -> sortTarget = libraryBrowseSortTarget(source, items, chip, sorting ?: LibraryTagSorting()) },
    )
    val openTagSettings = tags.onOpenTagSettings?.let { open -> { open(source) } }
    val prompts = LibraryBrowseLandingPrompts(
        onCheckGuesses = openTagSettings?.takeIf { rows.loaded && !rows.placesPromptDismissed },
        onNotNow = rows::dismissPlacesPrompt,
        onSortNewTags = openTagSettings,
        onSortTag = actions.onSortTag,
    )
    Box(modifier = modifier.fillMaxSize()) {
        val shown = view
        val context = shown?.let { LibraryBrowseRowContext(view = it, books = books, actions = actions, onOpenPlaces = { showPlaces = true }) }
        when {
            context == null -> Unit
            context.view.picked.isEmpty() -> LibraryBrowseLanding(
                rows = shownRows,
                context = context,
                contentTopInset = contentTopInset,
                onScrolledChanged = onScrolledChanged,
                prompts = prompts,
            )
            currentAppWindowWidth() == AppWindowWidth.Expanded -> LibraryBrowseTwoPanes(
                rows = shownRows,
                context = context,
                actionContext = actionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                gridCellSizeDp = gridCellSizeDp,
                contentTopInset = contentTopInset,
                onScrolledChanged = onScrolledChanged,
            )
            else -> LibraryBrowseOnePane(
                rows = shownRows,
                context = context,
                actionContext = actionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                gridCellSizeDp = gridCellSizeDp,
                contentTopInset = contentTopInset,
                onScrolledChanged = onScrolledChanged,
            )
        }
    }
    val placesView = view
    if (showPlaces && placesView != null) {
        LibraryBrowsePlacesSheet(
            countries = placesView.countries,
            picked = placesView.picked,
            books = books,
            onToggle = actions.onToggle,
            onDismiss = { showPlaces = false },
        )
    }
    if (showRows) {
        LibraryBrowseRowsSheet(
            settings = rows.settings,
            coverage = coverage,
            onRowChanged = rows::set,
            onDismiss = { onShowRowsChanged(false) },
        )
    }
    sortTarget?.let { target ->
        LibraryTagSortSheet(
            target = target,
            onDismiss = { sortTarget = null },
            onBrowse = { chip -> onRequestChanged(LibraryBrowseRequest(picked = listOf(chip))) },
        )
    }
}

/**
 * What Browse draws, worked out together from the source's books and the [picked] chips. The
 * shapes (countries, piles, genre covers, your reading) count among the books shown, as the chips do.
 */
@Immutable
internal data class LibraryBrowseView(
    val picked: List<LibraryBrowseChip>,
    val result: LibraryBrowseResult,
    /** When's piles, each with the books its spines are coloured from. */
    val timeline: List<LibraryEraBar> = emptyList(),
    /** Set in with its cities folded into their countries, each with the books its card shows. */
    val countries: List<LibraryPlaceCountry> = emptyList(),
    /** A few books of each genre, by [com.example.unifiedreader.core.model.libraryTagKey], for its list cover. */
    val genreBooks: Map<String, List<ReadableItem>> = emptyMap(),
    /** The books in progress, last read first, for Your reading. */
    val inProgress: List<ReadableItem> = emptyList(),
    /** A few books of each reading status, by its label, for Your reading's covers. */
    val statusBooks: Map<String, List<ReadableItem>> = emptyMap(),
    /** On the landing page, how many books have each row's field, for its headings. */
    val coverage: Map<LibraryBrowseRow, Int> = emptyMap(),
    /** On the landing page, what to say about tags nobody has sorted. */
    val tagPrompt: LibraryBrowseTagPrompt? = null,
)

internal fun libraryBrowseView(
    items: List<ReadableItem>,
    picked: List<LibraryBrowseChip>,
    rows: List<LibraryBrowseRow>,
    sorting: LibraryTagSorting,
    readingStatus: (ReadableItem) -> LibraryReadingStatus?,
    lastReadAt: (ReadableItem) -> Long = { 0L },
): LibraryBrowseView {
    val landing = picked.isEmpty()
    val result = libraryBrowse(items, picked, sorting, rows, readingStatus)
    val shown = result.items
    val statuses = if (LibraryBrowseRow.READING in rows) shown.map { item -> item to readingStatus(item) } else emptyList()
    return LibraryBrowseView(
        picked = picked,
        result = result,
        timeline = if (LibraryBrowseRow.WHEN in rows) libraryEraTimeline(shown, sorting, bookCount = PileBookCount) else emptyList(),
        countries = if (LibraryBrowseRow.SET_IN in rows) libraryPlaceCountries(shown, sorting) else emptyList(),
        genreBooks = if (LibraryBrowseRow.GENRE in rows) libraryBrowseBooks(shown, LibraryBrowseRow.GENRE, sorting, CoverBookCount) else emptyMap(),
        inProgress = statuses.filter { (_, status) -> status == LibraryReadingStatus.IN_PROGRESS }
            .map { (item, _) -> item }
            .sortedByDescending(lastReadAt)
            .take(InProgressBookCount),
        statusBooks = statuses.mapNotNull { (item, status) -> status?.let { it.libraryBrowseLabel to item } }
            .groupBy(keySelector = { (label, _) -> label }, valueTransform = { (_, item) -> item })
            .mapValues { (_, books) -> books.take(StatusBookCount) },
        coverage = if (landing) libraryBrowseCoverage(items, sorting, readingStatus) else emptyMap(),
        tagPrompt = if (landing) libraryBrowseTagPrompt(items, sorting) else null,
    )
}

private const val PileBookCount = 14
private const val CoverBookCount = 4
private const val InProgressBookCount = 3
private const val StatusBookCount = 3

/** How many of [items] have each row's field; a row at 0 waits for metadata. */
internal fun libraryBrowseCoverage(
    items: List<ReadableItem>,
    sorting: LibraryTagSorting,
    readingStatus: (ReadableItem) -> LibraryReadingStatus?,
): Map<LibraryBrowseRow, Int> = LibraryBrowseRow.entries.associateWith { row ->
    items.count { item -> item.libraryBrowseValues(row, sorting, readingStatus(item)).isNotEmpty() }
}

internal class LibraryBrowseActions(
    val onPick: (LibraryBrowseChip) -> Unit,
    val onToggle: (LibraryBrowseChip) -> Unit,
    val onRemove: (LibraryBrowseChip) -> Unit,
    val onClear: () -> Unit,
    val onItemSelected: (ReadableItem) -> Unit,
    /** Opens the held tag chip's sort sheet. */
    val onSortTag: ((LibraryBrowseChip) -> Unit)? = null,
)
