package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.applyLibraryFilters
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.feature.library.ItemProgressSummary
import java.net.URLDecoder
import java.net.URLEncoder

/** A smart list's filters: one library's ([sourceType]) filters, as the Filters sheet held them. */
data class SmartListFilter(
    val sourceType: SourceType,
    val filters: LibraryFilterState,
)

/**
 * The smart lists among [lists] with their books worked out now: the [items] of their library the
 * filters pick out (books and issues, not whole series), each dated by when it was added.
 */
fun List<UserList>.withSmartBooks(
    items: Collection<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    onDevice: Set<String>,
): List<UserList> = map { list ->
    val smart = list.smartFilter ?: return@map list
    val pool = items.filter { item ->
        item.sourceType == smart.sourceType && !item.isKavitaSeriesContainer() && !item.isStumpSeriesContainer()
    }
    val matching = applyLibraryFilters(
        items = pool,
        filters = smart.filters,
        downloadedRemoteItemIds = onDevice,
        readingStatusProvider = { item -> progressByItemId[item.id].readingStatus() },
    )
    list.copy(
        entries = matching.mapIndexed { index, item ->
            item.toUserListEntry(position = index, addedAtEpochMillis = item.addedAtEpochMillis ?: 0L)
        },
    )
}

private fun ItemProgressSummary?.readingStatus(): LibraryReadingStatus = when {
    this == null -> LibraryReadingStatus.NOT_STARTED
    isFinished -> LibraryReadingStatus.FINISHED
    fraction > 0f -> LibraryReadingStatus.IN_PROGRESS
    else -> LibraryReadingStatus.NOT_STARTED
}

/** Writes [this] as the line-per-filter text `user_lists.smartFilter` keeps. */
fun SmartListFilter.encode(): String = buildList {
    add("source=${sourceType.name}")
    add("formats=" + filters.formats.joinToString(",") { it.name })
    add("libraries=" + filters.libraries.joinToString(",", transform = ::encodeValue))
    add("locations=" + filters.fileLocations.joinToString(",") { it.name })
    add("statuses=" + filters.readingStatuses.joinToString(",") { it.name })
    filters.pages?.let { range -> add("pages=${range.min ?: ""}|${range.max ?: ""}|${range.includeUnknown}") }
    add("authors=" + filters.authors.joinToString(",", transform = ::encodeValue))
    add("series=" + filters.series.joinToString(",", transform = ::encodeValue))
    add("publishers=" + filters.publishers.joinToString(",", transform = ::encodeValue))
    add("genres=" + filters.genres.joinToString(",", transform = ::encodeValue))
    add("tags=" + filters.tags.joinToString(",", transform = ::encodeValue))
    add("languages=" + filters.languages.joinToString(",", transform = ::encodeValue))
}.joinToString("\n")

/** Reads what [encode] wrote; null for text that isn't a smart list's filters. */
fun decodeSmartListFilter(text: String?): SmartListFilter? {
    if (text.isNullOrBlank()) return null
    val fields = text.lines().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    val source = SourceType.entries.firstOrNull { it.name == fields["source"] } ?: return null
    fun values(key: String): Set<String> = fields[key].orEmpty().split(',').filter(String::isNotEmpty).map(::decodeValue).toSet()
    fun <T : Enum<T>> enums(key: String, entries: List<T>): Set<T> = values(key).mapNotNull { name -> entries.firstOrNull { it.name == name } }.toSet()
    val pages = fields["pages"]?.split('|')?.takeIf { it.size == 3 }?.let { (min, max, unknown) ->
        LibraryPageRange(min = min.toIntOrNull(), max = max.toIntOrNull(), includeUnknown = unknown.toBoolean())
    }
    return SmartListFilter(
        sourceType = source,
        filters = LibraryFilterState(
            formats = enums("formats", FormatType.entries),
            libraries = values("libraries"),
            fileLocations = enums("locations", LibraryFileLocation.entries),
            readingStatuses = enums("statuses", LibraryReadingStatus.entries),
            pages = pages,
            authors = values("authors"),
            series = values("series"),
            publishers = values("publishers"),
            genres = values("genres"),
            tags = values("tags"),
            languages = values("languages"),
        ),
    )
}

private fun encodeValue(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun decodeValue(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
