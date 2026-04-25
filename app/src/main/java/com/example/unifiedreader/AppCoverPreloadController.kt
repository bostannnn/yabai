package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.buildLibraryCoverPreloadTargets
import com.example.unifiedreader.feature.settings.CoverPreloadUiState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppCoverPreloadController @Inject constructor(
    private val remoteCoverPreloader: RemoteCoverPreloader,
    private val remoteCoverController: RemoteCoverController,
) {
    private val activeJobsBySource = mutableMapOf<SourceType, Job>()

    fun preloadCovers(
        sourceType: SourceType,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        if (activeJobsBySource[sourceType]?.isActive == true) return
        val state = stateProvider()
        val targets = buildLibraryCoverPreloadTargets(
            libraries = state.libraries,
            items = state.items,
            sourceType = sourceType,
        )
        if (targets.isEmpty()) {
            updatePreloadState(updateState, sourceType) {
                CoverPreloadUiState(
                    message = "No remote covers to preload.",
                    isError = false,
                )
            }
            return
        }
        updatePreloadState(updateState, sourceType) {
            CoverPreloadUiState(
                isRunning = true,
                progressFraction = 0f,
                progressLabel = "Preparing cover preload...",
                message = "Preloading covers.",
                isError = false,
            )
        }
        val job = scope.launch {
            runCatching {
                remoteCoverPreloader.preload(
                    sourceType = sourceType,
                    items = targets,
                    onProgress = { progress ->
                        updatePreloadState(updateState, sourceType) {
                            CoverPreloadUiState(
                                isRunning = true,
                                progressFraction = progress.fraction.coerceIn(0f, 1f),
                                progressLabel = progress.label,
                                message = "Preloading covers.",
                                isError = false,
                            )
                        }
                    },
                )
            }.onSuccess { result ->
                remoteCoverController.clearSource(sourceType)
                updatePreloadState(updateState, sourceType) {
                    CoverPreloadUiState(
                        isRunning = false,
                        progressFraction = 1f,
                        progressLabel = null,
                        message = result.message,
                        isError = result.failedCount > 0,
                    )
                }
            }.onFailure { throwable ->
                updatePreloadState(updateState, sourceType) {
                    CoverPreloadUiState(
                        isRunning = false,
                        progressFraction = null,
                        progressLabel = null,
                        message = "Unable to preload covers: ${throwable.userFacingMessage()}",
                        isError = true,
                    )
                }
            }
        }
        activeJobsBySource[sourceType] = job
        job.invokeOnCompletion {
            if (activeJobsBySource[sourceType] === job) {
                activeJobsBySource.remove(sourceType)
            }
        }
    }

    private fun updatePreloadState(
        updateState: AppUiStateUpdater,
        sourceType: SourceType,
        preloadState: () -> CoverPreloadUiState,
    ) {
        updateState { state ->
            when (sourceType) {
                SourceType.KAVITA -> state.copy(
                    kavitaConnection = state.kavitaConnection.copy(coverPreload = preloadState()),
                )
                SourceType.STUMP -> state.copy(
                    stumpConnection = state.stumpConnection.copy(coverPreload = preloadState()),
                )
                SourceType.GRIMMORY -> state.copy(
                    grimmoryConnection = state.grimmoryConnection.copy(coverPreload = preloadState()),
                )
                SourceType.LOCAL -> state
            }
        }
    }
}
