package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.distinctUntilChanged

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

@Composable
fun KavitaScopeLandingScreen(
    title: String,
    items: List<KavitaScopeLandingItem>,
    isLoading: Boolean,
    errorMessage: String?,
    searchQuery: String,
    gridCellSizeDp: Int,
    browseContextKey: String = title,
    emptyMessage: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ -> item.coverUrl },
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { _, _ -> },
    onCoverViewportScrollingChanged: (Boolean) -> Unit = {},
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedLetter by rememberSaveable(title, searchQuery) { mutableStateOf<String?>(null) }
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
        searchedItems
            .mapNotNull { item -> item.title.filterLetter { it } }
            .distinct()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val effectiveLetter = selectedLetter?.takeIf { it in sortLetters }
    val visibleItems = remember(searchedItems, effectiveLetter) {
        effectiveLetter?.let { letter ->
            searchedItems.filter { item -> item.title.filterLetter { it } == letter }
        } ?: searchedItems
    }
    val visibleCoverItems = remember(visibleItems) {
        visibleItems.map(KavitaScopeLandingItem::toCoverItem)
    }
    val visibleCoverItemsById = remember(visibleCoverItems) {
        visibleCoverItems.associateBy(ReadableItem::id)
    }
    val alphabetRailVisible = sortLetters.size > 1 && searchedItems.isNotEmpty()
    val gridState = remember(title) { LazyGridState() }

    LaunchedEffect(selectedLetter, effectiveLetter) {
        if (selectedLetter != effectiveLetter) {
            selectedLetter = effectiveLetter
        }
    }
    LaunchedEffect(title, searchQuery, effectiveLetter) {
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
            libraryGridAlphabetRailEndPaddingDp
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
                top = 16.dp,
                end = gridEndPadding,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(
                key = "kavita-scope-header",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                KavitaScopeLandingHeader(
                    title = title,
                    itemCount = items.size,
                    visibleCount = searchedItems.size,
                    searchActive = searchQuery.isNotBlank(),
                    errorMessage = errorMessage,
                )
            }
            when {
                isLoading && items.isEmpty() -> item(
                    key = "kavita-scope-loading",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    LoadingLibraryState(
                        gridCellSizeDp = gridPresentation.selectedGridSize.cellSizeDp,
                    )
                }

                visibleItems.isEmpty() -> item(
                    key = "kavita-scope-empty",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    KavitaScopeLandingEmptyState(
                        message = if (searchQuery.isBlank()) {
                            emptyMessage
                        } else {
                            "No items match this search"
                        },
                    )
                }

                else -> items(
                    items = visibleItems,
                    key = KavitaScopeLandingItem::id,
                ) { item ->
                    KavitaScopeLandingCard(
                        item = item,
                        coverItem = visibleCoverItemsById[item.id] ?: item.toCoverItem(),
                        imageVariant = imageVariant,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onCoverVisible = onCoverVisible,
                        onClick = { onItemSelected(item.id) },
                    )
                }
            }
        }
        if (alphabetRailVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                AlphabetRail(
                    letters = sortLetters,
                    selectedLetter = effectiveLetter,
                    onLetterSelected = { letter -> selectedLetter = letter },
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun KavitaScopeLandingHeader(
    title: String,
    itemCount: Int,
    visibleCount: Int,
    searchActive: Boolean,
    errorMessage: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (searchActive) {
                "${visibleCount.itemCountLabel()} of ${itemCount.itemCountLabel()}"
            } else {
                itemCount.itemCountLabel()
            },
            style = MaterialTheme.typography.titleMedium,
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

@Composable
private fun KavitaScopeLandingCard(
    item: KavitaScopeLandingItem,
    coverItem: ReadableItem,
    imageVariant: LibraryCoverImageVariant,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kavita-scope-card-${item.id}")
            .clickable(onClick = onClick),
        shape = shape,
        color = colors.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .shadow(2.dp, shape, clip = false)
                    .clip(shape)
                    .background(kavitaScopeCoverBackdrop())
                    .border(
                        width = 1.dp,
                        color = colors.outlineVariant.copy(alpha = 0.54f),
                        shape = shape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                KavitaScopeLandingCover(
                    item = item,
                    coverItem = coverItem,
                    imageVariant = imageVariant,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.itemCount > 0) {
                    KavitaScopeCountBadge(
                        count = item.itemCount,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KavitaScopeLandingCover(
    item: KavitaScopeLandingItem,
    coverItem: ReadableItem,
    imageVariant: LibraryCoverImageVariant,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(coverItem.id, coverItem.coverUrl, imageVariant) {
        onCoverVisible(coverItem, imageVariant)
    }
    val coverUrl = coverImageUrlProvider(coverItem, imageVariant)?.takeIf(String::isNotBlank)
    if (coverUrl != null) {
        BucketedCoverImage(
            identityKey = coverItem.id,
            imageUrl = coverUrl,
            contentDescription = item.title,
            variant = imageVariant,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.title.initialsFallback(),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
    }
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
private fun KavitaScopeCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun KavitaScopeLandingEmptyState(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun kavitaScopeCoverBackdrop(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF22313A),
            colors.primary.copy(alpha = 0.50f),
            Color(0xFF101820),
        ),
    )
}

private fun Int.itemCountLabel(): String =
    if (this == 1) "1 Item" else "$this Items"

private fun String.initialsFallback(): String {
    val initials = trim()
        .split(Regex("\\s+"))
        .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
        .take(3)
        .joinToString("")
        .uppercase()
    return initials.ifBlank { "K" }
}
