package com.example.unifiedreader

import android.util.Log
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RoomReaderSessionStore(
    private val readingProgressDao: ReadingProgressDao,
    private val readerSessionStateDao: ReaderSessionStateDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val appStartupTasks: AppStartupTasks = AppStartupTasks {},
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : ReaderSessionStore, ReaderSessionHydrator, StartupReaderSessionCacheSeeder {
    private val progressWriteMutex = Mutex()
    private val sessionWriteMutex = Mutex()
    private val hydrationMutex = Mutex()
    private val stateMonitor = Any()
    private val pendingWriteJobsMonitor = Any()
    private val pendingWriteJobs = linkedSetOf<Job>()
    private val tombstonedItemIds = mutableSetOf<String>()
    private val hydrationState = MutableStateFlow(false)
    override val isHydratedFlow: StateFlow<Boolean> = hydrationState.asStateFlow()
    private val progressState = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState.asStateFlow()
    private val sessionState = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionState.asStateFlow()

    override suspend fun hydrate() {
        if (isHydratedFlow.value) return
        hydrationMutex.withLock {
            if (isHydratedFlow.value) return
            appStartupTasks.initialize()
            val persistedProgress = withContext(ioDispatcher) {
                readingProgressDao.getAll()
                    .map(ReadingProgressEntity::toSnapshot)
            }
            val persistedSessions = withContext(ioDispatcher) {
                readerSessionStateDao.getAll()
                    .map(ReaderSessionStateEntity::toSnapshot)
            }
            mergePersistedReaderState(
                persistedProgress = persistedProgress,
                persistedSessions = persistedSessions,
            )
            hydrationState.value = true
        }
    }

    override suspend fun seedForStartupItemIds(
        itemIds: List<String>,
    ) {
        val requestedItemIds = itemIds.distinct()
        if (requestedItemIds.isEmpty()) return
        val persistedProgress = withContext(ioDispatcher) {
            runCatching {
                requestedItemIds
                    .chunked(SQLITE_BIND_SAFE_CHUNK_SIZE)
                    .flatMap(readingProgressDao::getByItemIds)
                    .map(ReadingProgressEntity::toSnapshot)
            }.onFailure { throwable ->
                logStartupReaderSeedFailure("Unable to seed startup reader progress.", throwable)
            }.getOrDefault(emptyList())
        }
        val persistedSessions = withContext(ioDispatcher) {
            runCatching {
                requestedItemIds
                    .chunked(SQLITE_BIND_SAFE_CHUNK_SIZE)
                    .flatMap(readerSessionStateDao::getByItemIds)
                    .map(ReaderSessionStateEntity::toSnapshot)
            }.onFailure { throwable ->
                logStartupReaderSeedFailure("Unable to seed startup reader sessions.", throwable)
            }.getOrDefault(emptyList())
        }
        mergePersistedReaderState(
            persistedProgress = persistedProgress,
            persistedSessions = persistedSessions,
        )
    }

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
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
            isExplicitUnreadReset = isExplicitUnreadReset,
            syncState = syncState,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        val accepted = synchronized(stateMonitor) {
            if (item.id in tombstonedItemIds) {
                false
            } else {
                progressState.value = progressState.value + (item.id to snapshot)
                true
            }
        }
        if (!accepted) return
        trackWriteJob(writeScope.launch {
            appStartupTasks.initialize()
            progressWriteMutex.withLock {
                val snapshotToPersist = synchronized(stateMonitor) {
                    progressState.value[item.id]
                }
                snapshotToPersist?.toEntity()?.let(readingProgressDao::insertOrReplace)
            }
        })
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean {
        val matched = synchronized(stateMonitor) {
            val current = progressState.value[expected.itemId]
            if (current != expected) {
                false
            } else {
                progressState.value = if (replacement == null) {
                    progressState.value - expected.itemId
                } else {
                    progressState.value + (expected.itemId to replacement)
                }
                true
            }
        }
        if (!matched) return false
        appStartupTasks.initialize()
        progressWriteMutex.withLock {
            withContext(Dispatchers.IO) {
                val latest = synchronized(stateMonitor) {
                    progressState.value[expected.itemId]
                }
                if (latest == null) {
                    readingProgressDao.deleteByItemId(expected.itemId)
                } else {
                    readingProgressDao.insertOrReplace(latest.toEntity())
                }
            }
        }
        return true
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionState.value[item.id]

    override suspend fun flushPendingWrites() {
        while (true) {
            val jobs = synchronized(pendingWriteJobsMonitor) {
                pendingWriteJobs.toList()
            }
            if (jobs.isEmpty()) return
            jobs.joinAll()
        }
    }

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val accepted = synchronized(stateMonitor) {
            if (itemId in tombstonedItemIds) {
                false
            } else {
                val current = sessionState.value[itemId] ?: ReaderSessionSnapshot(itemId = itemId)
                val next = current.copy(
                    pageCount = pageCount ?: current.pageCount,
                    preferences = current.preferences + preferences,
                )
                sessionState.value = sessionState.value + (itemId to next)
                true
            }
        }
        if (!accepted) return
        trackWriteJob(writeScope.launch {
            appStartupTasks.initialize()
            sessionWriteMutex.withLock {
                val latest = synchronized(stateMonitor) {
                    sessionState.value[itemId]
                } ?: return@withLock
                readerSessionStateDao.insertOrReplace(latest.toEntity())
            }
        })
    }

    override fun replaceSession(
        itemId: String,
        replacement: ReaderSessionSnapshot?,
    ) {
        val accepted = synchronized(stateMonitor) {
            if (itemId in tombstonedItemIds) {
                false
            } else {
                sessionState.value = if (replacement == null) {
                    sessionState.value - itemId
                } else {
                    sessionState.value + (itemId to replacement)
                }
                true
            }
        }
        if (!accepted) return
        trackWriteJob(writeScope.launch {
            appStartupTasks.initialize()
            sessionWriteMutex.withLock {
                val latest = synchronized(stateMonitor) {
                    sessionState.value[itemId]
                }
                if (latest == null) {
                    readerSessionStateDao.deleteByItemId(itemId)
                } else {
                    readerSessionStateDao.insertOrReplace(latest.toEntity())
                }
            }
        })
    }

    override fun clearProgress(itemId: String) {
        synchronized(stateMonitor) {
            progressState.update { it - itemId }
        }
        trackWriteJob(writeScope.launch {
            appStartupTasks.initialize()
            progressWriteMutex.withLock {
                val shouldDelete = synchronized(stateMonitor) {
                    itemId !in progressState.value
                }
                if (shouldDelete) {
                    readingProgressDao.deleteByItemId(itemId)
                }
            }
        })
    }

    override fun clearItem(itemId: String) {
        synchronized(stateMonitor) {
            tombstonedItemIds += itemId
            progressState.update { it - itemId }
            sessionState.update { it - itemId }
        }
        trackWriteJob(writeScope.launch {
            appStartupTasks.initialize()
            progressWriteMutex.withLock {
                val latest = synchronized(stateMonitor) {
                    progressState.value[itemId]
                }
                if (latest == null) {
                    readingProgressDao.deleteByItemId(itemId)
                } else {
                    readingProgressDao.insertOrReplace(latest.toEntity())
                }
            }
            sessionWriteMutex.withLock {
                val latest = synchronized(stateMonitor) {
                    sessionState.value[itemId]
                }
                if (latest == null) {
                    readerSessionStateDao.deleteByItemId(itemId)
                } else {
                    readerSessionStateDao.insertOrReplace(latest.toEntity())
                }
            }
        })
    }

    override fun restoreItem(itemId: String) {
        synchronized(stateMonitor) {
            tombstonedItemIds.remove(itemId)
        }
    }

    private fun trackWriteJob(
        job: Job,
    ) {
        synchronized(pendingWriteJobsMonitor) {
            pendingWriteJobs += job
        }
        job.invokeOnCompletion {
            synchronized(pendingWriteJobsMonitor) {
                pendingWriteJobs -= job
            }
        }
    }

    private fun mergePersistedReaderState(
        persistedProgress: List<ReaderProgressSnapshot>,
        persistedSessions: List<ReaderSessionSnapshot>,
    ) {
        synchronized(stateMonitor) {
            progressState.update { existing ->
                persistedProgress
                    .filterNot { snapshot -> snapshot.itemId in tombstonedItemIds }
                    .associateBy(ReaderProgressSnapshot::itemId) + existing
            }
            sessionState.update { existing ->
                persistedSessions
                    .filterNot { snapshot -> snapshot.itemId in tombstonedItemIds }
                    .associateBy(ReaderSessionSnapshot::itemId) + existing
            }
        }
    }
}

private fun logStartupReaderSeedFailure(
    message: String,
    throwable: Throwable,
) {
    if (Log.isLoggable(READER_SESSION_LOG_TAG, Log.WARN)) {
        Log.w(READER_SESSION_LOG_TAG, message, throwable)
    }
}

private const val READER_SESSION_LOG_TAG = "RoomReaderSessionStore"
private const val SQLITE_BIND_SAFE_CHUNK_SIZE = 900
