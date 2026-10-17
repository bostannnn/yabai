package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A sized, neutral stand-in for a not-yet-loaded paged grid cell. The rail sorts page with
 * placeholders enabled (so the A–Z rail can jump by grid offset), which means the grid renders slots
 * for rows Paging hasn't loaded yet; without a sized cell those rows would collapse. Mirrors the real
 * card's footprint — a 2:3 cover plus a two-line title block — so rows keep a stable height and only
 * the cover/title content fades in when the page loads.
 */
@Composable
internal fun LibraryGridPlaceholderCard(
    modifier: Modifier = Modifier,
) {
    val blockColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val blockShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(blockShape)
                .background(blockColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(blockColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.6f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(blockColor),
        )
    }
}
