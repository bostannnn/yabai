package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.ResolvedLibraryCover

@Composable
internal fun ShelfPreviewCard(
    item: ReadableItem,
    title: String,
    supportingText: String? = null,
    trailingText: String? = null,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverLoadingEnabled: Boolean = true,
    onClick: () -> Unit,
    menuContent: (@Composable BoxScope.() -> Unit)? = null,
    progressFraction: Float? = null,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
) {
    Card(
        modifier = Modifier
            .width(ShelfPreviewCardWidth)
            .height(ShelfPreviewCardHeight)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                ShelfCover(
                    item = item,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = { _, _ -> },
                    coverLoadingEnabled = coverLoadingEnabled,
                    notifyVisibleOnCompose = false,
                    isDownloaded = isDownloaded,
                    isRead = isRead,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(CoverAspectRatio),
                )
                progressFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    bottomStart = CoverCornerRadius,
                                    bottomEnd = CoverCornerRadius,
                                ),
                            ),
                    )
                }
                menuContent?.invoke(this)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supportingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                trailingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ShelfListRow(
    item: ReadableItem,
    title: String,
    supportingText: String,
    trailingText: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    coverLoadingEnabled: Boolean = true,
    onClick: () -> Unit,
    menuContent: (@Composable () -> Unit)? = null,
    progressFraction: Float? = null,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 42.dp, height = 58.dp),
        ) {
            ShelfCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                coverLoadingEnabled = coverLoadingEnabled,
                isDownloaded = isDownloaded,
                isRead = isRead,
                modifier = Modifier.fillMaxSize(),
            )
            progressFraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                bottomStart = CoverCornerRadius,
                                bottomEnd = CoverCornerRadius,
                            ),
                        ),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        menuContent?.invoke()
    }
}

@Composable
internal fun ShelfCover(
    item: ReadableItem,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    coverLoadingEnabled: Boolean = true,
    notifyVisibleOnCompose: Boolean = true,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(item.id, coverLoadingEnabled, notifyVisibleOnCompose) {
        if (coverLoadingEnabled && notifyVisibleOnCompose) {
            onCoverVisible(item, LibraryCoverImageVariant.ShelfRow)
        }
    }
    val variant = if (notifyVisibleOnCompose) {
        LibraryCoverImageVariant.ShelfRow
    } else {
        LibraryCoverImageVariant.ShelfPreview
    }
    ResolvedLibraryCover(
        item = item,
        coverImageUrlProvider = { coverItem, coverVariant ->
            if (coverLoadingEnabled) {
                coverImageUrlProvider(coverItem, coverVariant)
            } else {
                null
            }
        },
        isDownloaded = isDownloaded,
        isRead = isRead,
        imageVariant = variant,
        modifier = modifier,
    )
}

private val ShelfPreviewCardWidth = 132.dp
private val ShelfPreviewCardHeight = 226.dp
private val CoverCornerRadius = 4.dp
private const val CoverAspectRatio = 0.72f
