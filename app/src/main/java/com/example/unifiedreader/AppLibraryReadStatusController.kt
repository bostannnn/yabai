package com.example.unifiedreader
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.supportsManualReadStatusAction
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards

class AppLibraryReadStatusController @Inject constructor(
    private val readerSessionStore: ReaderSessionStore,
    private val sourceProgressSyncs: List<@JvmSuppressWildcards SourceProgressSync>,
) {
    suspend fun setReadStatus(
        item: ReadableItem,
        markAsRead: Boolean,
        onProgressChanged: () -> Unit,
    ) {
        if (!item.supportsManualReadStatusAction()) return
        applyLocalReadStatus(
            item = item,
            markAsRead = markAsRead,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        onProgressChanged()
        runCatching {
            sourceProgressSyncs.firstOrNull { candidate -> candidate.supports(item) }?.pushPendingProgress(
                items = listOf(item),
            )
        }
        onProgressChanged()
    }

    private fun applyLocalReadStatus(
        item: ReadableItem,
        markAsRead: Boolean,
        updatedAtEpochMillis: Long,
    ) {
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PERCENT,
                percent = if (markAsRead) 1.0 else 0.0,
            ),
            isFinished = markAsRead,
            isExplicitUnreadReset = !markAsRead,
            syncState = SyncState.DIRTY,
            pageCount = item.pageCount,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}
