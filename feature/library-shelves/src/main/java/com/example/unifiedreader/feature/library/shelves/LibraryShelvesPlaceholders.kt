package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.library.LibraryCoverAspectRatio
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.libraryGridColumns
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderContentOverlay
import com.example.unifiedreader.ui.theme.unifiedReaderSurfacePattern

internal const val LibraryShelvesSlotPlaceholderTag = "library-shelves-slot-placeholder"

@Composable
internal fun ShelfPreviewPlaceholderRow(
    coverWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val personality = UnifiedReaderThemeTokens.personality
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
    ) {
        repeat(ShelfPreviewPlaceholderCount) {
            // Same footprint as a loaded ShelfPreviewCard: the cover plus the card's padding.
            Column(
                modifier = Modifier
                    .width(coverWidth + presentation.shelfItemSpacing * 2)
                    .padding(presentation.shelfItemSpacing),
                verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(LibraryCoverAspectRatio)
                        .clip(RoundedCornerShape(presentation.coverCornerRadius))
                        .background(semanticColors.surfaceOverlay)
                        .unifiedReaderContentOverlay(
                            treatment = personality.surfaceTreatment,
                            accentColor = semanticColors.readerAccent,
                        ),
                )
                Box(
                    modifier = Modifier
                        .width(94.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(presentation.chipCornerRadius))
                        .background(semanticColors.surfaceSelected.copy(alpha = 0.54f)),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(72.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(presentation.chipCornerRadius))
                        .background(semanticColors.sourceAccent.copy(alpha = 0.25f)),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(84.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(presentation.chipCornerRadius))
                        .background(semanticColors.syncAccent.copy(alpha = 0.25f)),
                )
            }
        }
    }
}

@Composable
internal fun LibraryShelvesSlotPlaceholder(
    gridMetrics: LibraryGridMetrics,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val personality = UnifiedReaderThemeTokens.personality
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LibraryShelvesSlotPlaceholderTag),
    ) {
        val coverWidth = with(LocalDensity.current) {
            libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics).cellWidth
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(presentation.cardCornerRadius),
            colors = CardDefaults.cardColors(
                containerColor = semanticColors.surfaceRaised,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .unifiedReaderSurfacePattern(
                        treatment = personality.surfaceTreatment,
                        accentColor = semanticColors.sourceAccent,
                    )
                    .padding(
                        horizontal = presentation.shelfCardHorizontalPadding,
                        vertical = presentation.shelfCardVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(154.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(presentation.chipCornerRadius))
                            .background(semanticColors.surfaceSelected.copy(alpha = 0.5f)),
                    )
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(presentation.chipCornerRadius))
                            .background(semanticColors.sourceAccent.copy(alpha = 0.24f)),
                    )
                }
                ShelfPreviewPlaceholderRow(coverWidth = coverWidth)
            }
        }
    }
}

private const val ShelfPreviewPlaceholderCount = 3
