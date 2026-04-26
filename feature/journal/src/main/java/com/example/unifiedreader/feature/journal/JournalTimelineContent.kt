package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun JournalTimelineContent(
    snapshot: JournalSnapshot,
    onRequestDeleteEvent: (String) -> Unit,
    onDeleteItemJournal: (String) -> Unit,
    onSaveJournalEntry: (JournalItemRef, Int?, String) -> Unit,
    onSaveGoal: (ReadingGoalPeriod, ReadingGoalMetric, Int) -> Unit,
    onDeleteGoal: (String) -> Unit,
) {
    var editTarget by remember { mutableStateOf<JournalItemRef?>(null) }
    var detailTarget by remember { mutableStateOf<JournalItemRef?>(null) }
    var goalEditorOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item {
            TimelineGoalHeader(
                snapshot = snapshot,
                onAddGoal = { goalEditorOpen = true },
                onDeleteGoal = onDeleteGoal,
            )
        }
        if (snapshot.itemSummaries.isEmpty()) {
            item { EmptyJournalMessage("Reading activity from this app will appear here.") }
        }
        items(snapshot.itemSummaries, key = { summary -> summary.item.itemId }) { summary ->
            JournalSummaryCard(
                summary = summary,
                onOpen = { detailTarget = summary.item },
                onReview = { editTarget = summary.item },
                onDelete = { onDeleteItemJournal(summary.item.itemId) },
            )
        }
    }
    if (goalEditorOpen) {
        GoalEditorDialog(
            onDismiss = { goalEditorOpen = false },
            onSave = { period, metric, target ->
                onSaveGoal(period, metric, target)
                goalEditorOpen = false
            },
        )
    }
    editTarget?.let { item ->
        JournalEntryEditorSheet(
            item = item,
            existingEntry = snapshot.entryByItemId[item.itemId],
            onDismiss = { editTarget = null },
            onSave = { rating, review ->
                onSaveJournalEntry(item, rating, review)
                editTarget = null
            },
        )
    }
    detailTarget?.let { item ->
        JournalItemDetailSheet(
            item = item,
            events = snapshot.events.filter { event -> event.itemId == item.itemId },
            entry = snapshot.entryByItemId[item.itemId],
            onDismiss = { detailTarget = null },
            onReview = {
                detailTarget = null
                editTarget = item
            },
            onRequestDeleteEvent = onRequestDeleteEvent,
            onDeleteItemJournal = {
                onDeleteItemJournal(item.itemId)
                detailTarget = null
            },
        )
    }
}
