package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.AppDialog
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppPanelDefaults

@Composable
fun ReaderCompletionDialog(
    nextItem: ReadableItem?,
    nextItemTitle: String?,
    nextItemSubtitle: String?,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    reviewContent: @Composable ColumnScope.() -> Unit,
    onStartNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Finished") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                reviewContent()
                if (nextItem != null) {
                    ReaderCompletionNextItemCard(
                        item = nextItem,
                        title = nextItemTitle ?: nextItem.title,
                        subtitle = nextItemSubtitle,
                        coverContent = coverContent,
                        onClick = onStartNext,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun ReaderCompletionNextItemCard(
    item: ReadableItem,
    title: String,
    subtitle: String?,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    onClick: () -> Unit,
) {
    AppPanel(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = AppPanelDefaults.outlinedContainerColor,
        border = AppPanelDefaults.outlinedBorder,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            coverContent(
                item,
                Modifier
                    .width(54.dp)
                    .aspectRatio(0.68f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Start next",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}
