package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText

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
    coverImageUrlProvider: (ReadableItem) -> String?,
    onCoverVisible: (ReadableItem) -> Unit,
    onDismiss: () -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (sheet) {
                    is LibraryShelfSheetState.ContinueReading -> items(
                        items = sheet.items,
                        key = { it.item.id },
                    ) { shelfItem ->
                        ShelfListRow(
                            item = shelfItem.item,
                            title = shelfItem.item.libraryPrimaryText(),
                            supportingText = shelfItem.item.librarySecondaryText()
                                ?: shelfItem.progress.label,
                            trailingText = shelfItem.progress.detailLabel,
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onClick = { onItemSelected(shelfItem.item) },
                            progressFraction = shelfItem.progress.fraction,
                        )
                    }

                    is LibraryShelfSheetState.NextInSeries -> items(
                        items = sheet.items,
                        key = { it.item.id },
                    ) { shelfItem ->
                        ShelfListRow(
                            item = shelfItem.item,
                            title = shelfItem.item.libraryPrimaryText(),
                            supportingText = shelfItem.item.librarySecondaryText()
                                ?: shelfItem.item.title,
                            trailingText = "After ${shelfItem.currentItem.librarySecondaryText() ?: shelfItem.currentItem.title}",
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onClick = { onItemSelected(shelfItem.item) },
                        )
                    }

                    is LibraryShelfSheetState.RecentlyFinished -> items(
                        items = sheet.items,
                        key = { it.item.id },
                    ) { shelfItem ->
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
                        )
                    }
                }
            }
        }
    }
}
