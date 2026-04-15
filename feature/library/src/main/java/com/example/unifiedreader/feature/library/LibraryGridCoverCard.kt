package com.example.unifiedreader.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridCoverCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    isDownloaded: Boolean,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    val showCoverProgress = progress?.shouldShowCoverProgress == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-grid-card-${item.id}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        LibraryCover(
            item = item,
            coverUrl = coverImageUrlProvider(item, LibraryCoverImageVariant.Grid),
            isDownloaded = isDownloaded,
            isRead = progress?.isComplete == true,
            imageVariant = LibraryCoverImageVariant.Grid,
            downloadBadgeBottomInset = if (showCoverProgress) 2.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        progress?.takeIf { showCoverProgress }?.let { summary ->
            LibraryCoverProgressBar(
                progress = summary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
        menuContent?.invoke(this)
    }
}
