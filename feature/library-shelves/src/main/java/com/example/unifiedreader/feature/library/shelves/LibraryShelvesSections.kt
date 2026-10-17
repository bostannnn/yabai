package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.LibraryAnchoredItemMenu
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LibraryToggleChip
import com.example.unifiedreader.feature.library.hasMenuContent
import com.example.unifiedreader.feature.library.isDownloaded
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** How a shelf row lays out its covers: one per grid column, at the grid's cover size. */
internal data class ShelfRowLayout(
    val cellWidth: Dp,
    val spacing: Dp,
    val itemLimit: Int,
    val coverImageVariant: LibraryCoverImageVariant,
)

internal enum class ShelfHistoryTab(
    val title: String,
    val browserKind: LibraryShelfBrowserKind,
) {
    UpNext(
        title = "Up Next",
        browserKind = LibraryShelfBrowserKind.NextInSeries,
    ),
    RecentlyFinished(
        title = "Recently Finished",
        browserKind = LibraryShelfBrowserKind.RecentlyFinished,
    ),
}

@Composable
internal fun ContinueReadingShelfSection(
    items: List<ContinueReadingShelfItem>,
    rowLayout: ShelfRowLayout,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
) {
    val accentColor = UnifiedReaderThemeTokens.colors.readerAccent
    ShelfSection(
        showSeeAll = items.size > rowLayout.itemLimit,
        seeAllColor = accentColor,
        onSeeAll = onSeeAll,
        header = {
            ShelfSectionTitle(title = "Continue Reading", count = items.size)
        },
    ) {
        ShelfPreviewRow(rowLayout = rowLayout) {
            items.take(rowLayout.itemLimit).forEach { shelfItem ->
                ShelfPreviewCard(
                    item = shelfItem.item,
                    title = shelfItem.item.libraryPrimaryText(),
                    supportingText = continueReadingSupportingText(shelfItem.item),
                    trailingText = shelfItem.progress.detailLabel,
                    coverImageUrlProvider = coverImageUrlProvider,
                    imageVariant = rowLayout.coverImageVariant,
                    onClick = { onItemSelected(shelfItem.item) },
                    accentColor = accentColor,
                    menuContent = shelfItemMenu(
                        item = shelfItem.item,
                        itemActionContext = itemActionContext,
                        expandedMenuItemId = expandedMenuItemId,
                        onExpandedMenuChange = onExpandedMenuChange,
                        onDeleteImportedRequested = onDeleteImportedRequested,
                    ),
                    progressFraction = shelfItem.progress.fraction,
                    isDownloaded = itemActionContext.isDownloaded(shelfItem.item),
                    isRead = shelfItem.progress.isShelfComplete,
                    modifier = Modifier.width(rowLayout.cellWidth),
                )
            }
        }
    }
}

/** Up Next and Recently Finished share one row, with a switch between them when both have items. */
@Composable
internal fun ReadingHistoryShelfSection(
    nextInSeries: List<NextInSeriesShelfItem>,
    recentlyFinished: List<RecentlyFinishedShelfItem>,
    selectedTab: ShelfHistoryTab,
    onTabSelected: (ShelfHistoryTab) -> Unit,
    rowLayout: ShelfRowLayout,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onSeeAll: (ShelfHistoryTab) -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val selectedCount = when (selectedTab) {
        ShelfHistoryTab.UpNext -> nextInSeries.size
        ShelfHistoryTab.RecentlyFinished -> recentlyFinished.size
    }
    val accentColor = when (selectedTab) {
        ShelfHistoryTab.UpNext -> semanticColors.sourceAccent
        ShelfHistoryTab.RecentlyFinished -> semanticColors.syncAccent
    }
    ShelfSection(
        showSeeAll = selectedCount > rowLayout.itemLimit,
        seeAllColor = accentColor,
        onSeeAll = { onSeeAll(selectedTab) },
        header = {
            if (nextInSeries.isNotEmpty() && recentlyFinished.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShelfHistoryTab.entries.forEach { tab ->
                        val count = when (tab) {
                            ShelfHistoryTab.UpNext -> nextInSeries.size
                            ShelfHistoryTab.RecentlyFinished -> recentlyFinished.size
                        }
                        LibraryToggleChip(
                            label = "${tab.title} · $count",
                            selected = tab == selectedTab,
                            onClick = { onTabSelected(tab) },
                        )
                    }
                }
            } else {
                ShelfSectionTitle(title = selectedTab.title, count = selectedCount)
            }
        },
    ) {
        // Each tab starts scrolled to its first item.
        key(selectedTab) {
            ShelfPreviewRow(rowLayout = rowLayout) {
                when (selectedTab) {
                    ShelfHistoryTab.UpNext -> nextInSeries.take(rowLayout.itemLimit).forEach { shelfItem ->
                        ShelfPreviewCard(
                            item = shelfItem.item,
                            title = shelfItem.item.libraryPrimaryText(),
                            supportingText = shelfItem.item.librarySecondaryText()
                                ?: shelfItem.item.title,
                            trailingText = "After ${shelfItem.currentItem.librarySecondaryText() ?: shelfItem.currentItem.title}",
                            coverImageUrlProvider = coverImageUrlProvider,
                            imageVariant = rowLayout.coverImageVariant,
                            onClick = { onItemSelected(shelfItem.item) },
                            accentColor = accentColor,
                            menuContent = shelfItemMenu(
                                item = shelfItem.item,
                                itemActionContext = itemActionContext,
                                expandedMenuItemId = expandedMenuItemId,
                                onExpandedMenuChange = onExpandedMenuChange,
                                onDeleteImportedRequested = onDeleteImportedRequested,
                            ),
                            isDownloaded = itemActionContext.isDownloaded(shelfItem.item),
                            isRead = itemActionContext.progressByItemId[shelfItem.item.id]?.isShelfComplete == true,
                            modifier = Modifier.width(rowLayout.cellWidth),
                        )
                    }

                    ShelfHistoryTab.RecentlyFinished -> recentlyFinished.take(rowLayout.itemLimit).forEach { shelfItem ->
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
                            imageVariant = rowLayout.coverImageVariant,
                            onClick = { onItemSelected(shelfItem.item) },
                            accentColor = accentColor,
                            menuContent = shelfItemMenu(
                                item = shelfItem.item,
                                itemActionContext = itemActionContext,
                                expandedMenuItemId = expandedMenuItemId,
                                onExpandedMenuChange = onExpandedMenuChange,
                                onDeleteImportedRequested = onDeleteImportedRequested,
                            ),
                            modifier = Modifier.width(rowLayout.cellWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfSection(
    showSeeAll: Boolean,
    seeAllColor: Color,
    onSeeAll: () -> Unit,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ShelfSectionHeaderMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                header()
            }
            if (showSeeAll) {
                TextButton(onClick = onSeeAll) {
                    Text(
                        text = "See all",
                        color = seeAllColor,
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun ShelfSectionTitle(
    title: String,
    count: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShelfPreviewRow(
    rowLayout: ShelfRowLayout,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(rowLayout.spacing),
        content = content,
    )
}

private fun shelfItemMenu(
    item: ReadableItem,
    itemActionContext: LibraryItemActionContext,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
): (@Composable () -> Unit)? = if (itemActionContext.hasMenuContent(item)) {
    {
        LibraryAnchoredItemMenu(
            item = item,
            actionContext = itemActionContext,
            expanded = expandedMenuItemId == item.id,
            onExpandedChange = { expanded ->
                onExpandedMenuChange(item, expanded)
            },
            onDeleteImportedRequested = onDeleteImportedRequested,
            buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
        )
    }
} else {
    null
}

private val com.example.unifiedreader.feature.library.ItemProgressSummary.isShelfComplete: Boolean
    get() = isFinished || fraction >= 0.999f

private val ShelfSectionHeaderMinHeight = 40.dp
