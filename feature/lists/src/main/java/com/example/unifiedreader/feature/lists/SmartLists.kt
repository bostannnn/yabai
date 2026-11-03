package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleContext
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.decodeListRules
import com.example.unifiedreader.core.model.encode
import com.example.unifiedreader.core.model.matches
import com.example.unifiedreader.core.model.toListRules
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.feature.library.ItemProgressSummary
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A smart list as it is kept: its rules, how many of its matches it shows ([limit] null: every
 * one), and, for one saved from a library's Filters, those picks ([filter]), so Change filters
 * opens them again as they were. The rules are what match; the picks are what you change.
 */
data class SmartListSpec(
    val rules: ListRules,
    val limit: Int? = null,
    val filter: SmartListFilter? = null,
)

/** How many matches a smart list can keep, for its editor's How many. */
val SmartListLimits: List<Int?> = listOf(null, 10, 25, 50)

/** A smart list's filters as lists saved them before rules: one library's ([sourceType]) filters, as the Filters sheet held them. */
data class SmartListFilter(
    val sourceType: SourceType,
    val filters: LibraryFilterState,
)

/**
 * Writes [this] as the text `user_lists.smartFilter` keeps: the rules, then `limit=` when it has
 * one, then the Filters picks it was saved from, each line after [FilterLinePrefix] (rules ignore
 * lines they don't know, so an older app still reads the rules).
 */
fun SmartListSpec.encode(): String = rules.encode() +
    (limit?.let { "\nlimit=$it" } ?: "") +
    (filter?.let { picks -> "\n" + picks.encode().lines().joinToString("\n") { line -> FilterLinePrefix + line } } ?: "")

/** Reads a smart list's text: its rules, or the filters of one saved before rules, as rules. Null for anything else. */
fun decodeSmartListSpec(text: String?): SmartListSpec? {
    decodeListRules(text)?.let { rules ->
        val lines = text.orEmpty().lines()
        val limit = lines.firstOrNull { it.startsWith("limit=") }?.removePrefix("limit=")?.toIntOrNull()
        val filter = lines.filter { it.startsWith(FilterLinePrefix) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { it.removePrefix(FilterLinePrefix) }
            ?.let(::decodeSmartListFilter)
        return SmartListSpec(rules = rules, limit = limit, filter = filter)
    }
    return decodeSmartListFilter(text)?.let { old -> SmartListSpec(rules = old.filters.toListRules(old.sourceType)) }
}

/** What this device knows of your reading and downloads, as the rules ask it. */
fun listRuleContext(
    progressByItemId: Map<String, ItemProgressSummary>,
    onDevice: Set<String>,
    nowEpochMillis: Long,
): ListRuleContext = ListRuleContext(
    nowEpochMillis = nowEpochMillis,
    readingStatus = { item -> progressByItemId[item.id].readingStatus() },
    lastReadAtEpochMillis = { item -> progressByItemId[item.id]?.lastReadAtEpochMillis?.takeIf { it > 0L } },
    isDownloaded = { item -> item.isLocalImport || item.id in onDevice },
)

/** The books and issues rules can pick from (not whole series), from [sourceType] alone when given (a magic shelf's). */
fun smartListPool(items: Collection<ReadableItem>, sourceType: SourceType? = null): List<ReadableItem> = items.filter { item ->
    !item.isKavitaSeriesContainer() && !item.isStumpSeriesContainer() && (sourceType == null || item.sourceType == sourceType)
}

/** The books [rules] pick out of [pool] now, in [order], at most [limit]: a smart list's entries. */
fun smartListEntries(
    rules: ListRules,
    pool: List<ReadableItem>,
    context: ListRuleContext,
    order: UserListOrder,
    limit: Int?,
): List<UserListEntry> {
    if (rules.isEmpty) return emptyList()
    val entries = pool
        .filter { item -> rules.matches(item, context) }
        .map { item -> item.toUserListEntry(position = 0, addedAtEpochMillis = item.addedAtEpochMillis ?: 0L) }
    val sorted = sortEntries(entries, order)
    return (limit?.let(sorted::take) ?: sorted).mapIndexed { index, entry -> entry.copy(position = index) }
}

/** How many books of [pool] each whole rule of [rules] picks on its own, for the editor's rows. */
fun smartListRuleCounts(
    rules: ListRules,
    pool: List<ReadableItem>,
    context: ListRuleContext,
): Map<ListRule, Int> = rules.allRules
    .filter(ListRule::isComplete)
    .distinct()
    .associateWith { rule -> pool.count { item -> rule.matches(item, context) } }

/**
 * When rules joined by all match nothing: the one rule of them without which the most books
 * would match, and how many. Null when they match something, are joined by any, or no single
 * rule is to blame.
 */
fun smartListEmptiedBy(
    rules: ListRules,
    pool: List<ReadableItem>,
    context: ListRuleContext,
): Pair<ListRule, Int>? {
    if (!rules.matchAll || rules.rules.size + rules.groups.size < 2) return null
    if (pool.any { item -> rules.matches(item, context) }) return null
    return rules.rules
        .map { rule ->
            val without = rules.copy(rules = rules.rules - rule)
            rule to pool.count { item -> without.matches(item, context) }
        }
        .filter { (_, count) -> count > 0 }
        .maxByOrNull { (_, count) -> count }
}

/**
 * The smart lists among [lists] with their books worked out now: the books and issues of every
 * source their rules pick out, in each list's order, as many as it keeps.
 */
fun List<UserList>.withSmartBooks(
    items: Collection<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    onDevice: Set<String>,
    nowEpochMillis: Long = System.currentTimeMillis(),
): List<UserList> {
    if (none { it.kind == UserListKind.Smart && it.smart != null }) return this
    val pool = smartListPool(items)
    val context = listRuleContext(progressByItemId, onDevice, nowEpochMillis)
    return map { list ->
        val smart = list.smart?.takeIf { list.kind == UserListKind.Smart } ?: return@map list
        list.copy(entries = smartListEntries(smart.rules, pool, context, list.order, smart.limit))
    }
}

internal fun ItemProgressSummary?.readingStatus(): LibraryReadingStatus = when {
    this == null -> LibraryReadingStatus.NOT_STARTED
    isFinished -> LibraryReadingStatus.FINISHED
    fraction > 0f -> LibraryReadingStatus.IN_PROGRESS
    else -> LibraryReadingStatus.NOT_STARTED
}

/** Writes [this] as the line-per-filter text smart lists kept before rules (kept for tests of the old text). */
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
    add("places=" + filters.places.joinToString(",", transform = ::encodeValue))
    add("times=" + filters.times.joinToString(",", transform = ::encodeValue))
    add("moods=" + filters.moods.joinToString(",", transform = ::encodeValue))
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
            places = values("places"),
            times = values("times"),
            moods = values("moods"),
        ),
    )
}

// Marks the lines of a smart list's text that hold the Filters picks it was saved from.
private const val FilterLinePrefix = "filter."

private fun encodeValue(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun decodeValue(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
