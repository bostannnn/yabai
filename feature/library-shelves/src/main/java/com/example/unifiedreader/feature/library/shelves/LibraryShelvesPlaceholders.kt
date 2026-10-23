package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.unit.max
import com.example.unifiedreader.feature.library.LibraryCoverAspectRatio
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryReadingCardDefaults
import com.example.unifiedreader.feature.library.isLibraryCompactWindow
import com.example.unifiedreader.feature.library.libraryGridColumns
import com.example.unifiedreader.ui.components.appSkeletonPulse
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

internal const val LibraryShelvesSlotPlaceholderTag = "library-shelves-slot-placeholder"

@Composable
internal fun ShelfPreviewPlaceholderRow(
    coverWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    Row(
        modifier = modifier.appSkeletonPulse(),
        horizontalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
    ) {
        repeat(ShelfPreviewPlaceholderCount) {
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
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(72.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(presentation.coverCornerRadius))
                        .background(semanticColors.surfaceOverlay),
                )
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
    val compact = isLibraryCompactWindow()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LibraryShelvesSlotPlaceholderTag),
    ) {
        val columns = with(LocalDensity.current) { libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics) }
        val cardHeight = if (compact) {
            LibraryReadingCardDefaults.compactCoverWidth * 1.5f + 96.dp
        } else {
            max(columns.cellWidth * 1.5f, LibraryReadingCardDefaults.minTextHeight) + LibraryReadingCardDefaults.padding * 2
        }
        val card = @Composable { cardModifier: Modifier ->
            Box(
                modifier = cardModifier
                    .height(cardHeight)
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
                    coverWidth = columns.cellWidth,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        if (columns.count >= SideBySideColumns) {
            Row(horizontalArrangement = Arrangement.spacedBy(gridMetrics.spacing)) {
                card(Modifier.width(columns.span(SideBySideColumns / 2, gridMetrics.spacing)))
                band(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(StackedSpacing)) {
                card(Modifier.fillMaxWidth())
                band(Modifier.fillMaxWidth())
            }
        }
    }
}

private const val ShelfPreviewPlaceholderCount = 3
