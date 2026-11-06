package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.database.dao.DownloadQueueDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface DownloadQueueStore {
    fun observeEntries(): Flow<DownloadEntriesSnapshot>

    suspend fun getEntries(): List<DownloadEntry>

    suspend fun getEntry(entryId: String): DownloadEntry?

    suspend fun putEntry(entry: DownloadEntry)

    suspend fun putEntryIfNoActiveTrackedItems(entry: DownloadEntry): Boolean

    suspend fun retryEntryIfNoOtherActiveTrackedItems(entryId: String): Boolean

    suspend fun updateStatus(
        entryId: String,
        status: DownloadEntryStatus,
        currentItemId: String? = null,
        currentItemTitle: String? = null,
        errorMessage: String? = null,
        completedAtEpochMillis: Long? = null,
    )

    suspend fun updateStatusIfCurrentStatusIn(
        entryId: String,
        expectedStatuses: Set<DownloadEntryStatus>,
        status: DownloadEntryStatus,
        currentItemId: String? = null,
        currentItemTitle: String? = null,
        errorMessage: String? = null,
        completedAtEpochMillis: Long? = null,
    ): Boolean

    suspend fun updateProgress(
        entryId: String,
        completedItemIds: Set<String>,
        currentItemId: String?,
        currentItemTitle: String?,
    )

    suspend fun deleteEntry(entryId: String): Int
}

class RoomDownloadQueueStore(
    private val downloadQueueDao: DownloadQueueDao,
    private val timeProvider: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DownloadQueueStore {
    private val enqueueMutex = Mutex()

    override fun observeEntries(): Flow<DownloadEntriesSnapshot> = downloadQueueDao.observeAll()
        .map { entities -> DownloadEntriesSnapshot(entities.map { entity -> entity.toDomain() }) }

    override suspend fun getEntries(): List<DownloadEntry> = withContext(ioDispatcher) {
        downloadQueueDao.getAll().map { entity -> entity.toDomain() }
    }

    override suspend fun getEntry(
        entryId: String,
    ): DownloadEntry? = withContext(ioDispatcher) {
        downloadQueueDao.getById(entryId)?.toDomain()
    }

    override suspend fun putEntry(
        entry: DownloadEntry,
    ) {
        withContext(ioDispatcher) {
            downloadQueueDao.insertOrReplace(entry.toEntity())
        }
    }

    override suspend fun putEntryIfNoActiveTrackedItems(
        entry: DownloadEntry,
    ): Boolean = withContext(ioDispatcher) {
        enqueueMutex.withLock {
            val hasOverlap = downloadQueueDao.getAll()
                .map { entity -> entity.toDomain() }
                .hasActiveTrackedItemOverlap(
                    trackedItemIds = entry.trackedItemIds,
                    ignoredEntryId = null,
                )
            if (hasOverlap) {
                false
            } else {
                downloadQueueDao.insertOrReplace(entry.toEntity())
                true
            }
        }
    }

    override suspend fun retryEntryIfNoOtherActiveTrackedItems(
        entryId: String,
    ): Boolean = withContext(ioDispatcher) {
        enqueueMutex.withLock {
            val entries = downloadQueueDao.getAll().map { entity -> entity.toDomain() }
            val entry = entries.firstOrNull { candidate -> candidate.id == entryId } ?: return@withLock false
            val hasOverlap = entries.hasActiveTrackedItemOverlap(
                trackedItemIds = entry.trackedItemIds,
                ignoredEntryId = entryId,
            )
            if (hasOverlap) {
                false
            } else {
                downloadQueueDao.insertOrReplace(
                    entry.copy(
                        status = DownloadEntryStatus.Queued,
                        errorMessage = null,
                        currentItemId = null,
                        currentItemTitle = null,
                        updatedAtEpochMillis = timeProvider(),
                        completedAtEpochMillis = null,
                    ).toEntity(),
                )
                true
            }
        }
    }

    override suspend fun updateStatus(
        entryId: String,
        status: DownloadEntryStatus,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        completedAtEpochMillis: Long?,
    ) {
        withContext(ioDispatcher) {
            downloadQueueDao.updateStatus(
                entryId = entryId,
                status = status.name,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                errorMessage = errorMessage,
                updatedAtEpochMillis = timeProvider(),
                completedAtEpochMillis = completedAtEpochMillis,
            )
        }
    }

    override suspend fun updateStatusIfCurrentStatusIn(
        entryId: String,
        expectedStatuses: Set<DownloadEntryStatus>,
        status: DownloadEntryStatus,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        completedAtEpochMillis: Long?,
    ): Boolean = withContext(ioDispatcher) {
        downloadQueueDao.updateStatusIfCurrentStatusIn(
            entryId = entryId,
            expectedStatuses = expectedStatuses.map(DownloadEntryStatus::name),
            status = status.name,
            currentItemId = currentItemId,
            currentItemTitle = currentItemTitle,
            errorMessage = errorMessage,
            updatedAtEpochMillis = timeProvider(),
            completedAtEpochMillis = completedAtEpochMillis,
        ) > 0
    }

    override suspend fun updateProgress(
        entryId: String,
        completedItemIds: Set<String>,
        currentItemId: String?,
        currentItemTitle: String?,
    ) {
        withContext(ioDispatcher) {
            downloadQueueDao.updateProgress(
                entryId = entryId,
                completedItemIdsEncoded = completedItemIds.toList().encodeStringList(),
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                updatedAtEpochMillis = timeProvider(),
            )
        }
    }

    override suspend fun deleteEntry(
        entryId: String,
    ): Int = withContext(ioDispatcher) {
        downloadQueueDao.deleteById(entryId)
    }
}

private fun List<DownloadEntry>.hasActiveTrackedItemOverlap(
    trackedItemIds: Set<String>,
    ignoredEntryId: String?,
): Boolean = any { existingEntry ->
    existingEntry.id != ignoredEntryId &&
        existingEntry.status.blocksTrackedItemOverlap() &&
        existingEntry.trackedItemIds.any(trackedItemIds::contains)
}

private fun DownloadEntryStatus.blocksTrackedItemOverlap(): Boolean = when (this) {
    DownloadEntryStatus.Queued,
    DownloadEntryStatus.PausedConnectivity,
    DownloadEntryStatus.Running,
    DownloadEntryStatus.Canceling -> true
    DownloadEntryStatus.Completed,
    DownloadEntryStatus.Canceled,
    DownloadEntryStatus.Failed -> false
}
