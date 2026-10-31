package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType

/**
 * A server's own Want to read, kept in step with the app's for that server's books. Kavita keeps
 * whole series there, so a book stands for its series. The app implements it per server; a call
 * throws when the server refuses or can't be reached, and does nothing while it isn't connected.
 */
interface ServerWantToRead {
    val sourceType: SourceType

    /** The series [item] belongs to on the server, or null when it isn't the server's or has none. */
    fun seriesIdOf(item: ReadableItem): String?

    suspend fun add(seriesIds: Set<String>)

    suspend fun remove(seriesIds: Set<String>)
}

/**
 * The series a change to Want to read puts on the server's Want to read ([added]) or takes off it.
 * A series comes off only once none of its books is left in Want to read: [remaining] are the books
 * still there after the change.
 */
fun wantToReadSeriesChange(
    changed: Collection<ReadableItem>,
    added: Boolean,
    remaining: Collection<ReadableItem>,
    seriesIdOf: (ReadableItem) -> String?,
): Set<String> {
    val series = changed.mapNotNullTo(LinkedHashSet(), seriesIdOf)
    if (added) return series
    val stillWanted = remaining.mapNotNullTo(HashSet(), seriesIdOf)
    return series - stillWanted
}
