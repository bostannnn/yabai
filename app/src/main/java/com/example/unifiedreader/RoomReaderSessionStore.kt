package com.example.unifiedreader

import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomReaderSessionStore(
    private val readingProgressDao: ReadingProgressDao,
    private val readerSessionStateDao: ReaderSessionStateDao,
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ReaderSessionStore {
    private val progressWriteMutex = Mutex()
    private val sessionWriteMutex = Mutex()
    private val hydrationState = MutableStateFlow(false)
    override val isHydratedFlow: StateFlow<Boolean> = hydrationState.asStateFlow()
    private val progressState = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState.asStateFlow()
    private val sessionState = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionState.asStateFlow()

    init {
        writeScope.launch {
            progressState.update { existing ->
                readingProgressDao.getAll()
                    .map(ReadingProgressEntity::toSnapshot)
                    .associateBy(ReaderProgressSnapshot::itemId) + existing
            }
            sessionState.update { existing ->
                readerSessionStateDao.getAll()
                    .map(ReaderSessionStateEntity::toSnapshot)
                    .associateBy(ReaderSessionSnapshot::itemId) + existing
            }
            hydrationState.value = true
        }
    }

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        val snapshot = ReaderProgressSnapshot(
            itemId = item.id,
            locatorType = locator.locatorType,
            page = locator.page,
            cfi = locator.cfi,
            percent = locator.percent,
            isFinished = isFinished,
            syncState = syncState,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        progressState.value = progressState.value + (item.id to snapshot)
        writeScope.launch {
            progressWriteMutex.withLock {
                progressState.value[item.id]
                    ?.toEntity()
                    ?.let(readingProgressDao::insertOrReplace)
            }
        }
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionState.value[item.id]

    override fun updateSession(
        item: ReadableItem,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionState.value[item.id] ?: ReaderSessionSnapshot(itemId = item.id)
        val next = current.copy(
            pageCount = pageCount ?: current.pageCount,
            preferences = current.preferences + preferences,
        )
        sessionState.value = sessionState.value + (item.id to next)
        writeScope.launch {
            sessionWriteMutex.withLock {
                val latest = sessionState.value[item.id] ?: ReaderSessionSnapshot(itemId = item.id)
                readerSessionStateDao.insertOrReplace(latest.toEntity())
            }
        }
    }

    override fun clearProgress(itemId: String) {
        progressState.update { it - itemId }
        writeScope.launch {
            progressWriteMutex.withLock {
                if (itemId !in progressState.value) {
                    readingProgressDao.deleteByItemId(itemId)
                }
            }
        }
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        sessionState.update { it - itemId }
        writeScope.launch {
            sessionWriteMutex.withLock {
                readerSessionStateDao.deleteByItemId(itemId)
            }
        }
    }
}
