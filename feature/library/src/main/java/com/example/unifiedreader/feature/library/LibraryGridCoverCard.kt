package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.AppCheckbox

@Composable
internal fun GridCoverCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    rating: LibraryRatingBadgeState?,
    isDownloaded: Boolean,
    fadeRead: Boolean,
    imageVariant: LibraryCoverImageVariant,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    // A series tile (Kavita or Stump): its issue count draws the stack and fills the meta line.
    seriesIssueCount: Int? = null,
    title: String = itemPrimaryText(item),
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit = {},
    onSelectionStart: (() -> Unit)? = null,
    menuContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val progressLabel = progress
        ?.takeIf { it.shouldShowCoverProgress }
        ?.percentLabel
    val seriesLabel = seriesIssueCount?.let { count -> if (count == 1) "1 issue" else "$count issues" }
    val metaLabel = listOfNotNull(seriesLabel, progressLabel).joinToString(" · ").ifEmpty { null }

    LibraryCoverActionCard(
        item = item,
        title = title,
        coverImageUrlProvider = coverImageUrlProvider,
        imageVariant = imageVariant,
        isDownloaded = isDownloaded,
        isRead = progress?.isComplete == true,
        fadeRead = fadeRead,
        onClick = if (isSelectionMode) onSelectionToggle else onClick,
        onLongClick = onSelectionStart.takeUnless { isSelectionMode },
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-grid-card-${item.id}"),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(LibraryCoverAspectRatio)
            .then(if (seriesIssueCount != null && seriesIssueCount > 1) Modifier.librarySeriesStack() else Modifier),
        trailingText = metaLabel,
        menuContent = menuContent.takeUnless { isSelectionMode },
        emptyCoverContent = {
            LibraryCoverPlaceholder(
                item = item,
                reserveTopBadge = progress?.isComplete == true || isSelectionMode,
                reserveBottomBadge = rating != null || isDownloaded,
            )
        },
        coverOverlay = if (rating != null || isSelectionMode || progressLabel != null) {
            {
                progress?.takeIf { it.shouldShowCoverProgress }?.let { summary ->
                    LibraryCoverProgressBar(
                        fraction = summary.fraction,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    )
                }
                rating?.let { state ->
                    LibraryRatingBadge(
                        state = state,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                    )
                }
                if (isSelectionMode) {
                    AppCheckbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionToggle() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    )
                }
            }
        } else {
            null
        },
    )
}
