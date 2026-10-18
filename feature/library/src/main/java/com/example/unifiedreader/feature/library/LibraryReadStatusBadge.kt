package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** A small accent circle with a check mark: the book is finished. */
@Composable
internal fun LibraryReadStatusBadge(
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    AppBadge(
        modifier = modifier.semantics { contentDescription = "Read" },
        shape = CircleShape,
        color = semanticColors.readerAccent,
    ) {

        Box(
            modifier = Modifier.size(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = semanticColors.onReaderAccent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
