package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverActionCard
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.ResolvedLibraryCover
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun ShelfPreviewCard(
    item: ReadableItem,
    title: String,
    supportingText: String? = null,
    trailingText: String? = null,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverLoadingEnabled: Boolean = true,
    onClick: () -> Unit,
    menuContent: (@Composable () -> Unit)? = null,
    progressFraction: Float? = null,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    Card(
        modifier = Modifier
            .width(ShelfPreviewCardWidth)
            .height(ShelfPreviewCardHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(presentation.cardCornerRadius),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
        colors = CardDefaults.cardColors(
            containerColor = semanticColors.surfaceOverlay.copy(alpha = 0.72f),
        ),
    ) {
        LibraryCoverActionCard(
            item = item,
            title = title,
            supportingText = supportingText,
            trailingText = trailingText,
            coverImageUrlProvider = { coverItem, coverVariant ->
                if (coverLoadingEnabled) {
                    coverImageUrlProvider(coverItem, coverVariant)
                } else {
                    null
                }
            },
            imageVariant = LibraryCoverImageVariant.ShelfPreview,
            isDownloaded = isDownloaded,
            isRead = isRead,
            onClick = onClick,
            modifier = Modifier.padding(presentation.shelfItemSpacing),
            coverModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CoverAspectRatio),
            titleStyle = MaterialTheme.typography.titleSmall,
            supportingStyle = MaterialTheme.typography.bodySmall,
            trailingColor = accentColor,
            metadataTopPadding = 8.dp,
            menuContent = menuContent,
            coverOverlay = {
                progressFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    bottomStart = presentation.coverCornerRadius,
                                    bottomEnd = presentation.coverCornerRadius,
                                ),
                            ),
                        color = semanticColors.readerAccent,
                        trackColor = semanticColors.surfaceSelected.copy(alpha = 0.38f),
                    )
                }
            },
        )
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
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(presentation.cardCornerRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = presentation.shelfCardHorizontalPadding, vertical = presentation.shelfCardVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
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
                                bottomStart = presentation.coverCornerRadius,
                                bottomEnd = presentation.coverCornerRadius,
                            ),
                    ),
                    color = semanticColors.readerAccent,
                    trackColor = semanticColors.surfaceSelected.copy(alpha = 0.34f),
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
                color = semanticColors.readerAccent,
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
private val ShelfPreviewCardHeight = 242.dp
private const val CoverAspectRatio = 0.72f
