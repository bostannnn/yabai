package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.library.LibraryCoverAspectRatio
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryReadingCardDefaults
import com.example.unifiedreader.feature.library.libraryGridColumns
import com.example.unifiedreader.feature.library.readingCardBesideWidth
import com.example.unifiedreader.ui.components.appSkeletonPulse
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

internal const val LibraryShelvesSlotPlaceholderTag = "library-shelves-slot-placeholder"

@Composable
internal fun ShelfPreviewPlaceholderRow(
    coverWidth: Dp,
    modifier: Modifier = Modifier,
    count: Int = ShelfPreviewPlaceholderCount,
    showSupportingLine: Boolean = true,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    Row(
        modifier = modifier.appSkeletonPulse(),
        horizontalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
    ) {
        repeat(count) {
            // Same footprint as a loaded ShelfPreviewCard: a bare cover tile.
            Column(
                modifier = Modifier.width(coverWidth),
                verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(LibraryCoverAspectRatio)
                        .clip(RoundedCornerShape(presentation.coverCornerRadius))
                        .background(semanticColors.surfaceOverlay),
                )
                Box(
                    modifier = Modifier
                        .width(94.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(presentation.coverCornerRadius))
                        .background(semanticColors.surfaceOverlay),
                )
                if (showSupportingLine) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .width(72.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(presentation.coverCornerRadius))
                            .background(semanticColors.surfaceOverlay),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(84.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(presentation.coverCornerRadius))
                        .background(semanticColors.surfaceOverlay),
                )
            }
        }
    }
}

/**
 * The shelves zone while it loads, in the shape it will take: the reading card's footprint and
 * the tabs over a strip of cover tiles, side by side or stacked as [LibraryShelvesZone] lays them.
 */
@Composable
internal fun LibraryShelvesSlotPlaceholder(
    gridMetrics: LibraryGridMetrics,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LibraryShelvesSlotPlaceholderTag),
    ) {
        val columns = with(LocalDensity.current) { libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics) }
        val cardWidth = columns.readingCardBesideWidth(gridMetrics.spacing)
        // Beside the card the strip is Small, as many tiles as fill the shelves' share of the row.
        val stripCoverWidth = if (cardWidth != null) ShelfBesideCardCoverWidth else columns.cellWidth
        val stripCount = if (cardWidth != null) {
            ((maxWidth - cardWidth) / (stripCoverWidth + gridMetrics.spacing)).toInt().coerceAtLeast(1)
        } else {
            ShelfPreviewPlaceholderCount
        }
        val card = @Composable { cardModifier: Modifier ->
            Box(
                modifier = cardModifier
                    .appSkeletonPulse()
                    .clip(RoundedCornerShape(LibraryReadingCardDefaults.cornerRadius))
                    .background(semanticColors.surfaceRaised),
            )
        }
        val band = @Composable { bandModifier: Modifier ->
            Column(modifier = bandModifier) {
                Row(
                    modifier = Modifier.appSkeletonPulse(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(118.dp, 92.dp, 96.dp).forEach { width ->
                        Box(
                            modifier = Modifier
                                .width(width)
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(semanticColors.surfaceOverlay),
                        )
                    }
                }
                ShelfPreviewPlaceholderRow(
                    coverWidth = stripCoverWidth,
                    count = stripCount,
                    showSupportingLine = cardWidth == null,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        if (cardWidth != null) {
            CardBesideBand(
                cardWidth = cardWidth,
                spacing = gridMetrics.spacing,
                card = { card(Modifier.fillMaxSize()) },
                band = { band(Modifier) },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(StackedSpacing)) {
                card(Modifier.fillMaxWidth().height(LibraryReadingCardDefaults.height))
                band(Modifier.fillMaxWidth())
            }
        }
    }
}

private const val ShelfPreviewPlaceholderCount = 3
