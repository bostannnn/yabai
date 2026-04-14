package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem

@Composable
internal fun GridCoverCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    isDownloaded: Boolean,
    coverImageUrlProvider: (ReadableItem) -> String?,
    onClick: () -> Unit,
) {
    val showCoverProgress = progress?.shouldShowCoverProgress == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library-grid-card-${item.id}")
            .clickable(onClick = onClick),
    ) {
        LibraryCover(
            item = item,
            coverUrl = coverImageUrlProvider(item),
            isDownloaded = isDownloaded,
            isRead = progress?.isComplete == true,
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
    }
}
