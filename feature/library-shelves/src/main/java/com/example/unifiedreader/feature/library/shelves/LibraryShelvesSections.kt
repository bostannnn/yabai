package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.LibraryAnchoredItemMenu
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext

@Composable
internal fun ContinueReadingShelfSection(
    items: List<ContinueReadingShelfItem>,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    previewContentEnabled: Boolean,
    coverLoadingEnabled: Boolean = true,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onMenuExpandedChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewItems = items.take(ShelfPreviewItemLimit)
    ShelfSectionCard(
        title = "Continue Reading",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        modifier = modifier,
    ) {
        if (previewContentEnabled) {
            ShelfPreviewRow {
                previewItems.forEach { shelfItem ->
                    ContinueReadingCarouselCard(
                        shelfItem = shelfItem,
                        itemActionContext = itemActionContext,
                        coverImageUrlProvider = coverImageUrlProvider,
                        coverLoadingEnabled = coverLoadingEnabled,
                        onClick = { onItemSelected(shelfItem.item) },
                        expandedMenuItemId = expandedMenuItemId,
                        onMenuExpandedChange = onMenuExpandedChange,
                        onDeleteImportedRequested = onDeleteImportedRequested,
                    )
                }
            }
        } else {
            ShelfPreviewPlaceholderRow()
        }
    }
}

@Composable
internal fun NextInSeriesShelfSection(
    items: List<NextInSeriesShelfItem>,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    previewContentEnabled: Boolean,
    coverLoadingEnabled: Boolean = true,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onMenuExpandedChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewItems = items.take(ShelfPreviewItemLimit)
    ShelfSectionCard(
        title = "Up Next",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        modifier = modifier,
    ) {
        if (previewContentEnabled) {
            ShelfPreviewRow {
                previewItems.forEach { shelfItem ->
                    ShelfPreviewCard(
                        item = shelfItem.item,
                        title = shelfItem.item.libraryPrimaryText(),
                        supportingText = shelfItem.item.librarySecondaryText()
                            ?: shelfItem.item.title,
                        trailingText = "After ${shelfItem.currentItem.librarySecondaryText() ?: shelfItem.currentItem.title}",
                        coverImageUrlProvider = coverImageUrlProvider,
                        coverLoadingEnabled = coverLoadingEnabled,
                        onClick = { onItemSelected(shelfItem.item) },
                        menuContent = {
                            LibraryAnchoredItemMenu(
                                item = shelfItem.item,
                                actionContext = itemActionContext,
                                expanded = expandedMenuItemId == shelfItem.item.id,
                                onExpandedChange = { expanded ->
                                    onMenuExpandedChange(shelfItem.item, expanded)
                                },
                                onDeleteImportedRequested = onDeleteImportedRequested,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                            )
                        },
                    )
                }
            }
        } else {
            ShelfPreviewPlaceholderRow()
        }
    }
}

@Composable
internal fun RecentlyFinishedShelfSection(
    items: List<RecentlyFinishedShelfItem>,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    previewContentEnabled: Boolean,
    coverLoadingEnabled: Boolean = true,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onMenuExpandedChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewItems = items.take(ShelfPreviewItemLimit)
    ShelfSectionCard(
        title = "Recently Finished",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        modifier = modifier,
    ) {
        if (previewContentEnabled) {
            ShelfPreviewRow {
                previewItems.forEach { shelfItem ->
                    ShelfPreviewCard(
                        item = shelfItem.item,
                        title = shelfItem.item.libraryPrimaryText(),
                        supportingText = shelfItem.item.librarySecondaryText()
                            ?: shelfItem.progress.label,
                        trailingText = DateUtils.getRelativeTimeSpanString(
                            shelfItem.progress.lastReadAtEpochMillis,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString(),
                        coverImageUrlProvider = coverImageUrlProvider,
                        coverLoadingEnabled = coverLoadingEnabled,
                        onClick = { onItemSelected(shelfItem.item) },
                        menuContent = {
                            LibraryAnchoredItemMenu(
                                item = shelfItem.item,
                                actionContext = itemActionContext,
                                expanded = expandedMenuItemId == shelfItem.item.id,
                                onExpandedChange = { expanded ->
                                    onMenuExpandedChange(shelfItem.item, expanded)
                                },
                                onDeleteImportedRequested = onDeleteImportedRequested,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                            )
                        },
                    )
                }
            }
        } else {
            ShelfPreviewPlaceholderRow()
        }
    }
}

@Composable
private fun ShelfPreviewRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ShelfSectionCard(
    title: String,
    count: Int,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = shelfCountLabel(count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showSeeAll) {
                    TextButton(onClick = onSeeAll) {
                        Text(text = "See all")
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun ContinueReadingCarouselCard(
    shelfItem: ContinueReadingShelfItem,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverLoadingEnabled: Boolean,
    onClick: () -> Unit,
    expandedMenuItemId: String?,
    onMenuExpandedChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    ShelfPreviewCard(
        item = shelfItem.item,
        title = shelfItem.item.libraryPrimaryText(),
        supportingText = continueReadingSupportingText(shelfItem.item),
        trailingText = shelfItem.progress.detailLabel,
        coverImageUrlProvider = coverImageUrlProvider,
        coverLoadingEnabled = coverLoadingEnabled,
        onClick = onClick,
        menuContent = {
            LibraryAnchoredItemMenu(
                item = shelfItem.item,
                actionContext = itemActionContext,
                expanded = expandedMenuItemId == shelfItem.item.id,
                onExpandedChange = { expanded ->
                    onMenuExpandedChange(shelfItem.item, expanded)
                },
                onDeleteImportedRequested = onDeleteImportedRequested,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        },
        progressFraction = shelfItem.progress.fraction,
    )
}

private fun shelfCountLabel(
    count: Int,
): String = when (count) {
    1 -> "1 item"
    else -> "$count items"
}

private const val ShelfPreviewItemLimit = 4
private const val SeeAllThreshold = 4
