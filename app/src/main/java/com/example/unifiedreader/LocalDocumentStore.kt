package com.example.unifiedreader

import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.model.ReadableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

internal class LocalDocumentStore(
    private val appStartupTasks: AppStartupTasks = AppStartupTasks {},
    private val importedComicArchiveDao: ImportedComicArchiveDao,
    private val persistedCoverUriResolver: (itemId: String, coverFileName: String?) -> String? = { _, _ -> null },
) {
    private val recordsById = MutableStateFlow<Map<String, LocalDocumentRecord>>(emptyMap())
    private val importedItemsState = MutableStateFlow<List<ReadableItem>>(emptyList())
    private val hydrationState = MutableStateFlow(false)

    val isHydratedFlow: StateFlow<Boolean> = hydrationState.asStateFlow()
    val importedItemsFlow: StateFlow<List<ReadableItem>> = importedItemsState.asStateFlow()

    suspend fun hydrate(): List<LocalDocumentRecord> {
        appStartupTasks.initialize()
        val persistedRecords = withContext(Dispatchers.IO) {
            importedComicArchiveDao.getAll()
                .map { entity ->
                    entityToRecord(
                        entity = entity,
                        coverUri = persistedCoverUriResolver(entity.itemId, entity.coverFileName),
                    )
                }
                .filter(LocalDocumentRecord::isSupportedLocalFormat)
        }
        recordsById.update { existingRecords ->
            buildMap {
                persistedRecords.forEach { put(it.id, it) }
                existingRecords.values.forEach { put(it.id, it) }
            }
        }
        publishRecords(recordsById.value.values)
        hydrationState.value = true
        return persistedRecords
    }

    fun getImportedItems(): List<ReadableItem> = importedItemsFlow.value

    fun findRecord(itemId: String): LocalDocumentRecord? = recordsById.value[itemId]

    suspend fun upsert(record: LocalDocumentRecord) {
        appStartupTasks.initialize()
        withContext(Dispatchers.IO) {
            importedComicArchiveDao.insertOrReplace(record.toEntity())
        }
        publishRecords(recordsById.value.values.filterNot { it.id == record.id } + record)
    }

    suspend fun remove(itemId: String): LocalDocumentRecord? {
        val record = recordsById.value[itemId] ?: return null
        appStartupTasks.initialize()
        withContext(Dispatchers.IO) {
            importedComicArchiveDao.deleteByItemId(itemId)
        }
        publishRecords(recordsById.value.values.filterNot { it.id == itemId })
        return record
    }

    private fun publishRecords(
        records: Collection<LocalDocumentRecord>,
    ) {
        val sortedRecords = records.sortedBy { record ->
            record.title.ifBlank { record.displayName }.lowercase()
        }
        recordsById.value = sortedRecords.associateBy(LocalDocumentRecord::id)
        importedItemsState.value = sortedRecords.map(::recordToReadableItem)
    }
}
