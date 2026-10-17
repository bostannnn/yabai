package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverActionCard
import com.example.unifiedreader.feature.library.LibraryCoverAspectRatio
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.ResolvedLibraryCover
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment
import com.example.unifiedreader.ui.theme.unifiedReaderProgressTreatment

/** A shelf item laid out like a grid card: the grid's cover size and shape, with text underneath. */
@Composable
internal fun ShelfPreviewCard(
    item: ReadableItem,
    title: String,
    supportingText: String,
    trailingText: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    imageVariant: LibraryCoverImageVariant,
    onClick: () -> Unit,
    accentColor: Color,
    menuContent: (@Composable () -> Unit)? = null,
    progressFraction: Float? = null,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val personality = UnifiedReaderThemeTokens.personality
    LibraryCoverActionCard(
        item = item,
        title = title,
        supportingText = supportingText,
        trailingText = trailingText,
        coverImageUrlProvider = coverImageUrlProvider,
        imageVariant = imageVariant,
        isDownloaded = isDownloaded,
        isRead = isRead,
        onClick = onClick,
        modifier = modifier,
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(LibraryCoverAspectRatio),
        trailingColor = accentColor,
        menuContent = menuContent,
        coverOverlay = progressFraction?.let { fraction ->
            {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = presentation.coverCornerRadius,
                                bottomEnd = presentation.coverCornerRadius,
                            ),
                        )
                        .unifiedReaderProgressTreatment(
                            treatment = personality.progressTreatment,
                            fraction = fraction,
                            progressColor = semanticColors.readerAccent,
                            trackColor = semanticColors.surfaceSelected.copy(alpha = 0.38f),
                        ),
                )
            }
        },
    )
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
    val personality = UnifiedReaderThemeTokens.personality
    val metadataRecipe = UnifiedReaderThemeTokens.components.metadata
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
            .background(
                semanticColors.surfaceRaised.copy(alpha = metadataRecipe.containerAlpha),
            )
            .unifiedReaderComponentTreatment(
                recipe = metadataRecipe,
                accentColor = semanticColors.readerAccent,
                secondaryColor = semanticColors.syncAccent,
                overlayColor = MaterialTheme.colorScheme.onSurface,
                shape = rowShape,
            )
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
                        .unifiedReaderProgressTreatment(
                            treatment = personality.progressTreatment,
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

