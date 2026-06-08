package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.buildLibraryCoverPreloadTargets
import com.example.unifiedreader.feature.settings.CoverPreloadFailureUiItem
import com.example.unifiedreader.feature.settings.CoverPreloadUiState
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppCoverPreloadController @Inject constructor(
    private val remoteCoverPreloader: RemoteCoverPreloader,
    private val remoteCoverController: RemoteCoverController,
) {
    private val activeJobsBySource = mutableMapOf<SourceType, Job>()
    private val activeClearCacheJobsBySource = mutableMapOf<SourceType, Job>()
    private val activeProgressBySource = mutableMapOf<SourceType, RemoteCoverPreloadProgress>()
    private val failedTargetsBySource = mutableMapOf<SourceType, List<RemoteCoverPreloadTarget>>()
    private val cancellingSources = mutableSetOf<SourceType>()

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
        startPreload(
            sourceType = sourceType,
            items = targets,
            retryTargets = null,
            updateState = updateState,
            scope = scope,
        )
    }

    fun retryFailedCovers(
        sourceType: SourceType,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        val failedTargets = failedTargetsBySource[sourceType].orEmpty()
        if (failedTargets.isEmpty()) return
        startPreload(
            sourceType = sourceType,
            items = emptyList(),
            retryTargets = failedTargets,
            updateState = updateState,
            scope = scope,
        )
    }

    fun cancelPreload(
        sourceType: SourceType,
        updateState: AppUiStateUpdater,
    ) {
        val activeJob = activeJobsBySource[sourceType]?.takeIf(Job::isActive) ?: return
        cancellingSources += sourceType
        activeJob.cancel()
        val progress = activeProgressBySource[sourceType]
        updatePreloadState(updateState, sourceType) {
            CoverPreloadUiState(
                isRunning = true,
                progressFraction = progress?.fraction?.coerceIn(0f, 1f),
                progressLabel = null,
                message = if (progress == null) {
                    "Cancelling cover preload..."
                } else {
                    "Cancelling cover preload at ${progress.processedCount} of ${progress.totalCount} covers."
                },
                isError = false,
                failedItems = failedTargetsBySource[sourceType].orEmpty().toPendingFailureUiItems(),
            )
        }
    }

    fun clearCachedCovers(
        sourceType: SourceType,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        if (activeJobsBySource[sourceType]?.isActive == true) return
        if (activeClearCacheJobsBySource[sourceType]?.isActive == true) return
        updatePreloadState(updateState, sourceType) {
            CoverPreloadUiState(
                isClearingCache = true,
                message = "Removing cover cache.",
                isError = false,
            )
        }
        val job = scope.launch {
            runSuspendCatchingPreservingCancellation {
                remoteCoverController.resetSource(sourceType)
            }.onSuccess {
                failedTargetsBySource.remove(sourceType)
                updatePreloadState(updateState, sourceType) {
                    CoverPreloadUiState(
                        message = "Cover cache removed.",
                        isError = false,
                    )
                }
            }.onFailure { throwable ->
                updatePreloadState(updateState, sourceType) {
                    CoverPreloadUiState(
                        message = "Unable to remove cover cache: ${throwable.userFacingMessage()}",
                        isError = true,
                        failedItems = failedTargetsBySource[sourceType].orEmpty().toPendingFailureUiItems(),
                    )
                }
            }
        }
        activeClearCacheJobsBySource[sourceType] = job
        job.invokeOnCompletion {
            if (activeClearCacheJobsBySource[sourceType] === job) {
                activeClearCacheJobsBySource.remove(sourceType)
            }
        }
    }

    private fun startPreload(
        sourceType: SourceType,
        items: List<ReadableItem>,
        retryTargets: List<RemoteCoverPreloadTarget>?,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        if (activeJobsBySource[sourceType]?.isActive == true) return
        cancellingSources -= sourceType
        updatePreloadState(updateState, sourceType) {
            CoverPreloadUiState(
                isRunning = true,
                progressFraction = 0f,
                progressLabel = "Preparing cover preload...",
                message = "Preloading covers.",
                isError = false,
            )
        }
        activeProgressBySource.remove(sourceType)
        val job = scope.launch {
            runCatching {
                val onProgress: (RemoteCoverPreloadProgress) -> Unit = { progress ->
                    activeProgressBySource[sourceType] = progress
                    updatePreloadState(updateState, sourceType) {
                        CoverPreloadUiState(
                            isRunning = true,
                            progressFraction = progress.fraction.coerceIn(0f, 1f),
                            progressLabel = progress.label,
                            message = "Preloading covers.",
                            isError = false,
                        )
                    }
                }
                if (retryTargets == null) {
                    remoteCoverPreloader.preload(
                        sourceType = sourceType,
                        items = items,
                        onProgress = onProgress,
                    )
                } else {
                    remoteCoverPreloader.preloadTargets(
                        sourceType = sourceType,
                        targets = retryTargets,
                        onProgress = onProgress,
                    )
                }
            }.onSuccess { result ->
                failedTargetsBySource[sourceType] = result.failures.map { failure ->
                    RemoteCoverPreloadTarget(failure.item, failure.variant)
                }
                remoteCoverController.clearSource(sourceType)
                updatePreloadState(updateState, sourceType) {
                    CoverPreloadUiState(
                        isRunning = false,
                        progressFraction = 1f,
                        progressLabel = null,
                        message = result.message,
                        isError = result.failedCount > 0,
                        failedItems = result.failures.toFailureUiItems(),
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    val progress = activeProgressBySource[sourceType]
                    val userCancelled = sourceType in cancellingSources
                    updatePreloadState(updateState, sourceType) {
                        CoverPreloadUiState(
                            isRunning = false,
                            progressFraction = progress?.fraction?.coerceIn(0f, 1f),
                            progressLabel = null,
                            message = cancellationMessage(
                                progress = progress,
                                userCancelled = userCancelled,
                            ),
                            isError = !userCancelled,
                            failedItems = failedTargetsBySource[sourceType].orEmpty().toPendingFailureUiItems(),
                        )
                    }
                } else {
                    updatePreloadState(updateState, sourceType) {
                        CoverPreloadUiState(
                            isRunning = false,
                            progressFraction = null,
                            progressLabel = null,
                            message = "Unable to preload covers: ${throwable.userFacingMessage()}",
                            isError = true,
                            failedItems = failedTargetsBySource[sourceType].orEmpty().toPendingFailureUiItems(),
                        )
                    }
                }
            }
        }
        activeJobsBySource[sourceType] = job
        job.invokeOnCompletion {
            if (activeJobsBySource[sourceType] === job) {
                activeJobsBySource.remove(sourceType)
                activeProgressBySource.remove(sourceType)
                cancellingSources -= sourceType
            }
        }
    }

    private fun cancellationMessage(
        progress: RemoteCoverPreloadProgress?,
        userCancelled: Boolean,
    ): String {
        val prefix = if (userCancelled) {
            "Cover preload cancelled"
        } else {
            "Cover preload stopped unexpectedly"
        }
        return if (progress == null) {
            "$prefix."
        } else {
            "$prefix at ${progress.processedCount} of ${progress.totalCount} covers."
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

internal fun List<RemoteCoverPreloadFailure>.toFailureUiItems(): List<CoverPreloadFailureUiItem> =
    groupedFailuresByItemForPreloadUi().take(MAX_FAILURES_TO_SHOW).map { failures ->
        val failure = failures.first()
        CoverPreloadFailureUiItem(
            title = failure.item.coverPreloadFailureTitle(),
            variant = failures.map { preloadFailure -> preloadFailure.variant.name }
                .distinct()
                .coverVariantSummary(),
            reason = failures.map { preloadFailure -> preloadFailure.reason.readableCoverFailureReason() }
                .distinct()
                .joinToString(separator = "; "),
        )
    }

internal fun List<RemoteCoverPreloadTarget>.toPendingFailureUiItems(): List<CoverPreloadFailureUiItem> =
    groupedTargetsByItemForPreloadUi().take(MAX_FAILURES_TO_SHOW).map { targets ->
        val target = targets.first()
        CoverPreloadFailureUiItem(
            title = target.item.coverPreloadFailureTitle(),
            variant = targets.map { pendingTarget -> pendingTarget.variant.name }
                .distinct()
                .coverVariantSummary(),
            reason = "Retry pending.",
        )
    }

private fun List<RemoteCoverPreloadFailure>.groupedFailuresByItemForPreloadUi(): List<List<RemoteCoverPreloadFailure>> =
    groupBy { failure -> failure.item.id }.values.toList()

private fun List<RemoteCoverPreloadTarget>.groupedTargetsByItemForPreloadUi(): List<List<RemoteCoverPreloadTarget>> =
    groupBy { target -> target.item.id }.values.toList()

private fun ReadableItem.coverPreloadFailureTitle(): String {
    if (kind != ReadableItemKind.COMIC) return title
    val metadata = comicMetadata ?: return title
    val series = metadata.series?.trim()?.takeIf(String::isNotBlank)
    val issueNumberLabel = metadata.issueNumber
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { number -> "Issue $number" }
    val issueTitle = metadata.issueTitle
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { candidate -> series != null && candidate.equals(series, ignoreCase = true) }
        ?.takeUnless { candidate -> issueNumberLabel != null && candidate.equals(issueNumberLabel, ignoreCase = true) }
    return listOfNotNull(
        series,
        issueNumberLabel,
        issueTitle ?: title.trim().takeIf(String::isNotBlank),
    )
        .distinctBy { part -> part.lowercase() }
        .joinToString(" - ")
        .ifBlank { title }
}

private fun List<String>.coverVariantSummary(): String = when (size) {
    0 -> "cover"
    1 -> single()
    else -> {
        val preview = take(MAX_VARIANTS_TO_LIST).joinToString(separator = ", ")
        if (size > MAX_VARIANTS_TO_LIST) {
            "$size variants ($preview, ...)"
        } else {
            "$size variants ($preview)"
        }
    }
}

private fun String.readableCoverFailureReason(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return "Remote cover request failed."
    if (!trimmed.startsWith("{")) return trimmed
    val status = JsonStatusRegex.find(trimmed)?.groupValues?.getOrNull(1)
    val title = JsonTitleRegex.find(trimmed)?.groupValues?.getOrNull(1)
    return listOfNotNull(status, title)
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString(separator = " ")
        ?: "Remote cover request failed."
}

private val JsonStatusRegex = Regex("\"status\"\\s*:\\s*(\\d+)")
private val JsonTitleRegex = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"")
private const val MAX_VARIANTS_TO_LIST = 3
private const val MAX_FAILURES_TO_SHOW = 8
