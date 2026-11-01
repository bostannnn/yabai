package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.journal.JournalSnapshot
import kotlinx.coroutines.delay

/**
 * The Journal tab's full snapshot ([AppViewModel.journalFullSnapshotFlow]), collected by the shell
 * so the tab opens on a finished snapshot instead of building it first: from a moment after the
 * library has loaded (never in the way of a cold start), and at once while the Journal is shown.
 * Null until then. Read its value only where the Journal draws, so a new snapshot redraws just that.
 */
@Composable
internal fun rememberWarmJournalSnapshot(
    viewModel: AppViewModel,
    uiState: AppUiState,
): State<JournalSnapshot?>? {
    var isWarm by remember { mutableStateOf(false) }
    val isLibraryLoaded = uiState.hydratedSources.isNotEmpty()
    LaunchedEffect(isLibraryLoaded) {
        if (!isLibraryLoaded || isWarm) return@LaunchedEffect
        delay(JOURNAL_WARMUP_DELAY_MILLIS)
        isWarm = true
    }
    return if (isWarm || uiState.topLevel == TopLevelDestination.Journal) {
        viewModel.journalFullSnapshotFlow.collectAsStateWithLifecycle()
    } else {
        null
    }
}

/** After the library loads, so the snapshot doesn't compete with its first recompute. */
private const val JOURNAL_WARMUP_DELAY_MILLIS = 1_500L
