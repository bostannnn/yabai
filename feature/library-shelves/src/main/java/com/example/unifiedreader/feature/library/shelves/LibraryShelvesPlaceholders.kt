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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

internal const val LibraryShelvesSlotPlaceholderTag = "library-shelves-slot-placeholder"

/** Stands in for the two shelf rows while they load, at the same cover size, so nothing shifts on load. */
@Composable
internal fun LibraryShelvesSlotPlaceholder(
    gridMetrics: LibraryGridMetrics,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LibraryShelvesSlotPlaceholderTag),
    ) {
        val columns = with(LocalDensity.current) {
            libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            repeat(ShelfPlaceholderSectionCount) {
                ShelfPlaceholderSection(
                    cellWidth = columns.cellWidth,
                    spacing = gridMetrics.spacing,
                    itemCount = columns.count,
                )
            }
        }
    }
}

@Composable
private fun ShelfPlaceholderSection(
    cellWidth: Dp,
    spacing: Dp,
    itemCount: Int,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val personality = UnifiedReaderThemeTokens.personality
    val barShape = RoundedCornerShape(presentation.chipCornerRadius)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .width(154.dp)
                    .height(18.dp)
                    .clip(barShape)
                    .background(semanticColors.surfaceSelected.copy(alpha = 0.5f)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            repeat(itemCount) {
                Column(modifier = Modifier.width(cellWidth)) {
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
                            .padding(top = 8.dp)
                            .fillMaxWidth(0.72f)
                            .height(12.dp)
                            .clip(barShape)
                            .background(semanticColors.surfaceSelected.copy(alpha = 0.54f)),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(0.56f)
                            .height(10.dp)
                            .clip(barShape)
                            .background(semanticColors.sourceAccent.copy(alpha = 0.25f)),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(0.64f)
                            .height(10.dp)
                            .clip(barShape)
                            .background(semanticColors.syncAccent.copy(alpha = 0.25f)),
                    )
                }
            }
        }
    }
}

private const val ShelfPlaceholderSectionCount = 2
