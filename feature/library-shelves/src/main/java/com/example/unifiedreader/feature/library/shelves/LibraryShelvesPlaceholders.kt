package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun ShelfPreviewPlaceholderRow(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(ShelfPreviewPlaceholderCount) {
            Column(
                modifier = Modifier.width(ShelfPreviewPlaceholderWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(ShelfPreviewPlaceholderWidth)
                        .aspectRatio(ShelfPreviewPlaceholderAspectRatio)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Box(
                    modifier = Modifier
                        .width(94.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .width(72.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }
}

private const val ShelfPreviewPlaceholderCount = 3
private val ShelfPreviewPlaceholderWidth = 132.dp
private const val ShelfPreviewPlaceholderAspectRatio = 0.72f
