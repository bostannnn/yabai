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
    val isExplicitUnreadReset: Boolean = false,
    val syncState: SyncState = SyncState.CLEAN,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
)

data class ReaderSessionSnapshot(
    val itemId: String,
    val pageCount: Int? = null,
    val preferences: Map<String, String> = emptyMap(),
)

/** Where the reader is among the book's chapters: chapter [index] (from 1) of [count]. */
data class ReaderChapterPosition(
    val index: Int,
    val count: Int,
)

/**
 * The session preference that keeps the [ReaderChapterPosition] the reader last saved, as
 * "index/count", so the library can name the chapter without opening the book. Empty when the
 * position is outside every chapter (a foreword) or the book has none.
 */
const val ReaderSessionChapterKey = "reader.chapter"

fun readerSessionChapterPreference(position: ReaderChapterPosition?): Pair<String, String> =
    ReaderSessionChapterKey to (position?.let { "${it.index}/${it.count}" } ?: "")

val ReaderSessionSnapshot.chapterPosition: ReaderChapterPosition?
    get() {
        val parts = preferences[ReaderSessionChapterKey]?.split('/') ?: return null
        if (parts.size != 2) return null
        val index = parts[0].toIntOrNull() ?: return null
        val count = parts[1].toIntOrNull() ?: return null
        return ReaderChapterPosition(index, count).takeIf { count > 0 && index in 1..count }
    }

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
        isExplicitUnreadReset: Boolean = false,
        syncState: SyncState = SyncState.CLEAN,
        pageCount: Int? = null,
        updatedAtEpochMillis: Long = System.currentTimeMillis(),
    )

    suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean

    suspend fun flushPendingWrites() = Unit

    /**
     * Schedules a flush of pending writes on a store-owned scope without blocking the caller.
     *
     * Unlike [flushPendingWrites], this never blocks the calling (e.g. main/composition) thread,
     * so it is safe to invoke from reader close/stop/dispose paths.
     */
    fun requestFlushPendingWrites() = Unit

    fun getSession(item: ReadableItem): ReaderSessionSnapshot?

    fun updateSession(
        itemId: String,
        pageCount: Int? = null,
        preferences: Map<String, String> = emptyMap(),
    )

    fun updateSession(
        item: ReadableItem,
        pageCount: Int? = null,
        preferences: Map<String, String> = emptyMap(),
    ) = updateSession(
        itemId = item.id,
        pageCount = pageCount,
        preferences = preferences,
    )

    fun replaceSession(
        itemId: String,
        replacement: ReaderSessionSnapshot?,
    ) = Unit

    fun clearProgress(itemId: String)

    fun clearItem(itemId: String)

    fun restoreItem(itemId: String)
}
