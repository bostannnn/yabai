package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.LibraryAnchoredItemMenu
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.hasMenuContent
import com.example.unifiedreader.feature.library.isDownloaded
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment
import com.example.unifiedreader.ui.theme.unifiedReaderSurfacePattern

internal sealed interface LibraryShelfSheetState {
    val title: String
    val count: Int

    data class ContinueReading(
        val items: List<ContinueReadingShelfItem>,
    ) : LibraryShelfSheetState {
        override val title: String = "Continue Reading"
        override val count: Int = items.size
    }

    data class NextInSeries(
        val items: List<NextInSeriesShelfItem>,
    ) : LibraryShelfSheetState {
        override val title: String = "Up Next"
        override val count: Int = items.size
    }

    data class RecentlyFinished(
        val items: List<RecentlyFinishedShelfItem>,
    ) : LibraryShelfSheetState {
        override val title: String = "Recently Finished"
        override val count: Int = items.size
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryShelfItemsSheet(
    sheet: LibraryShelfSheetState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onDismiss: () -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val sheetRecipe = UnifiedReaderThemeTokens.components.sheet
    val sheetShape = RoundedCornerShape(
        topStart = presentation.cardCornerRadius,
        topEnd = presentation.cardCornerRadius,
    )
    val listState = rememberLazyListState()
    var expandedMenuItemId by remember(sheet) { mutableStateOf<String?>(null) }
    LaunchedEffect(sheet) {
        listState.scrollToItem(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.unifiedReaderComponentTreatment(
            recipe = sheetRecipe,
            accentColor = semanticColors.readerAccent,
            secondaryColor = semanticColors.syncAccent,
            overlayColor = MaterialTheme.colorScheme.onSurface,
            shape = sheetShape,
        ),
        containerColor = semanticColors.surfaceBase,
        shape = sheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .unifiedReaderSurfacePattern(
                    treatment = sheetRecipe.surfaceTreatment,
                    accentColor = semanticColors.readerAccent,
                    overlayColor = MaterialTheme.colorScheme.onSurface,
                    intensity = sheetRecipe.patternIntensity,
                )
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = sheet.title,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = when (sheet.count) {
                            1 -> "1 item"
                            else -> "${sheet.count} items"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ShelfItemsList(
                sheet = sheet,
                listState = listState,
                itemActionContext = itemActionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onItemSelected = onItemSelected,
                expandedMenuItemId = expandedMenuItemId,
                onExpandedMenuChange = { item, expanded ->
                    expandedMenuItemId = if (expanded) item.id else null
                },
                onDeleteImportedItem = onDeleteImportedItem,
            )
        }
    }
}

@Composable
private fun ShelfItemsList(
    sheet: LibraryShelfSheetState,
    listState: LazyListState,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = SheetListMaxHeight),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (sheet) {
            is LibraryShelfSheetState.ContinueReading -> items(
                items = sheet.items,
                key = { it.item.id },
            ) { shelfItem ->
                val hasMenuContent = itemActionContext.hasMenuContent(shelfItem.item)
                ShelfListRow(
                    item = shelfItem.item,
                    title = shelfItem.item.libraryPrimaryText(),
                    supportingText = shelfItem.item.librarySecondaryText()
                        ?: shelfItem.progress.label,
                    trailingText = shelfItem.progress.detailLabel,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onClick = { onItemSelected(shelfItem.item) },
                    menuContent = if (hasMenuContent) {
                        {
                            LibraryAnchoredItemMenu(
                                item = shelfItem.item,
                                actionContext = itemActionContext,
                                expanded = expandedMenuItemId == shelfItem.item.id,
                                onExpandedChange = { expanded ->
                                    onExpandedMenuChange(shelfItem.item, expanded)
                                },
                                onDeleteImportedRequested = onDeleteImportedItem,
                            )
                        }
                    } else {
                        null
                    },
                    progressFraction = shelfItem.progress.fraction,
                    isDownloaded = itemActionContext.isDownloaded(shelfItem.item),
                    isRead = shelfItem.progress.isShelfComplete,
                )
            }

            is LibraryShelfSheetState.NextInSeries -> items(
                items = sheet.items,
                key = { it.item.id },
            ) { shelfItem ->
                val hasMenuContent = itemActionContext.hasMenuContent(shelfItem.item)
                ShelfListRow(
                    item = shelfItem.item,
                    title = shelfItem.item.libraryPrimaryText(),
                    supportingText = shelfItem.item.librarySecondaryText()
                        ?: shelfItem.item.title,
                    trailingText = "After ${shelfItem.currentItem.librarySecondaryText() ?: shelfItem.currentItem.title}",
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onClick = { onItemSelected(shelfItem.item) },
                    menuContent = if (hasMenuContent) {
                        {
                            LibraryAnchoredItemMenu(
                                item = shelfItem.item,
                                actionContext = itemActionContext,
                                expanded = expandedMenuItemId == shelfItem.item.id,
                                onExpandedChange = { expanded ->
                                    onExpandedMenuChange(shelfItem.item, expanded)
                                },
                                onDeleteImportedRequested = onDeleteImportedItem,
                            )
                        }
                    } else {
                        null
                    },
                    isDownloaded = itemActionContext.isDownloaded(shelfItem.item),
                    isRead = itemActionContext.progressByItemId[shelfItem.item.id]?.isShelfComplete == true,
                )
            }

            is LibraryShelfSheetState.RecentlyFinished -> items(
                items = sheet.items,
                key = { it.item.id },
            ) { shelfItem ->
                val hasMenuContent = itemActionContext.hasMenuContent(shelfItem.item)
                ShelfListRow(
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
                    onCoverVisible = onCoverVisible,
                    onClick = { onItemSelected(shelfItem.item) },
                    menuContent = if (hasMenuContent) {
                        {
                            LibraryAnchoredItemMenu(
                                item = shelfItem.item,
                                actionContext = itemActionContext,
                                expanded = expandedMenuItemId == shelfItem.item.id,
                                onExpandedChange = { expanded ->
                                    onExpandedMenuChange(shelfItem.item, expanded)
                                },
                                onDeleteImportedRequested = onDeleteImportedItem,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private val com.example.unifiedreader.feature.library.ItemProgressSummary.isShelfComplete: Boolean
    get() = isFinished || fraction >= 0.999f

private val SheetListMaxHeight = 560.dp
