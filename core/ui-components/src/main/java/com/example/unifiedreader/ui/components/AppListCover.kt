package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A list's cover: the first four books' covers in a 2×2 mosaic at the cover's 2:3 and corner
 * radius, 2 dp apart, so a list reads apart from a book and from a series' stack. [cell] draws
 * cover [index] filling the modifier it is given. One to three books show the first cover whole
 * (a mosaic with empty cells reads as broken); an empty list is the bare `surfaceContainerHigh` card.
 */
@Composable
fun AppListCover(
    count: Int,
    modifier: Modifier = Modifier,
    cell: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val radius = UnifiedReaderThemeTokens.presentation.coverCornerRadius
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalArrangement = Arrangement.spacedBy(AppListCoverDefaults.gap),
    ) {
        if (count in 1 until AppListCoverDefaults.mosaicCount) {
            cell(0, Modifier.fillMaxSize())
        } else {
            repeat(2) { rowIndex ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppListCoverDefaults.gap),
                ) {
                    repeat(2) { columnIndex ->
                        val index = rowIndex * 2 + columnIndex
                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            if (index < count) cell(index, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

object AppListCoverDefaults {
    val gap = 2.dp

    /** How many books a list needs for the mosaic; fewer show the first cover. */
    const val mosaicCount = 4
}
