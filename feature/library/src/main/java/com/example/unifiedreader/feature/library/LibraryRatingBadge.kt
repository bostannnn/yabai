package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
fun RatingBadge(
    state: LibraryRatingBadgeState,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    AppBadge(
        modifier = modifier.semantics {
            contentDescription = state.contentDescription
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(presentation.chipCornerRadius),
        color = semanticColors.syncAccent.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = semanticColors.onSyncAccent,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = state.label,
                style = MaterialTheme.typography.labelSmall,
                color = semanticColors.onSyncAccent,
            )
        }
    }
}

@Composable
internal fun LibraryRatingBadge(
    state: LibraryRatingBadgeState,
    modifier: Modifier = Modifier,
) {
    RatingBadge(
        state = state,
        modifier = modifier,
    )
}
