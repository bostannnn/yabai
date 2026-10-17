package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun LibraryReadStatusBadge(
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    AppBadge(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(presentation.chipCornerRadius),
        color = semanticColors.readerAccent.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = semanticColors.onReaderAccent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Read",
                style = MaterialTheme.typography.labelSmall,
                color = semanticColors.onReaderAccent,
            )
        }
    }
}
