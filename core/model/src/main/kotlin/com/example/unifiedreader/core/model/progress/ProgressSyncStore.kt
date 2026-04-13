package com.example.unifiedreader.core.model.progress

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import kotlinx.coroutines.flow.Flow

data class ProgressSyncSnapshot(
    val itemId: String,
    val locatorType: LocatorType,
    val page: Int? = null,
    val cfi: String? = null,
    val percent: Double? = null,
    val isFinished: Boolean = false,
    val isExplicitUnreadReset: Boolean = false,
    val syncState: SyncState = SyncState.CLEAN,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

data class ProgressSyncLocator(
    val locatorType: LocatorType,
    val page: Int? = null,
    val cfi: String? = null,
    val percent: Double? = null,
)

interface ProgressSyncStore {
    val isHydratedFlow: Flow<Boolean>

    val progressByItemIdFlow: Flow<Map<String, ProgressSyncSnapshot>>

    fun currentProgressByItemId(): Map<String, ProgressSyncSnapshot>

    fun getProgress(
        item: ReadableItem,
    ): ProgressSyncSnapshot?

    fun saveProgress(
        item: ReadableItem,
        locator: ProgressSyncLocator,
        isFinished: Boolean = false,
        isExplicitUnreadReset: Boolean = false,
        syncState: SyncState = SyncState.CLEAN,
        pageCount: Int? = null,
        updatedAtEpochMillis: Long = System.currentTimeMillis(),
    )

    suspend fun replaceProgressIfCurrent(
        expected: ProgressSyncSnapshot,
        replacement: ProgressSyncSnapshot?,
    ): Boolean

    fun clearProgress(
        itemId: String,
    )

    fun clearItem(
        itemId: String,
    )
}
