package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun LibraryCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    AppBadge(modifier = modifier) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = semanticColors.onSurfaceSelected,
            maxLines = 1,
        )

    }
}
