package com.example.unifiedreader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppEmptyState
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class KavitaScopeLandingItem(
    val id: String,
    val title: String,
    val itemCount: Int,
    val summary: String? = null,
    val coverUrl: String? = null,
    val sourceType: SourceType = SourceType.KAVITA,
    val remoteId: String? = null,
    val coverCacheScope: String = "scope",
)

/**
 * Kavita's Collections or Reading lists: a switch between the two, how many there are, and a grid
 * of their covers with the count on each tile's meta line (a collection drawn as a stack of series).
 */
@Composable
fun KavitaScopeLandingScreen(
    section: KavitaLibrarySection,
    items: List<KavitaScopeLandingItem>,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    gridCellSizeDp: Int,
    browseContextKey: String = section.name,
    notConnected: Boolean = false,
    unreachable: Boolean = false,
    onSectionSelected: (KavitaLibrarySection) -> Unit = {},
    onConnect: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onCoverViewportScrollingChanged: (Boolean) -> Unit = {},
    onItemSelected: (String) -> Unit,
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val searchedItems = remember(items, searchQuery) {
        val query = searchQuery.trim()
        items
            .filter { item ->
                query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.summary.orEmpty().contains(query, ignoreCase = true)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { item -> item.title })
    }
    val sortLetters = remember(searchedItems) {
        alphabetNavigationSections(
            searchedItems.mapNotNull { item -> item.title.filterLetter { it } },
        )
    }
    val itemAlphabetSections = remember(searchedItems) {
        searchedItems.map { item -> item.title.filterLetter { it } }
    }
    val alphabetSectionStarts = remember(itemAlphabetSections) {
        alphabetSectionStarts(itemAlphabetSections)
    }
    val visibleCoverItems = remember(searchedItems) {
        searchedItems.map(KavitaScopeLandingItem::toCoverItem)
    }
    val visibleCoverItemsById = remember(visibleCoverItems) {
        visibleCoverItems.associateBy(ReadableItem::id)
    }
    val alphabetRailVisible = sortLetters.size > 1 && searchedItems.isNotEmpty()
    val gridState = remember(browseContextKey) { LazyGridState() }
    val activeLetter by remember(gridState, itemAlphabetSections) {
        derivedStateOf {
            activeAlphabetSection(
                sectionKeys = itemAlphabetSections,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                leadingContentItemCount = 1,
            )
        }
    }

    LaunchedEffect(browseContextKey, searchQuery) {
        gridState.scrollToItem(0)
    }
    LaunchedEffect(gridState, onCoverViewportScrollingChanged) {
        snapshotFlow { gridState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(onCoverViewportScrollingChanged)
    }
    DisposableEffect(onCoverViewportScrollingChanged) {
        onDispose {
            onCoverViewportScrollingChanged(false)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val gridEndPadding = if (alphabetRailVisible) {
            libraryGridNavigationEndPaddingDp
        } else {
            libraryGridEndPaddingDp
        }
        val gridPresentation = remember(maxWidth, gridEndPadding, gridCellSizeDp) {
            libraryGridPresentation(
                maxWidth = maxWidth,
                endPadding = gridEndPadding,
                requestedGridCellSizeDp = gridCellSizeDp,
            )
        }
        val imageVariant = libraryGridCoverImageVariant(
            gridPresentation.selectedGridSize.cellSizeDp,
        )
        VisibleRemoteCoverPrefetcher(
            browseContextKey = browseContextKey,
            gridState = gridState,
            items = visibleCoverItems,
            leadingContentItemCount = 1,
            variant = imageVariant,
            maxTargetsWhileScrolling = gridPresentation.scrollingPrefetchItemLimit,
            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
            onCoverVisible = onCoverVisible,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridPresentation.selectedGridSize.cellSizeDp.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = libraryGridStartPaddingDp,
                top = 16.dp + contentTopInset,
                end = gridEndPadding,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(
                key = "kavita-scope-header",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                KavitaScopeLandingHeader(
                    section = section,
                    countLabel = kavitaScopeLandingCountLabel(
                        section = section,
                        total = items.size,
                        shown = searchedItems.size,
                        searchActive = searchQuery.isNotBlank(),
                    ).takeUnless { items.isEmpty() },
                    errorMessage = errorMessage.takeUnless { notConnected },
                    onSectionSelected = onSectionSelected,
                )
            }
            when {
                isLoading && items.isEmpty() && !notConnected -> item(
                    key = "kavita-scope-loading",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    LoadingLibraryState(
                        gridCellSizeDp = gridPresentation.selectedGridSize.cellSizeDp,
                    )
                }

                searchedItems.isEmpty() -> item(
                    key = "kavita-scope-empty",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    KavitaScopeLandingEmptyState(
                        section = section,
                        reason = kavitaScopeLandingEmptyReason(
                            searchActive = searchQuery.isNotBlank(),
                            notConnected = notConnected,
                            unreachable = unreachable,
                        ),
                        onConnect = onConnect,
                        onRetry = onRetry,
                    )
                }

                else -> items(
                    items = searchedItems,
                    key = KavitaScopeLandingItem::id,
                    contentType = { LibraryScreenContentTypes.GridItem },
                ) { item ->
                    KavitaScopeLandingCard(
                        section = section,
                        item = item,
                        coverItem = visibleCoverItemsById[item.id] ?: item.toCoverItem(),
                        imageVariant = imageVariant,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onClick = { onItemSelected(item.id) },
                    )
                }
            }
        }
        val showBackToTop by remember(
            gridState,
            gridPresentation.selectedGridSize.columnCount,
        ) {
            derivedStateOf {
                shouldShowLibraryBackToTop(
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    leadingContentItemCount = 1,
                    columnCount = gridPresentation.selectedGridSize.columnCount,
                )
            }
        }
        if (alphabetRailVisible) {
            AlphabetNavigationRail(
                sections = sortLetters,
                activeSection = activeLetter,
                onSectionSelected = { section ->
                    alphabetSectionStarts[section]?.let { itemIndex ->
                        coroutineScope.launch {
                            gridState.scrollToItem(1 + itemIndex)
                        }
                    }
                },
                // Under the floating top bar, like the library's rail.
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp, top = contentTopInset + 8.dp, bottom = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = showBackToTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = if (alphabetRailVisible) {
                        libraryGridNavigationEndPaddingDp + 12.dp
                    } else {
                        libraryGridEndPaddingDp
                    },
                    bottom = 24.dp,
                ),
        ) {
            LibraryBackToTopButton(
                onClick = {
                    coroutineScope.launch {
                        if (gridState.firstVisibleItemIndex <= 80) {
                            gridState.animateScrollToItem(0)
                        } else {
                            gridState.scrollToItem(0)
                        }
                    }
                },
            )
        }
    }
}

/**
 * The switch between Collections and Reading lists, then how many there are (its line keeps its
 * height while they load, so the grid does not move) and any error. The name stays in the bar.
 */
@Composable
private fun KavitaScopeLandingHeader(
    section: KavitaLibrarySection,
    countLabel: String?,
    errorMessage: String?,
    onSectionSelected: (KavitaLibrarySection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppSegmentedRow(modifier = Modifier.width(KavitaScopeSwitchWidth)) {
            KavitaScopeSections.forEach { option ->
                AppSegment(
                    selected = option == section,
                    onClick = { if (option != section) onSectionSelected(option) },
                    label = { Text(text = option.displayName(), maxLines = 1) },
                )
            }
        }
        Text(
            text = countLabel ?: " ",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        errorMessage?.takeIf(String::isNotBlank)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val KavitaScopeSections = listOf(KavitaLibrarySection.Collections, KavitaLibrarySection.ReadingLists)
private val KavitaScopeSwitchWidth = 280.dp

@Composable
private fun KavitaScopeLandingCard(
    section: KavitaLibrarySection,
    item: KavitaScopeLandingItem,
    coverItem: ReadableItem,
    imageVariant: LibraryCoverImageVariant,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onClick: () -> Unit,
) {
    LibraryCoverActionCard(
        item = coverItem,
        title = item.title,
        coverImageUrlProvider = coverImageUrlProvider,
        imageVariant = imageVariant,
        isDownloaded = false,
        isRead = false,
        fadeRead = false,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kavita-scope-card-${item.id}"),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            // A collection holds series, so it is drawn as their stack.
            .then(if (section == KavitaLibrarySection.Collections) Modifier.librarySeriesStack() else Modifier),
        trailingText = kavitaScopeLandingItemMeta(section, item.itemCount),
        emptyCoverContent = {
            Text(
                text = item.title.initialsFallback(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        },
    )
}

private fun KavitaScopeLandingItem.toCoverItem(): ReadableItem = ReadableItem(
    id = id,
    sourceType = sourceType,
    libraryId = "kavita-$coverCacheScope",
    remoteId = remoteId,
    remoteFileType = coverCacheScope,
    title = title,
    format = FormatType.CBZ,
    kind = ReadableItemKind.COMIC,
    coverUrl = coverUrl,
)

@Composable
private fun KavitaScopeLandingEmptyState(
    section: KavitaLibrarySection,
    reason: KavitaScopeLandingEmptyReason,
    onConnect: (() -> Unit)?,
    onRetry: (() -> Unit)?,
) {
    val text = kavitaScopeLandingEmptyText(section, reason)
    val action: Pair<String, () -> Unit>? = when (reason) {
        KavitaScopeLandingEmptyReason.NotConnected -> onConnect?.let { "Connect Kavita" to it }
        KavitaScopeLandingEmptyReason.Unreachable -> onRetry?.let { "Try again" to it }
        else -> null
    }
    AppEmptyState(
        title = text.title,
        body = text.body,
        actions = action?.let { (label, onClick) ->
            {
                AppButton(onClick = onClick) {
                    Text(text = label, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
}

private fun String.initialsFallback(): String {
    val initials = trim()
        .split(Regex("\\s+"))
        .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
        .take(3)
        .joinToString("")
        .uppercase()
    return initials.ifBlank { "K" }
}
