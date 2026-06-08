package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem

@Composable
internal fun GridCoverCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    rating: LibraryRatingBadgeState?,
    isDownloaded: Boolean,
    fadeRead: Boolean,
    imageVariant: LibraryCoverImageVariant,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    issueCountBadge: Int? = null,
    title: String = itemPrimaryText(item),
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit = {},
    onSelectionStart: (() -> Unit)? = null,
    menuContent: (@Composable () -> Unit)? = null,
) {
    val progressLabel = progress
        ?.takeIf { it.shouldShowCoverProgress }
        ?.percentLabel

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
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-grid-card-${item.id}"),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f),
        trailingText = progressLabel,
        menuContent = menuContent.takeUnless { isSelectionMode },
        coverOverlay = if (rating != null || issueCountBadge != null || isSelectionMode) {
            {
                issueCountBadge?.takeUnless { isSelectionMode }?.let { count ->
                    LibraryCountBadge(
                        count = count,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
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
                    Checkbox(
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
