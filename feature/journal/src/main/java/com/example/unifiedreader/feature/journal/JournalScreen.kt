package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class JournalTab(
    val label: String,
) {
    Timeline("Timeline"),
    Stats("Stats"),
}

@Composable
fun JournalScreen(
    snapshot: JournalSnapshot,
    onDeleteEvent: (String) -> Unit,
    onDeleteItemJournal: (String) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    onSaveGoal: (ReadingGoalPeriod, ReadingGoalMetric, Int) -> Unit,
    onDeleteGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(JournalTab.Timeline) }
    var deleteTarget by remember { mutableStateOf<JournalDeleteTarget?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            JournalTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = JournalTab.entries.size,
                    ),
                ) {
                    Text(text = tab.label)
                }
            }
        }
        when (selectedTab) {
            JournalTab.Timeline -> JournalTimelineContent(
                snapshot = snapshot,
                onRequestDeleteEvent = { eventId -> deleteTarget = JournalDeleteTarget.Event(eventId) },
                onDeleteItemJournal = { itemId -> deleteTarget = JournalDeleteTarget.Item(itemId) },
                onSaveJournalEntry = onSaveJournalEntry,
                onSaveGoal = onSaveGoal,
                onDeleteGoal = { goalId -> deleteTarget = JournalDeleteTarget.Goal(goalId) },
            )
            JournalTab.Stats -> JournalStatsContent(snapshot = snapshot)
        }
    }
    deleteTarget?.let { target ->
        JournalDeleteConfirmationDialog(
            target = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                when (target) {
                    is JournalDeleteTarget.Event -> onDeleteEvent(target.eventId)
                    is JournalDeleteTarget.Goal -> onDeleteGoal(target.goalId)
                    is JournalDeleteTarget.Item -> onDeleteItemJournal(target.itemId)
                }
                deleteTarget = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalEntryEditorSheet(
    item: JournalItemRef,
    existingEntry: ItemJournalEntry?,
    onDismiss: () -> Unit,
    onSave: (Int?, String) -> Unit,
) {
    var selectedRating by rememberSaveable(item.itemId) { mutableStateOf(existingEntry?.rating) }
    var reviewText by rememberSaveable(item.itemId) { mutableStateOf(existingEntry?.reviewText.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = item.displayTitle, style = MaterialTheme.typography.titleLarge)
            item.displaySubtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { rating ->
                    FilterChip(
                        selected = selectedRating == rating,
                        onClick = { selectedRating = rating },
                        label = { Text(text = rating.toString()) },
                    )
                }
            }
            OutlinedTextField(
                value = reviewText,
                onValueChange = { reviewText = it },
                label = { Text(text = "Private review") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
                Button(onClick = { onSave(selectedRating, reviewText) }) {
                    Text(text = "Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalEditorDialog(
    onDismiss: () -> Unit,
    onSave: (ReadingGoalPeriod, ReadingGoalMetric, Int) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(ReadingGoalPeriod.MONTH) }
    var metric by rememberSaveable { mutableStateOf(ReadingGoalMetric.BOOKS_FINISHED) }
    var targetText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Reading goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalDropdown(
                    label = "Period",
                    value = period.label,
                    entries = ReadingGoalPeriod.entries,
                    entryLabel = ReadingGoalPeriod::label,
                    onSelected = { period = it },
                )
                GoalDropdown(
                    label = "Metric",
                    value = metric.label,
                    entries = ReadingGoalMetric.entries,
                    entryLabel = ReadingGoalMetric::label,
                    onSelected = { metric = it },
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter(Char::isDigit) },
                    label = { Text(text = "Target") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(period, metric, targetText.toIntOrNull() ?: 0)
                },
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> GoalDropdown(
    label: String,
    value: String,
    entries: List<T>,
    entryLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(text = entryLabel(entry)) },
                    onClick = {
                        expanded = false
                        onSelected(entry)
                    },
                )
            }
        }
    }
}

@Composable
internal fun EmptyJournalMessage(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun JournalDeleteConfirmationDialog(
    target: JournalDeleteTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = target.title) },
        text = { Text(text = target.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

private sealed interface JournalDeleteTarget {
    val title: String
    val body: String

    data class Event(
        val eventId: String,
    ) : JournalDeleteTarget {
        override val title: String = "Remove timeline entry?"
        override val body: String = "This removes only this reading event from the Journal."
    }

    data class Item(
        val itemId: String,
    ) : JournalDeleteTarget {
        override val title: String = "Remove item journal?"
        override val body: String = "This removes the item reading history, rating, and private review from the Journal."
    }

    data class Goal(
        val goalId: String,
    ) : JournalDeleteTarget {
        override val title: String = "Remove goal?"
        override val body: String = "This removes the goal. Reading history and reviews stay untouched."
    }
}
