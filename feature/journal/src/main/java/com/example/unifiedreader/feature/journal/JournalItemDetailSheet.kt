package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalItemDetailSheet(
    item: JournalItemRef,
    events: List<JournalReadingEvent>,
    entry: ItemJournalEntry?,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
    onRequestDeleteEvent: (String) -> Unit,
    onDeleteItemJournal: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    JournalCoverArt(
                        item = item,
                        modifier = Modifier
                            .width(92.dp)
                            .aspectRatio(0.68f),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.displayTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        item.displaySubtitle?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Text(
                            text = "${item.sourceType.name.displayName()} · ${item.format.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry?.rating != null || !entry?.reviewText.isNullOrBlank()) {
                            Text(
                                text = "${entry?.rating?.stars().orEmpty()} ${entry?.reviewText.orEmpty()}".trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onReview) {
                                Icon(imageVector = Icons.Default.EditNote, contentDescription = null)
                                Text(text = "Review", modifier = Modifier.padding(start = 8.dp))
                            }
                            TextButton(onClick = onDeleteItemJournal) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                                Text(text = "Remove journal", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = "Reading timeline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        text = "No reading activity recorded for this item.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(events.sortedByDescending(JournalReadingEvent::occurredAtEpochMillis), key = JournalReadingEvent::id) { event ->
                JournalItemEventRow(
                    event = event,
                    onDelete = { onRequestDeleteEvent(event.id) },
                )
            }
        }
    }
}

@Composable
private fun JournalItemEventRow(
    event: JournalReadingEvent,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = event.eventType.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = event.progressLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatDateTime(event.occurredAtEpochMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove timeline entry")
        }
    }
}
