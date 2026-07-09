package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.feature.library.LibraryDerivedState
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AppLibraryPendingRecompute(
    private val appDispatchers: AppDispatchers,
    private val computeDerivedState: (AppUiState) -> LibraryDerivedState,
    private val computeShelvesState: (AppUiState) -> LibraryShelvesState,
) {
    private var pendingDerivedKey: Any? = null
    private var pendingShelvesKey: Any? = null
    private var pendingJob: Job? = null

    fun cancel() {
        pendingJob?.cancel()
        pendingJob = null
        pendingDerivedKey = null
        pendingShelvesKey = null
    }

    fun schedule(
        baseState: AppUiState,
        derivedKey: Any,
        shelvesKey: Any,
        shouldComputeDerived: Boolean,
        shouldComputeShelves: Boolean,
        scope: CoroutineScope,
        onComputed: (AppLibraryComputedState) -> Unit,
    ) {
        val nextDerivedKey = derivedKey.takeIf { shouldComputeDerived }
        val nextShelvesKey = shelvesKey.takeIf { shouldComputeShelves }
        if (pendingDerivedKey == nextDerivedKey && pendingShelvesKey == nextShelvesKey) {
            return
        }

        pendingJob?.cancel()
        pendingDerivedKey = nextDerivedKey
        pendingShelvesKey = nextShelvesKey
        var recomputeJob: Job? = null
        recomputeJob = scope.launch(start = CoroutineStart.LAZY) {
            val recomputeStart = System.nanoTime()
            val computedState = withContext(appDispatchers.default) {
                AppLibraryComputedState(
                    derivedState = if (shouldComputeDerived) {
                        computeDerivedState(baseState)
                    } else {
                        null
                    },
                    shelvesState = if (shouldComputeShelves) {
                        computeShelvesState(baseState)
                    } else {
                        null
                    },
                )
            }
            if (pendingJob !== recomputeJob) return@launch
            // Opt-in end-to-end timing (see LibraryPerfLog): `adb logcat -s LibraryPerf`.
            val recomputeMs = (System.nanoTime() - recomputeStart) / 1_000_000.0
            runCatching {
                android.util.Log.d(
                    "LibraryPerf",
                    "recompute.async total: %.2f ms (derived=%b shelves=%b)".format(recomputeMs, shouldComputeDerived, shouldComputeShelves),
                )
            }
            pendingJob = null
            pendingDerivedKey = null
            pendingShelvesKey = null
            onComputed(computedState)
        }
        pendingJob = recomputeJob
        recomputeJob.start()
    }
}

internal data class AppLibraryComputedState(
    val derivedState: LibraryDerivedState?,
    val shelvesState: LibraryShelvesState?,
)
