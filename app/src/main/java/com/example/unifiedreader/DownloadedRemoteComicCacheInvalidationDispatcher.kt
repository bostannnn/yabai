package com.example.unifiedreader

import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadedRemoteComicCacheInvalidationDispatcher @Inject constructor() {
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun register(
        listener: (String) -> Unit,
    ) {
        listeners += listener
    }

    fun invalidate(
        itemId: String,
    ) {
        listeners.forEach { listener -> listener(itemId) }
    }
}
