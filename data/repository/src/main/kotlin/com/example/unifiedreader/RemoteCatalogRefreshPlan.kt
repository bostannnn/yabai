package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.model.SourceType

/**
 * What a library refresh writes for a server that lists its whole catalog at once: the books that
 * are new or whose row differs from the stored one, the stored books the server no longer lists,
 * and the source's libraries. Null when the libraries and every book match what is stored, so a
 * refresh that finds nothing new leaves the tables, the grid and the covers alone.
 */
fun planRemoteCatalogRefresh(
    sourceType: SourceType,
    libraries: List<LibraryEntity>,
    items: List<ReadableItemEntity>,
    stored: StoredRemoteCatalog,
): RemoteCatalogDelta? {
    val storedById = stored.items.associateBy(ReadableItemEntity::id)
    val upsertItems = items.filterNot { item -> storedById[item.id]?.isSameRowAs(item) == true }
    val incomingIds = items.mapTo(HashSet(items.size), ReadableItemEntity::id)
    val removeItemIds = storedById.keys.filterNotTo(mutableSetOf(), incomingIds::contains)
    val librariesChanged = libraries.sortedBy(LibraryEntity::id) != stored.libraries.sortedBy(LibraryEntity::id)
    if (upsertItems.isEmpty() && removeItemIds.isEmpty() && !librariesChanged) return null
    return RemoteCatalogDelta(
        sourceType = sourceType,
        libraries = libraries,
        upsertItems = upsertItems,
        removeItemIds = removeItemIds,
    )
}

// Every column but the time the row was written.
private fun ReadableItemEntity.isSameRowAs(other: ReadableItemEntity): Boolean =
    this == other.copy(updatedAtEpochMillis = updatedAtEpochMillis)
