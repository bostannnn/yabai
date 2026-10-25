package com.example.unifiedreader.feature.reader.core

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import java.util.Locale
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

/**
 * Where the reader is among the book's chapters: chapter [index] (from 1) of [count], its
 * [title], and where each chapter starts as a share of the book ([starts], one per chapter,
 * rising; empty when the book's positions don't say).
 */
data class ReaderChapterPosition(
    val index: Int,
    val count: Int,
    val title: String? = null,
    val starts: List<Float> = emptyList(),
)

/**
 * The session preferences that keep the [ReaderChapterPosition] the reader last saved, so the
 * library can name the chapter without opening the book: "index/count", the title, and the
 * starts as "0.0000,0.1250,…". Empty when the position is outside every chapter (a foreword) or
 * the book has none.
 */
const val ReaderSessionChapterKey = "reader.chapter"
const val ReaderSessionChapterTitleKey = "reader.chapterTitle"
const val ReaderSessionChapterStartsKey = "reader.chapterStarts"

fun readerSessionChapterPreferences(position: ReaderChapterPosition?): Map<String, String> = mapOf(
    ReaderSessionChapterKey to (position?.let { "${it.index}/${it.count}" } ?: ""),
    ReaderSessionChapterTitleKey to position?.title.orEmpty(),
    ReaderSessionChapterStartsKey to position?.starts.orEmpty().joinToString(",") { start -> "%.4f".format(Locale.US, start) },
)

val ReaderSessionSnapshot.chapterPosition: ReaderChapterPosition?
    get() {
        val parts = preferences[ReaderSessionChapterKey]?.split('/') ?: return null
        if (parts.size != 2) return null
        val index = parts[0].toIntOrNull() ?: return null
        val count = parts[1].toIntOrNull() ?: return null
        if (count <= 0 || index !in 1..count) return null
        val starts = preferences[ReaderSessionChapterStartsKey].orEmpty()
            .split(',')
            .mapNotNull(String::toFloatOrNull)
            .takeIf { starts -> starts.size == count && starts.zipWithNext().all { (a, b) -> a < b } }
            .orEmpty()
        return ReaderChapterPosition(
            index = index,
            count = count,
            title = preferences[ReaderSessionChapterTitleKey]?.takeIf(String::isNotBlank),
            starts = starts,
        )
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
