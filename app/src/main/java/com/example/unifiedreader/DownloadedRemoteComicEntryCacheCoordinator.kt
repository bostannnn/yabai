package com.example.unifiedreader

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadedRemoteComicEntryCacheCoordinator(
    private val archiveEntryCache: DownloadedRemoteComicArchiveEntryCache,
) {
    private val entryLoadMutex = Mutex()
    private val generationByItemId = ConcurrentHashMap<String, Int>()

    suspend fun entryNamesFor(
        itemId: String,
        loadEntryNames: suspend () -> List<String>,
    ): List<String> {
        archiveEntryCache.get(itemId)?.let { return it }
        return entryLoadMutex.withLock {
            loadCurrentEntryNames(
                itemId = itemId,
                loadEntryNames = loadEntryNames,
            )
        }
    }

    fun invalidate(
        itemId: String,
    ) {
        generationByItemId.compute(itemId) { _, generation -> (generation ?: 0) + 1 }
        archiveEntryCache.invalidate(itemId)
    }

    private fun generationFor(
        itemId: String,
    ): Int = generationByItemId[itemId] ?: 0

    private suspend fun loadCurrentEntryNames(
        itemId: String,
        loadEntryNames: suspend () -> List<String>,
    ): List<String> {
        while (true) {
            archiveEntryCache.get(itemId)?.let { return it }
            val requestGeneration = generationFor(itemId)
            val loadedEntryNames = loadEntryNames()
            if (generationFor(itemId) == requestGeneration) {
                archiveEntryCache.put(itemId, loadedEntryNames)
                return loadedEntryNames
            }
        }
    }
}
