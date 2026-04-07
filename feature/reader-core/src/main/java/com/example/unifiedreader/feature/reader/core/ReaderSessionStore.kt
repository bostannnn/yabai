package com.example.unifiedreader.feature.reader.core

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import kotlinx.coroutines.flow.StateFlow

data class ReaderProgressSnapshot(
    val itemId: String,
    val locatorType: LocatorType,
    val page: Int? = null,
    val cfi: String? = null,
    val percent: Double? = null,
    val isFinished: Boolean = false,
    val syncState: SyncState = SyncState.CLEAN,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

data class ReaderSessionSnapshot(
    val itemId: String,
    val pageCount: Int? = null,
    val preferences: Map<String, String> = emptyMap(),
)

data class ReaderLocator(
    val locatorType: LocatorType,
    val page: Int? = null,
    val cfi: String? = null,
    val percent: Double? = null,
)

interface ReaderSessionStore {
    val isHydratedFlow: StateFlow<Boolean>

    val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>>

    val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>>

    fun getProgress(item: ReadableItem): ReaderProgressSnapshot?

    fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean = false,
        syncState: SyncState = SyncState.CLEAN,
        pageCount: Int? = null,
    )

    fun getSession(item: ReadableItem): ReaderSessionSnapshot?

    fun updateSession(
        item: ReadableItem,
        pageCount: Int? = null,
        preferences: Map<String, String> = emptyMap(),
    )

    fun clearItem(itemId: String)
}
