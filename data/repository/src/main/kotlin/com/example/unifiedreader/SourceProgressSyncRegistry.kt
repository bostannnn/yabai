package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.repository.RemoteSyncArtifact
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards
import kotlin.coroutines.cancellation.CancellationException

data class SourceProgressSyncFailure(
    val sourceType: SourceType,
    val throwable: Throwable,
    val artifact: RemoteSyncArtifact? = null,
    val blocksDependentArtifacts: Boolean = false,
)

class SourceProgressSyncRegistry @Inject constructor(
    private val syncs: List<@JvmSuppressWildcards SourceProgressSync>,
) {
    fun syncFor(
        item: ReadableItem,
    ): SourceProgressSync? = syncs.firstOrNull { sync -> sync.supports(item) }

    suspend fun syncItem(
        item: ReadableItem,
    ): Result<Unit> = try {
        syncFor(item)?.syncItem(item)
        Result.success(Unit)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) {
            throw throwable
        }
        Result.failure(throwable)
    }

    suspend fun pushPendingProgress(
        item: ReadableItem,
    ): List<SourceProgressSyncFailure> = pushPendingProgress(listOf(item))

    suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        val failures = mutableListOf<SourceProgressSyncFailure>()
        syncs.forEach { sync ->
            try {
                failures += sync.pushPendingProgress(items)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                failures += SourceProgressSyncFailure(
                    sourceType = sync.sourceType,
                    throwable = throwable,
                    blocksDependentArtifacts = true,
                )
            }
        }
        return failures
    }
}
