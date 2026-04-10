package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.database.dao.DownloadQueueDao
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

    suspend fun clearCompletedEntries(): Int

    suspend fun deleteEntry(entryId: String): Int
}

class RoomDownloadQueueStore(
    private val downloadQueueDao: DownloadQueueDao,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) : DownloadQueueStore {
    private val enqueueMutex = Mutex()

    override fun observeEntries(): Flow<DownloadEntriesSnapshot> = downloadQueueDao.observeAll()
        .map { entities -> DownloadEntriesSnapshot(entities.map { entity -> entity.toDomain() }) }

    override suspend fun getEntries(): List<DownloadEntry> = withContext(Dispatchers.IO) {
        downloadQueueDao.getAll().map { entity -> entity.toDomain() }
    }

    override suspend fun getEntry(
        entryId: String,
    ): DownloadEntry? = withContext(Dispatchers.IO) {
        downloadQueueDao.getById(entryId)?.toDomain()
    }

    override suspend fun putEntry(
        entry: DownloadEntry,
    ) {
        withContext(Dispatchers.IO) {
            downloadQueueDao.insertOrReplace(entry.toEntity())
        }
    }

    override suspend fun putEntryIfNoActiveTrackedItems(
        entry: DownloadEntry,
    ): Boolean = withContext(Dispatchers.IO) {
        enqueueMutex.withLock {
            val hasOverlap = downloadQueueDao.getAll()
                .map { entity -> entity.toDomain() }
                .any { existingEntry ->
                    (existingEntry.status == DownloadEntryStatus.Queued ||
                        existingEntry.status == DownloadEntryStatus.Running) &&
                        existingEntry.trackedItemIds.any(entry.trackedItemIds::contains)
                }
            if (hasOverlap) {
                false
            } else {
                downloadQueueDao.insertOrReplace(entry.toEntity())
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
        withContext(Dispatchers.IO) {
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
    ): Boolean = withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) {
            downloadQueueDao.updateProgress(
                entryId = entryId,
                completedItemIdsEncoded = completedItemIds.toList().encodeStringList(),
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                updatedAtEpochMillis = timeProvider(),
            )
        }
    }

    override suspend fun clearCompletedEntries(): Int = withContext(Dispatchers.IO) {
        downloadQueueDao.deleteByStatus(DownloadEntryStatus.Completed.name) +
            downloadQueueDao.deleteByStatus(DownloadEntryStatus.Canceled.name)
    }

    override suspend fun deleteEntry(
        entryId: String,
    ): Int = withContext(Dispatchers.IO) {
        downloadQueueDao.deleteById(entryId)
    }
}
