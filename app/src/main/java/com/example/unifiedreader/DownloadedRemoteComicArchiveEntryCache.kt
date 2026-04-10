package com.example.unifiedreader

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class DownloadedRemoteComicArchiveEntryCache @Inject constructor() {
    private val pageEntryNamesByItemId = ConcurrentHashMap<String, List<String>>()

    fun get(
        itemId: String,
    ): List<String>? = pageEntryNamesByItemId[itemId]

    fun put(
        itemId: String,
        entryNames: List<String>,
    ) {
        pageEntryNamesByItemId[itemId] = entryNames
    }

    fun invalidate(
        itemId: String,
    ) {
        pageEntryNamesByItemId.remove(itemId)
    }
}
