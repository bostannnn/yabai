package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * The one loading stand-in for a grid cell: a 2:3 block at the cover radius plus two title
 * lines, on the raised surface. The paged grid renders it for rows Paging hasn't loaded yet
 * (the rail sorts page with placeholders enabled, so unloaded rows need a sized cell), and
 * the first-load skeleton tiles it. Mirrors the real card's footprint so rows keep a stable
 * height and only the content fades in.
 */
@Composable
internal fun LibraryGridPlaceholderCard(
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val blockColor = semanticColors.surfaceRaised
    val coverShape = RoundedCornerShape(presentation.coverCornerRadius)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(coverShape)
                .background(blockColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(coverShape)
                .background(blockColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.6f)
                .height(12.dp)
                .clip(coverShape)
                .background(blockColor),
        )
    }
}
