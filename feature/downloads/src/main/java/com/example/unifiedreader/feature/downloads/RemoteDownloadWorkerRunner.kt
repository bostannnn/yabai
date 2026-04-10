package com.example.unifiedreader.feature.downloads

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class RemoteDownloadWorkerOutcome {
    Success,
    Failure,
    Canceled,
}

class RemoteDownloadWorkerRunner(
    private val store: DownloadQueueStore,
    private val remoteDownloadDataSources: List<RemoteItemDownloadDataSource>,
    private val completionSideEffects: List<DownloadCompletionSideEffect>,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    suspend fun runEntry(
        entryId: String,
    ): RemoteDownloadWorkerOutcome {
        val entry = store.getEntry(entryId) ?: return RemoteDownloadWorkerOutcome.Success
        if (entry.status == DownloadEntryStatus.Completed || entry.status == DownloadEntryStatus.Canceled) {
            return RemoteDownloadWorkerOutcome.Success
        }
        if (entry.status == DownloadEntryStatus.Canceling) {
            return finalizeCanceled(entryId)
        }
        return try {
            val transitionedToRunning = store.updateStatusIfCurrentStatusIn(
                entryId = entryId,
                expectedStatuses = setOf(entry.status),
                status = DownloadEntryStatus.Running,
            )
            if (!transitionedToRunning) {
                return resolveStoppedOutcome(entryId)
            }
            var completedItemIds = entry.completedItemIds.toMutableSet()
            entry.actionItems.forEach { actionItem ->
                val currentStatus = store.getEntry(entryId)?.status
                if (currentStatus == DownloadEntryStatus.Canceled || currentStatus == DownloadEntryStatus.Canceling) {
                    return finalizeCanceled(entryId)
                }
                if (actionItem.id in completedItemIds) return@forEach
                val readableItem = actionItem.toReadableItem()
                store.updateProgress(
                    entryId = entryId,
                    completedItemIds = completedItemIds,
                    currentItemId = readableItem.id,
                    currentItemTitle = readableItem.title,
                )
                val dataSource = remoteDownloadDataSources.downloadDataSourceFor(readableItem)
                    ?: error("Offline download is not supported for ${readableItem.title}.")
                dataSource.downloadToLocalStorage(readableItem)
                    ?: error("Unable to download ${readableItem.title}.")
                completedItemIds += readableItem.id
                store.updateProgress(
                    entryId = entryId,
                    completedItemIds = completedItemIds,
                    currentItemId = readableItem.id,
                    currentItemTitle = readableItem.title,
                )
                completionSideEffects.forEach { effect ->
                    if (effect.supports(readableItem)) {
                        try {
                            effect.onDownloadCompleted(readableItem)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            Log.w(LOG_TAG, "Post-download side effect failed for ${readableItem.id}.", throwable)
                        }
                    }
                }
            }
            val transitionedToCompleted = store.updateStatusIfCurrentStatusIn(
                entryId = entryId,
                expectedStatuses = setOf(DownloadEntryStatus.Running),
                status = DownloadEntryStatus.Completed,
                completedAtEpochMillis = timeProvider(),
            )
            if (transitionedToCompleted) {
                RemoteDownloadWorkerOutcome.Success
            } else {
                resolveStoppedOutcome(entryId)
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                finalizeCanceled(entryId)
            }
            RemoteDownloadWorkerOutcome.Canceled
        } catch (throwable: Throwable) {
            store.updateStatus(
                entryId = entryId,
                status = DownloadEntryStatus.Failed,
                errorMessage = throwable.message?.takeIf(String::isNotBlank) ?: "Unknown error",
            )
            RemoteDownloadWorkerOutcome.Failure
        }
    }

    private suspend fun resolveStoppedOutcome(
        entryId: String,
    ): RemoteDownloadWorkerOutcome = when (store.getEntry(entryId)?.status) {
        DownloadEntryStatus.Canceling -> finalizeCanceled(entryId)
        DownloadEntryStatus.Canceled -> RemoteDownloadWorkerOutcome.Canceled
        DownloadEntryStatus.Failed -> RemoteDownloadWorkerOutcome.Failure
        else -> RemoteDownloadWorkerOutcome.Success
    }

    private suspend fun finalizeCanceled(
        entryId: String,
    ): RemoteDownloadWorkerOutcome {
        val currentStatus = store.getEntry(entryId)?.status
        if (currentStatus != DownloadEntryStatus.Canceled) {
            store.updateStatusIfCurrentStatusIn(
                entryId = entryId,
                expectedStatuses = setOf(
                    DownloadEntryStatus.Queued,
                    DownloadEntryStatus.Running,
                    DownloadEntryStatus.Canceling,
                ),
                status = DownloadEntryStatus.Canceled,
                completedAtEpochMillis = timeProvider(),
            )
        }
        return RemoteDownloadWorkerOutcome.Canceled
    }

    private companion object {
        const val LOG_TAG = "RemoteDownloadWorker"
    }
}
