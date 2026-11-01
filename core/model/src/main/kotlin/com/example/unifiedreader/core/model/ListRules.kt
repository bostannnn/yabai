package com.example.unifiedreader.core.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/** Where a field sits in the Add a rule sheet. */
enum class ListRuleSection(val label: String) {
    AboutTheBook("About the book"),
    YourReading("Your reading"),
    InYourLibrary("In your library"),
}

/** What a rule's values are, which decides its operators and its value sheet. */
enum class ListRuleValues {
    Names,
    Number,
    Year,
    ReadingStatus,
    Time,
    YesNo,
}

/**
 * What a smart list's rule looks at. [canMatchAll] where a book has several values (tags, genres),
 * so "all of" means something; [onDeviceOnly] where only this device knows the answer, so a
 * Grimmory magic shelf can't use it; [isPickable] false for a field only lists saved from the
 * library's filters carry.
 */
enum class ListRuleField(
    val label: String,
    val values: ListRuleValues,
    val section: ListRuleSection,
    val hint: String,
    val canMatchAll: Boolean = false,
    val onDeviceOnly: Boolean = false,
    val isPickable: Boolean = true,
) {
    Author("Author", ListRuleValues.Names, ListRuleSection.AboutTheBook, "Any of, all of, none of", canMatchAll = true),
    Series("Series", ListRuleValues.Names, ListRuleSection.AboutTheBook, "Any of, none of"),
    Genre("Genre", ListRuleValues.Names, ListRuleSection.AboutTheBook, "Any of, all of, none of", canMatchAll = true),
    Tag("Tag", ListRuleValues.Names, ListRuleSection.AboutTheBook, "Set in, when, about", canMatchAll = true),
    Mood("Mood", ListRuleValues.Names, ListRuleSection.AboutTheBook, "Any of, all of, none of", canMatchAll = true),
    Language("Language", ListRuleValues.Names, ListRuleSection.AboutTheBook, "Any of, none of"),
    Publisher("Publisher", ListRuleValues.Names, ListRuleSection.AboutTheBook, "Any of, none of"),
    Pages("Pages", ListRuleValues.Number, ListRuleSection.AboutTheBook, "Under, over, between"),
    Published("Published", ListRuleValues.Year, ListRuleSection.AboutTheBook, "Before, after, between"),
    Status("Status", ListRuleValues.ReadingStatus, ListRuleSection.YourReading, "Not started, reading, finished"),
    LastRead("Last read", ListRuleValues.Time, ListRuleSection.YourReading, "In the last, not in the last"),
    Added("Added", ListRuleValues.Time, ListRuleSection.InYourLibrary, "In the last, not in the last"),
    Format("Format", ListRuleValues.Names, ListRuleSection.InYourLibrary, "EPUB, PDF, CBZ, CBR"),
    Shelf("Grimmory shelf", ListRuleValues.Names, ListRuleSection.InYourLibrary, "Any of, all of, none of", canMatchAll = true),
    Downloaded("Downloaded", ListRuleValues.YesNo, ListRuleSection.InYourLibrary, "On this device or not", onDeviceOnly = true),
    Source("Source", ListRuleValues.Names, ListRuleSection.InYourLibrary, "Kavita, Grimmory, Local, Stump", onDeviceOnly = true),
    Library("Library", ListRuleValues.Names, ListRuleSection.InYourLibrary, "Any of, none of", isPickable = false),
}

/** What a rule asks of its field. Names: any/all/none of; numbers and years: under/over/between; times: in the last/not in the last; yes or no: is/is not. */
enum class ListRuleOperator {
    AnyOf,
    AllOf,
    NoneOf,
    Under,
    Over,
    Between,
    InLast,
    NotInLast,
    Is,
    IsNot,
}

enum class ListRuleTimeUnit(val singular: String, val plural: String, val millis: Long) {
    Days("day", "days", 24L * 60 * 60 * 1000),
    Weeks("week", "weeks", 7L * 24 * 60 * 60 * 1000),
    Months("month", "months", 30L * 24 * 60 * 60 * 1000),
}

/**
 * One rule: [values] for names and statuses; [low] and [high] for numbers and years (Under uses
 * [high], Over [low], Between both, inclusive); for times the amount is [low] in [unit].
 */
data class ListRule(
    val field: ListRuleField,
    val operator: ListRuleOperator,
    val values: List<String> = emptyList(),
    val low: Int? = null,
    val high: Int? = null,
    val unit: ListRuleTimeUnit? = null,
) {
    /** Whether the rule asks something whole enough to save. */
    val isComplete: Boolean
        get() = when (operator) {
            ListRuleOperator.AnyOf, ListRuleOperator.AllOf, ListRuleOperator.NoneOf -> values.isNotEmpty()
            ListRuleOperator.Under -> high != null
            ListRuleOperator.Over -> low != null
            ListRuleOperator.Between -> low != null && high != null && low <= high
            ListRuleOperator.InLast, ListRuleOperator.NotInLast -> low != null && low > 0 && unit != null
            ListRuleOperator.Is, ListRuleOperator.IsNot -> true
        }
}

/** Rules joined by all ([matchAll]) or any; a group sits inside a list's rules, one level deep. */
data class ListRuleGroup(
    val matchAll: Boolean = false,
    val rules: List<ListRule> = emptyList(),
)

/** A smart list's rules: its own, joined by all or any, and its groups, each one more rule. */
data class ListRules(
    val matchAll: Boolean = true,
    val rules: List<ListRule> = emptyList(),
    val groups: List<ListRuleGroup> = emptyList(),
) {
    val isEmpty: Boolean
        get() = rules.isEmpty() && groups.all { it.rules.isEmpty() }

    /** Every rule, the groups' too. */
    val allRules: List<ListRule>
        get() = rules + groups.flatMap(ListRuleGroup::rules)

    /** Whether a Grimmory magic shelf can hold these rules: none asks what only this device knows. */
    val fitsMagicShelf: Boolean
        get() = allRules.none { it.field.onDeviceOnly || it.field == ListRuleField.Library }
}

/** What this device knows about a book besides the book itself, for the rules that ask it. */
class ListRuleContext(
    val nowEpochMillis: Long,
    val readingStatus: (ReadableItem) -> LibraryReadingStatus,
    val lastReadAtEpochMillis: (ReadableItem) -> Long?,
    val isDownloaded: (ReadableItem) -> Boolean,
)

fun ListRules.matches(item: ReadableItem, context: ListRuleContext): Boolean {
    val facets = item.facetMetadata()
    val results = rules.map { rule -> rule.matches(item, facets, context) } +
        groups.filter { it.rules.isNotEmpty() }.map { group -> group.matches(item, facets, context) }
    if (results.isEmpty()) return true
    return if (matchAll) results.all { it } else results.any { it }
}

private fun ListRuleGroup.matches(item: ReadableItem, facets: ReadableItemFacetMetadata, context: ListRuleContext): Boolean {
    val results = rules.map { rule -> rule.matches(item, facets, context) }
    return if (matchAll) results.all { it } else results.any { it }
}

fun ListRule.matches(item: ReadableItem, context: ListRuleContext): Boolean = matches(item, item.facetMetadata(), context)

private fun ListRule.matches(item: ReadableItem, facets: ReadableItemFacetMetadata, context: ListRuleContext): Boolean = when (field.values) {
    ListRuleValues.Names -> matchesNames(field.namesOf(item, facets))
    ListRuleValues.ReadingStatus -> matchesNames(listOf(context.readingStatus(item).name))
    ListRuleValues.Number -> matchesNumber(if (field == ListRuleField.Pages) item.pageCount?.takeIf { it > 0 } else null)
    ListRuleValues.Year -> matchesNumber(item.publishedYear)
    ListRuleValues.Time -> {
        val at = when (field) {
            ListRuleField.LastRead -> context.lastReadAtEpochMillis(item)
            else -> item.addedAtEpochMillis
        }?.takeIf { it > 0L }
        matchesTime(at, context.nowEpochMillis)
    }
    ListRuleValues.YesNo -> {
        val yes = context.isDownloaded(item)
        if (operator == ListRuleOperator.IsNot) !yes else yes
    }
}

/** The book's values for a names field, as the rule compares them (case aside). */
fun ListRuleField.namesOf(item: ReadableItem, facets: ReadableItemFacetMetadata = item.facetMetadata()): List<String> = when (this) {
    ListRuleField.Author -> (facets.authors + facets.writers).distinct()
    ListRuleField.Series -> facets.series
    ListRuleField.Genre -> facets.genres
    ListRuleField.Tag -> facets.tags
    ListRuleField.Mood -> facets.moods
    ListRuleField.Language -> facets.languages
    ListRuleField.Publisher -> facets.publishers
    ListRuleField.Shelf -> facets.shelves
    ListRuleField.Format -> listOf(item.format.name)
    ListRuleField.Source -> listOf(item.sourceType.name)
    ListRuleField.Library -> listOf(item.libraryId)
    else -> emptyList()
}

private fun ListRule.matchesNames(itemValues: List<String>): Boolean {
    val have = itemValues.mapTo(HashSet(), String::ruleKey)
    val wanted = values.map(String::ruleKey)
    return when (operator) {
        ListRuleOperator.AnyOf -> wanted.any(have::contains)
        ListRuleOperator.AllOf -> wanted.isNotEmpty() && wanted.all(have::contains)
        ListRuleOperator.NoneOf -> wanted.none(have::contains)
        else -> false
    }
}

/** A value as rules compare it: case, width and ё aside, as the library's own filters do. */
private fun String.ruleKey(): String = Normalizer.normalize(trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace('ё', 'е')

private fun ListRule.matchesNumber(value: Int?): Boolean {
    value ?: return false
    return when (operator) {
        ListRuleOperator.Under -> high != null && value < high
        ListRuleOperator.Over -> low != null && value > low
        ListRuleOperator.Between -> low != null && high != null && value in low..high
        else -> false
    }
}

private fun ListRule.matchesTime(at: Long?, now: Long): Boolean {
    at ?: return false
    val span = (low ?: return false) * (unit ?: return false).millis
    return when (operator) {
        ListRuleOperator.InLast -> at >= now - span
        ListRuleOperator.NotInLast -> at < now - span
        else -> false
    }
}

/** The operators a field's rule can take, the first being where a new rule starts. */
fun ListRuleField.operators(): List<ListRuleOperator> = when (values) {
    ListRuleValues.Names -> if (canMatchAll) {
        listOf(ListRuleOperator.AnyOf, ListRuleOperator.AllOf, ListRuleOperator.NoneOf)
    } else {
        listOf(ListRuleOperator.AnyOf, ListRuleOperator.NoneOf)
    }
    ListRuleValues.ReadingStatus -> listOf(ListRuleOperator.AnyOf, ListRuleOperator.NoneOf)
    ListRuleValues.Number, ListRuleValues.Year -> listOf(ListRuleOperator.Under, ListRuleOperator.Over, ListRuleOperator.Between)
    ListRuleValues.Time -> listOf(ListRuleOperator.InLast, ListRuleOperator.NotInLast)
    ListRuleValues.YesNo -> listOf(ListRuleOperator.Is, ListRuleOperator.IsNot)
}

/** "Any of", "Under", "In the last": an operator's name in its segmented row. */
fun ListRuleOperator.label(field: ListRuleField): String = when (this) {
    ListRuleOperator.AnyOf -> "Any of"
    ListRuleOperator.AllOf -> "All of"
    ListRuleOperator.NoneOf -> "None of"
    ListRuleOperator.Under -> if (field.values == ListRuleValues.Year) "Before" else "Under"
    ListRuleOperator.Over -> if (field.values == ListRuleValues.Year) "After" else "Over"
    ListRuleOperator.Between -> "Between"
    ListRuleOperator.InLast -> "In the last"
    ListRuleOperator.NotInLast -> "Not in the last"
    ListRuleOperator.Is -> "Yes"
    ListRuleOperator.IsNot -> "No"
}

/** The value of a names rule as people read it: a status by its name, a format and source as shown. */
fun ListRuleField.valueLabel(value: String): String = when (this) {
    ListRuleField.Status -> when (value) {
        LibraryReadingStatus.NOT_STARTED.name -> "Not started"
        LibraryReadingStatus.IN_PROGRESS.name -> "Reading"
        LibraryReadingStatus.FINISHED.name -> "Finished"
        else -> value
    }
    ListRuleField.Source -> SourceType.entries.firstOrNull { it.name == value }?.displayName() ?: value
    else -> value
}

/** What the rule asks, after its field's name, for the editor's rows: "is all of: Russia, 19th century", "under 500". */
fun ListRule.summary(valueLabel: (String) -> String = field::valueLabel): String {
    val names = values.joinToString(", ", transform = valueLabel)
    return when (operator) {
        ListRuleOperator.AnyOf -> "is any of: $names"
        ListRuleOperator.AllOf -> "is all of: $names"
        ListRuleOperator.NoneOf -> "is none of: $names"
        ListRuleOperator.Under -> if (field.values == ListRuleValues.Year) "before ${high ?: ""}" else "under ${high ?: ""}"
        ListRuleOperator.Over -> if (field.values == ListRuleValues.Year) "after ${low ?: ""}" else "over ${low ?: ""}"
        ListRuleOperator.Between -> "between ${low ?: ""} and ${high ?: ""}"
        ListRuleOperator.InLast -> "in the last ${timeSpan()}"
        ListRuleOperator.NotInLast -> "not in the last ${timeSpan()}"
        ListRuleOperator.Is -> "yes"
        ListRuleOperator.IsNot -> "no"
    }
}

/**
 * The rule as a short pill on the list's page: "Tag: Russia and 19th century", "Genre: Classics or
 * Poetry", "Pages under 500", "Published 1850–1900", "Added in the last 30 days", "Not downloaded".
 */
fun ListRule.pill(valueLabel: (String) -> String = field::valueLabel): String {
    val shown = values.map(valueLabel)
    fun joined(word: String) = when (shown.size) {
        0 -> ""
        1 -> shown.single()
        else -> shown.dropLast(1).joinToString(", ") + " $word " + shown.last()
    }
    return when (operator) {
        ListRuleOperator.AnyOf -> "${field.label}: ${joined("or")}"
        ListRuleOperator.AllOf -> "${field.label}: ${joined("and")}"
        ListRuleOperator.NoneOf -> "${field.label}: not ${joined("or")}"
        ListRuleOperator.Under -> if (field.values == ListRuleValues.Year) "${field.label} before ${high ?: ""}" else "${field.label} under ${high ?: ""}"
        ListRuleOperator.Over -> if (field.values == ListRuleValues.Year) "${field.label} after ${low ?: ""}" else "${field.label} over ${low ?: ""}"
        ListRuleOperator.Between -> "${field.label} ${low ?: ""}–${high ?: ""}"
        ListRuleOperator.InLast -> "${field.label} in the last ${timeSpan()}"
        ListRuleOperator.NotInLast -> "${field.label} over ${timeSpan()} ago"
        ListRuleOperator.Is -> field.label
        ListRuleOperator.IsNot -> "Not ${field.label.lowercase()}"
    }
}

private fun ListRule.timeSpan(): String {
    val amount = low ?: return ""
    val unit = unit ?: ListRuleTimeUnit.Days
    return "$amount ${if (amount == 1) unit.singular else unit.plural}"
}

/**
 * Writes [this] as the line text a smart list keeps: `rules=2`, then `match=all|any`, then one
 * `rule=` line per rule and a `group=all|any` line opening each group, whose rules follow until `end`.
 */
fun ListRules.encode(): String = buildList {
    add("rules=$ListRulesVersion")
    add("match=${if (matchAll) "all" else "any"}")
    rules.forEach { add("rule=" + it.encodeLine()) }
    groups.forEach { group ->
        add("group=${if (group.matchAll) "all" else "any"}")
        group.rules.forEach { add("rule=" + it.encodeLine()) }
        add("end")
    }
}.joinToString("\n")

/** Reads what [encode] wrote; null for text that isn't rules (an older smart list keeps its filters instead). */
fun decodeListRules(text: String?): ListRules? {
    val lines = text?.lines()?.filter(String::isNotBlank) ?: return null
    if (lines.firstOrNull()?.startsWith("rules=") != true) return null
    var matchAll = true
    val rules = mutableListOf<ListRule>()
    val groups = mutableListOf<ListRuleGroup>()
    var group: ListRuleGroup? = null
    lines.drop(1).forEach { line ->
        when {
            line.startsWith("match=") -> matchAll = line.removePrefix("match=") != "any"
            line.startsWith("group=") -> group = ListRuleGroup(matchAll = line.removePrefix("group=") == "all")
            line == "end" -> group?.let { groups += it }.also { group = null }
            line.startsWith("rule=") -> decodeRuleLine(line.removePrefix("rule="))?.let { rule ->
                val open = group
                if (open != null) group = open.copy(rules = open.rules + rule) else rules += rule
            }
        }
    }
    group?.let { groups += it }
    return ListRules(matchAll = matchAll, rules = rules, groups = groups)
}

private const val ListRulesVersion = 2

private fun ListRule.encodeLine(): String = listOf(
    field.name,
    operator.name,
    values.joinToString(",") { URLEncoder.encode(it, Charsets.UTF_8.name()) },
    low?.toString().orEmpty(),
    high?.toString().orEmpty(),
    unit?.name.orEmpty(),
).joinToString("|")

private fun decodeRuleLine(line: String): ListRule? {
    val parts = line.split('|')
    if (parts.size < 6) return null
    val field = ListRuleField.entries.firstOrNull { it.name == parts[0] } ?: return null
    val operator = ListRuleOperator.entries.firstOrNull { it.name == parts[1] } ?: return null
    return ListRule(
        field = field,
        operator = operator,
        values = parts[2].split(',').filter(String::isNotEmpty).map { URLDecoder.decode(it, Charsets.UTF_8.name()) },
        low = parts[3].toIntOrNull(),
        high = parts[4].toIntOrNull(),
        unit = ListRuleTimeUnit.entries.firstOrNull { it.name == parts[5] },
    )
}

/**
 * The library's filters as rules (Filters › Save as list, and smart lists saved before rules):
 * the library's source, then a rule per filter in use. A page range that also takes books with no
 * page count can't be said as a rule and keeps only its bounds. Set in and When become tag rules of
 * their own: [placeTags] gives the tags a picked place stands for (a country and its cities),
 * [timeTags] the tags a picked pile holds ("1800s": "19th century", "1850s").
 */
fun LibraryFilterState.toListRules(
    sourceType: SourceType,
    placeTags: (String) -> List<String> = { place -> listOf(place) },
    timeTags: (String) -> List<String> = { time -> listOf(time) },
): ListRules = ListRules(
    matchAll = true,
    rules = buildList {
        add(ListRule(ListRuleField.Source, ListRuleOperator.AnyOf, listOf(sourceType.name)))
        if (formats.isNotEmpty()) add(ListRule(ListRuleField.Format, ListRuleOperator.AnyOf, formats.map { it.name }.sorted()))
        if (libraries.isNotEmpty()) add(ListRule(ListRuleField.Library, ListRuleOperator.AnyOf, libraries.sorted()))
        when (fileLocations.singleOrNull()) {
            LibraryFileLocation.LOCAL -> add(ListRule(ListRuleField.Downloaded, ListRuleOperator.Is))
            LibraryFileLocation.SERVER -> add(ListRule(ListRuleField.Downloaded, ListRuleOperator.IsNot))
            else -> Unit
        }
        if (readingStatuses.isNotEmpty()) {
            add(ListRule(ListRuleField.Status, ListRuleOperator.AnyOf, readingStatuses.map { it.name }.sorted()))
        }
        pages?.let { range ->
            val (min, max) = range.min to range.max
            when {
                min != null && max != null -> add(ListRule(ListRuleField.Pages, ListRuleOperator.Between, low = min, high = max))
                min != null -> add(ListRule(ListRuleField.Pages, ListRuleOperator.Over, low = min - 1))
                max != null -> add(ListRule(ListRuleField.Pages, ListRuleOperator.Under, high = max + 1))
            }
        }
        listOf(
            ListRuleField.Author to authors,
            ListRuleField.Series to series,
            ListRuleField.Publisher to publishers,
            ListRuleField.Genre to genres,
            ListRuleField.Tag to tags,
            ListRuleField.Language to languages,
            ListRuleField.Tag to places.flatMap(placeTags).distinct().toSet(),
            ListRuleField.Tag to times.flatMap(timeTags).distinct().toSet(),
            ListRuleField.Mood to moods,
        ).forEach { (field, values) ->
            if (values.isNotEmpty()) add(ListRule(field, ListRuleOperator.AnyOf, values.sorted()))
        }
    },
)

/** The tags of [items] a Set in pick stands for: the place itself and, for a country, its cities. */
fun libraryPlaceTags(place: String, items: List<ReadableItem>): List<String> =
    listOf(place) + items.asSequence()
        .flatMap { item -> item.facetMetadata().tags }
        .filter { tag -> libraryPlaceCountry(tag) == place }
        .distinct()

/** The tags of [items] a When pick stands for: the period itself, or every era tag in its pile. */
fun libraryTimeTags(time: String, items: List<ReadableItem>): List<String> =
    listOf(time) + items.asSequence()
        .flatMap { item -> item.facetMetadata().tags }
        .filter { tag -> libraryEraSlot(tag)?.label == time }
        .distinct()
