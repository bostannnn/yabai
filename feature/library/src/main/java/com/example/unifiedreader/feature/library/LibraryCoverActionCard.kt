package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem

@Composable
fun LibraryCoverActionCard(
    item: ReadableItem,
    title: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    imageVariant: LibraryCoverImageVariant,
    isDownloaded: Boolean,
    isRead: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier.fillMaxWidth(),
    titleStyle: TextStyle = MaterialTheme.typography.labelMedium,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingText: String? = null,
    supportingStyle: TextStyle = MaterialTheme.typography.bodySmall,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingText: String? = null,
    trailingStyle: TextStyle = MaterialTheme.typography.labelSmall,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    metadataTopPadding: Dp = 6.dp,
    metadataVerticalSpacing: Dp = 3.dp,
    menuContent: (@Composable () -> Unit)? = null,
    coverOverlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            ResolvedLibraryCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                isDownloaded = isDownloaded,
                isRead = isRead,
                imageVariant = imageVariant,
                modifier = coverModifier,
            )
            coverOverlay?.invoke(this)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = metadataTopPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(metadataVerticalSpacing),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.clickable(onClick = onClick),
                    style = titleStyle,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supportingText?.let {
                    Text(
                        text = it,
                        style = supportingStyle,
                        color = supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                trailingText?.let {
                    Text(
                        text = it,
                        style = trailingStyle,
                        color = trailingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (menuContent != null) {
                menuContent()
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }
        }
    }
}
