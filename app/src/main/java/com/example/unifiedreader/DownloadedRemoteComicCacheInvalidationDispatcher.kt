package com.example.unifiedreader

import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Singleton
class DownloadedRemoteComicCacheInvalidationDispatcher @Inject constructor() {
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val invalidatedItemIds = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
    )

    fun register(
        listener: (String) -> Unit,
    ) {
        listeners += listener
    }

    fun observeInvalidations(
        itemId: String,
    ): Flow<Unit> = invalidatedItemIds
        .filter { invalidatedItemId -> invalidatedItemId == itemId }
        .map { Unit }

    fun invalidate(
        itemId: String,
    ) {
        invalidatedItemIds.tryEmit(itemId)
        listeners.forEach { listener -> listener(itemId) }
    }
}
