package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.downloads.downloadDataSourceFor
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSuppressWildcards

class RemoteReaderContentPreparer @Inject constructor(
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    suspend fun prepare(
        item: ReadableItem,
    ): RemoteReaderContentPreparationResult {
        val dataSource = remoteDownloadDataSources.downloadDataSourceFor(item)
            ?: return RemoteReaderContentPreparationResult.Failure(
                SourceProgressSyncFailure(
                    sourceType = item.sourceType,
                    throwable = IllegalStateException("Offline download is not supported for ${item.title}."),
                ),
            )
        return runCatching {
            dataSource.prepareDocumentForReading(item)
                ?: error("Unable to prepare ${item.title} for reading.")
        }.fold(
            onSuccess = { result ->
                if (result.downloadedFromNetwork) {
                    remoteSourceConnectivityPolicy.recordReaderContentSuccess(item.sourceType)
                }
                RemoteReaderContentPreparationResult.Ready
            },
            onFailure = { throwable ->
                if (throwable is CancellationException) throw throwable
                remoteSourceConnectivityPolicy.recordReaderContentFailure(
                    sourceType = item.sourceType,
                    throwable = throwable,
                )
                RemoteReaderContentPreparationResult.Failure(
                    SourceProgressSyncFailure(
                        sourceType = item.sourceType,
                        throwable = throwable,
                    ),
                )
            },
        )
    }
}

sealed interface RemoteReaderContentPreparationResult {
    data object Ready : RemoteReaderContentPreparationResult
    data class Failure(
        val failure: SourceProgressSyncFailure,
    ) : RemoteReaderContentPreparationResult
}
