package com.example.unifiedreader

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.unifiedreader.feature.downloads.DownloadCompletionSideEffect
import com.example.unifiedreader.feature.downloads.DownloadWorkScheduler
import com.example.unifiedreader.feature.downloads.RemoteDownloadWorkerOutcome
import com.example.unifiedreader.feature.downloads.RemoteDownloadWorkerRunner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class WorkManagerDownloadScheduler(
    private val workManager: WorkManager,
) : DownloadWorkScheduler {
    override fun scheduleEntry(
        entryId: String,
    ) {
        workManager.enqueueUniqueWork(
            uniqueWorkName(entryId),
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RemoteDownloadWorker>()
                .setInputData(workDataOf(RemoteDownloadWorker.EntryIdKey to entryId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build(),
        )
    }

    override fun cancelEntry(
        entryId: String,
    ) {
        workManager.cancelUniqueWork(uniqueWorkName(entryId))
    }
}

class SourceProgressSyncDownloadCompletionSideEffect(
    private val sourceProgressSyncs: List<SourceProgressSync>,
) : DownloadCompletionSideEffect {
    override fun supports(
        item: com.example.unifiedreader.core.model.ReadableItem,
    ): Boolean = sourceProgressSyncs.any { sync -> sync.supports(item) }

    override suspend fun onDownloadCompleted(
        item: com.example.unifiedreader.core.model.ReadableItem,
    ) {
        sourceProgressSyncs.firstOrNull { sync -> sync.supports(item) }
            ?.syncItem(item)
    }
}

class RemoteDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryId = inputData.getString(EntryIdKey) ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RemoteDownloadWorkerEntryPoint::class.java,
        )
        return when (entryPoint.remoteDownloadWorkerRunner().runEntry(entryId)) {
            RemoteDownloadWorkerOutcome.Success,
            RemoteDownloadWorkerOutcome.Canceled -> Result.success()

            RemoteDownloadWorkerOutcome.Failure -> Result.failure()
        }
    }

    companion object {
        const val EntryIdKey = "entry_id"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RemoteDownloadWorkerEntryPoint {
    fun remoteDownloadWorkerRunner(): RemoteDownloadWorkerRunner
}

private fun uniqueWorkName(
    entryId: String,
): String = "remote-download-entry-$entryId"
