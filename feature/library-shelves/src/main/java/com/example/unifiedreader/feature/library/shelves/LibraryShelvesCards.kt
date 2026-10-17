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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverActionCard
import com.example.unifiedreader.feature.library.LibraryCoverAspectRatio
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.ResolvedLibraryCover
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.appProgressFill
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** Shelf covers match the grid's covers: the grid's cell width, 2:3 shape, and image size. */
internal data class ShelfCoverLayout(
    val width: Dp,
    val imageVariant: LibraryCoverImageVariant,
)

@Composable
internal fun ShelfPreviewCard(
    item: ReadableItem,
    title: String,
    supportingText: String? = null,
    trailingText: String? = null,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverLoadingEnabled: Boolean = true,
    covers: ShelfCoverLayout,
    onClick: () -> Unit,
    menuContent: (@Composable () -> Unit)? = null,
    progressFraction: Float? = null,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    AppPanel(
        modifier = Modifier
            .width(covers.width + presentation.shelfItemSpacing * 2)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(presentation.cardCornerRadius),
        color = semanticColors.surfaceOverlay.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.05f)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
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
                imageVariant = covers.imageVariant,
                isDownloaded = isDownloaded,
                isRead = isRead,
                onClick = onClick,
                modifier = Modifier.padding(presentation.shelfItemSpacing),
                coverModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(LibraryCoverAspectRatio),
                titleStyle = MaterialTheme.typography.titleSmall,
                supportingStyle = MaterialTheme.typography.bodySmall,
                trailingColor = accentColor,
                metadataTopPadding = 8.dp,
                menuContent = menuContent,
                coverOverlay = {
                    progressFraction?.let { fraction ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(
                                    RoundedCornerShape(
                                        bottomStart = presentation.coverCornerRadius,
                                        bottomEnd = presentation.coverCornerRadius,
                                    ),
                                )
                                .appProgressFill(
                                    fraction = fraction,
                                    progressColor = semanticColors.readerAccent,
                                    trackColor = semanticColors.surfaceSelected.copy(alpha = 0.38f),
                                ),
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun ShelfListRow(
    item: ReadableItem,
    title: String,
    supportingText: String,
    trailingText: String,
    metadataText: String? = null,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    coverLoadingEnabled: Boolean = true,
    onClick: () -> Unit,
    menuContent: (@Composable () -> Unit)? = null,
    progressFraction: Float? = null,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    compact: Boolean = false,
    notifyCoverVisibleOnCompose: Boolean = true,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val rowShape = RoundedCornerShape(presentation.cardCornerRadius)
    val coverSize = if (compact) {
        36.dp to 52.dp
    } else {
        42.dp to 58.dp
    }
    val verticalPadding = if (compact) {
        (presentation.shelfCardVerticalPadding - 2.dp).coerceAtLeast(6.dp)
    } else {
        presentation.shelfCardVerticalPadding
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = presentation.shelfCardHorizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(width = coverSize.first, height = coverSize.second)) {
            ShelfCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                coverLoadingEnabled = coverLoadingEnabled,
                notifyVisibleOnCompose = notifyCoverVisibleOnCompose,
                isDownloaded = isDownloaded,
                isRead = isRead,
                modifier = Modifier.fillMaxSize(),
            )
            progressFraction?.let { fraction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = presentation.coverCornerRadius,
                                bottomEnd = presentation.coverCornerRadius,
                            ),
                        )
                        .appProgressFill(
                            fraction = fraction,
                            progressColor = semanticColors.readerAccent,
                            trackColor = semanticColors.surfaceSelected.copy(alpha = 0.34f),
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
                color = semanticColors.readerAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metadataText?.let { metadata ->
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    imageVariant: LibraryCoverImageVariant = LibraryCoverImageVariant.ShelfRow,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(item.id, coverLoadingEnabled, notifyVisibleOnCompose, imageVariant) {
        if (coverLoadingEnabled && notifyVisibleOnCompose) {
            onCoverVisible(item, imageVariant)
        }
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
        imageVariant = imageVariant,
        modifier = modifier,
    )
}

