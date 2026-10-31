package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRuleGroup
import com.example.unifiedreader.core.model.ListRuleOperator
import com.example.unifiedreader.core.model.ListRuleTimeUnit
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.operators
import com.example.unifiedreader.core.model.pill
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * A smart list's rules as a Grimmory magic shelf's filterJson, and back. Grimmory's rules are a group
 * {"type":"group","join":"and"|"or","rules":[...]} whose rules are {"field","operator","value",
 * "valueStart","valueEnd"} or further groups; the server (BookRuleEvaluatorService) reads a missing
 * join as "or", an empty group as "every book", and skips a rule it can't parse.
 */

/**
 * The rules as Grimmory's filterJson: the list's own rules first, then each group as a nested group
 * (an empty group is left out: Grimmory would read it as "every book"). [shelfIdByName] gives the
 * Grimmory id of a shelf the rules name.
 *
 * Throws [IllegalArgumentException] naming the rule when one can't be said to Grimmory (Downloaded,
 * Source, Library, an unfinished rule, a shelf name with no id).
 */
fun ListRules.toGrimmoryFilterJson(shelfIdByName: (String) -> String?): String {
    val nested = groups.filter { it.rules.isNotEmpty() }.map { group ->
        grimmoryGroup(group.matchAll, group.rules.map { it.toGrimmoryRule(shelfIdByName) })
    }
    return grimmoryGroup(matchAll, rules.map { it.toGrimmoryRule(shelfIdByName) } + nested).toString()
}

/**
 * Grimmory's filterJson as the app's rules. Null when the JSON holds anything the app's rules can't
 * say: a field or operator it doesn't know, a group inside a group, a value it can't map (a status like
 * WONT_READ, a file type it doesn't read, a shelf id [shelfNameById] doesn't know), or JSON that isn't
 * a rule group.
 */
fun grimmoryFilterJsonToListRules(json: String, shelfNameById: (String) -> String?): ListRules? {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
    // The server reads the top level as a group whatever its "type" says.
    val matchAll = root.joinMatchesAll() ?: return null
    val rules = mutableListOf<ListRule>()
    val groups = mutableListOf<ListRuleGroup>()
    for (element in root.ruleElements() ?: return null) {
        val obj = element as? JsonObject ?: return null
        if (!obj.isGroup()) {
            rules += obj.toListRule(shelfNameById) ?: return null
            continue
        }
        val groupMatchAll = obj.joinMatchesAll() ?: return null
        val groupRules = (obj.ruleElements() ?: return null).map { member ->
            val rule = member as? JsonObject ?: return null
            if (rule.isGroup()) return null
            rule.toListRule(shelfNameById) ?: return null
        }
        when {
            groupRules.isNotEmpty() -> groups += ListRuleGroup(matchAll = groupMatchAll, rules = groupRules)
            // An empty group is "every book" to Grimmory: nothing under "and", everything under "or".
            !matchAll -> return null
        }
    }
    return ListRules(matchAll = matchAll, rules = rules, groups = groups)
}

private const val GroupType = "group"
private const val IncludesAny = "includes_any"
private const val IncludesAll = "includes_all"
private const val ExcludesAll = "excludes_all"
private const val Equals = "equals"
private const val NotEquals = "not_equals"
private const val LessThan = "less_than"
private const val LessThanOrEqual = "less_than_equal_to"
private const val GreaterThan = "greater_than"
private const val GreaterThanOrEqual = "greater_than_equal_to"
private const val InBetween = "in_between"
private const val WithinLast = "within_last"
private const val OlderThan = "older_than"

/** The Grimmory field each app field is written as. Downloaded, Source and Library have none. */
private val GrimmoryFieldNames = mapOf(
    ListRuleField.Author to "authors",
    ListRuleField.Genre to "categories",
    ListRuleField.Tag to "tags",
    ListRuleField.Mood to "moods",
    ListRuleField.Shelf to "shelf",
    ListRuleField.Series to "seriesName",
    ListRuleField.Language to "language",
    ListRuleField.Publisher to "publisher",
    ListRuleField.Format to "fileType",
    ListRuleField.Status to "readStatus",
    ListRuleField.Pages to "pageCount",
    ListRuleField.Published to "publishedDate",
    ListRuleField.LastRead to "lastReadTime",
    ListRuleField.Added to "addedOn",
)

private val AppFieldsByGrimmoryName: Map<String, ListRuleField> =
    GrimmoryFieldNames.entries.associate { (field, name) -> name to field } + ("genre" to ListRuleField.Genre)

/** The fields Grimmory keeps several values of per book, where "includes all" means all of them. */
private val GrimmoryArrayFields = setOf(
    ListRuleField.Author,
    ListRuleField.Genre,
    ListRuleField.Tag,
    ListRuleField.Mood,
    ListRuleField.Shelf,
)

/** Grimmory's reading statuses for each of the app's; UNSET is a book never given one. */
private val GrimmoryReadStatuses = mapOf(
    LibraryReadingStatus.NOT_STARTED to listOf("UNREAD", "UNSET"),
    LibraryReadingStatus.IN_PROGRESS to listOf("READING", "RE_READING", "PARTIALLY_READ", "PAUSED"),
    LibraryReadingStatus.FINISHED to listOf("READ"),
)

private val AppReadStatusesByGrimmoryName: Map<String, LibraryReadingStatus> =
    GrimmoryReadStatuses.entries.flatMap { (status, names) -> names.map { it to status } }.toMap()

private val IsoDate = Regex("""^(\d{4})-\d{2}-\d{2}(?:[T ].*)?$""")
private val FirstOfJanuary = Regex("""^(\d{4})-01-01$""")
private val LastOfDecember = Regex("""^(\d{4})-12-31$""")

// Writing

private fun grimmoryGroup(matchAll: Boolean, rules: List<JsonElement>): JsonObject = buildJsonObject {
    put("type", GroupType)
    put("join", if (matchAll) "and" else "or")
    put("rules", JsonArray(rules))
}

private fun grimmoryRule(
    field: String,
    operator: String,
    value: JsonElement? = null,
    valueStart: JsonElement? = null,
    valueEnd: JsonElement? = null,
): JsonObject = buildJsonObject {
    put("field", field)
    put("operator", operator)
    value?.let { put("value", it) }
    valueStart?.let { put("valueStart", it) }
    valueEnd?.let { put("valueEnd", it) }
}

private fun ListRule.cantSay(): IllegalArgumentException =
    IllegalArgumentException("A Grimmory magic shelf can't hold the rule “${pill()}”.")

private fun ListRule.toGrimmoryRule(shelfIdByName: (String) -> String?): JsonObject {
    val grimmoryField = GrimmoryFieldNames[field] ?: throw cantSay()
    if (operator !in field.operators()) throw cantSay()
    require(isComplete) { "The rule “${field.label}” isn't finished." }
    return when (field) {
        ListRuleField.Pages -> when (operator) {
            ListRuleOperator.Under -> grimmoryRule(grimmoryField, LessThan, JsonPrimitive(high))
            ListRuleOperator.Over -> grimmoryRule(grimmoryField, GreaterThan, JsonPrimitive(low))
            else -> grimmoryRule(grimmoryField, InBetween, valueStart = JsonPrimitive(low), valueEnd = JsonPrimitive(high))
        }
        ListRuleField.Published -> when (operator) {
            ListRuleOperator.Under -> grimmoryRule(grimmoryField, LessThan, JsonPrimitive(firstOfJanuary(high!!)))
            ListRuleOperator.Over -> grimmoryRule(grimmoryField, GreaterThan, JsonPrimitive(lastOfDecember(low!!)))
            else -> grimmoryRule(
                grimmoryField,
                InBetween,
                valueStart = JsonPrimitive(firstOfJanuary(low!!)),
                valueEnd = JsonPrimitive(lastOfDecember(high!!)),
            )
        }
        ListRuleField.LastRead, ListRuleField.Added -> grimmoryRule(
            grimmoryField,
            if (operator == ListRuleOperator.InLast) WithinLast else OlderThan,
            value = JsonPrimitive(low),
            valueEnd = JsonPrimitive(
                when (unit!!) {
                    ListRuleTimeUnit.Days -> "days"
                    ListRuleTimeUnit.Weeks -> "weeks"
                    ListRuleTimeUnit.Months -> "months"
                },
            ),
        )
        else -> grimmoryRule(
            grimmoryField,
            when (operator) {
                ListRuleOperator.AllOf -> IncludesAll
                ListRuleOperator.NoneOf -> ExcludesAll
                else -> IncludesAny
            },
            value = JsonArray(grimmoryValues(shelfIdByName)),
        )
    }
}

/** A names or status rule's values as Grimmory says them. */
private fun ListRule.grimmoryValues(shelfIdByName: (String) -> String?): List<JsonPrimitive> = when (field) {
    ListRuleField.Format -> values.map { value ->
        val format = FormatType.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: throw cantSay()
        JsonPrimitive(format.name.lowercase())
    }
    ListRuleField.Status -> values
        .flatMap { value ->
            val status = LibraryReadingStatus.entries.firstOrNull { it.name == value } ?: throw cantSay()
            GrimmoryReadStatuses.getValue(status)
        }
        .distinct()
        .map(::JsonPrimitive)
    ListRuleField.Shelf -> values.map { name ->
        val id = shelfIdByName(name) ?: throw IllegalArgumentException("Grimmory has no shelf called “$name”.")
        // The web app keeps shelf ids as numbers; the server reads either.
        id.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(id)
    }
    else -> values.map(::JsonPrimitive)
}

private fun firstOfJanuary(year: Int): String = "%04d-01-01".format(Locale.ROOT, year)

private fun lastOfDecember(year: Int): String = "%04d-12-31".format(Locale.ROOT, year)

// Reading

private fun JsonObject.isGroup(): Boolean = (this["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content == GroupType

/** True for "and", false for "or" or none (as the server reads it), null for anything else. */
private fun JsonObject.joinMatchesAll(): Boolean? = when (val join = this["join"]) {
    null, JsonNull -> false
    is JsonPrimitive -> when (join.content.takeIf { join.isString }) {
        "and" -> true
        "or" -> false
        else -> null
    }
    else -> null
}

/** A group's members, nulls skipped as the server skips them; null when "rules" isn't a list. */
private fun JsonObject.ruleElements(): List<JsonElement>? = when (val rules = this["rules"]) {
    null, JsonNull -> emptyList()
    is JsonArray -> rules.filter { it != JsonNull }
    else -> null
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.toListRule(shelfNameById: (String) -> String?): ListRule? {
    val field = text("field")?.let(AppFieldsByGrimmoryName::get) ?: return null
    val operator = text("operator") ?: return null
    return when (field) {
        ListRuleField.Pages -> readPages(operator)
        ListRuleField.Published -> readPublished(operator)
        ListRuleField.LastRead, ListRuleField.Added -> readTime(field, operator)
        else -> readNames(field, operator, shelfNameById)
    }
}

private fun JsonObject.readNames(
    field: ListRuleField,
    operator: String,
    shelfNameById: (String) -> String?,
): ListRule? {
    val isArrayField = field in GrimmoryArrayFields
    val appOperator = when (operator) {
        IncludesAny, Equals -> ListRuleOperator.AnyOf
        // On a field with one value per book the server reads "includes all" as "includes any".
        IncludesAll -> if (isArrayField) ListRuleOperator.AllOf else ListRuleOperator.AnyOf
        ExcludesAll, NotEquals -> ListRuleOperator.NoneOf
        else -> return null
    }
    val raw = when (val value = this["value"]) {
        is JsonArray -> {
            // "equals" compares a one-value field with the list's text, which matches nothing.
            if ((operator == Equals || operator == NotEquals) && !isArrayField) return null
            value.map { element -> (element as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content ?: return null }
        }
        is JsonPrimitive -> if (value == JsonNull) emptyList() else listOf(value.content)
        else -> emptyList()
    }
    if (raw.isEmpty()) return null
    val values = when (field) {
        ListRuleField.Format -> raw.map { extension ->
            FormatType.entries.firstOrNull { it.name.equals(extension, ignoreCase = true) }?.name ?: return null
        }
        ListRuleField.Status -> raw.map { status -> AppReadStatusesByGrimmoryName[status]?.name ?: return null }
        ListRuleField.Shelf -> raw.map { id -> shelfNameById(id) ?: return null }
        else -> raw
    }.distinct()
    return ListRule(field, appOperator, values = values)
}

private fun JsonObject.readPages(operator: String): ListRule? {
    fun number(key: String): Int? = (this[key] as? JsonPrimitive)?.wholeNumber()
    val field = ListRuleField.Pages
    return when (operator) {
        LessThan -> ListRule(field, ListRuleOperator.Under, high = number("value") ?: return null)
        LessThanOrEqual -> ListRule(field, ListRuleOperator.Under, high = (number("value") ?: return null) + 1)
        GreaterThan -> ListRule(field, ListRuleOperator.Over, low = number("value") ?: return null)
        GreaterThanOrEqual -> ListRule(field, ListRuleOperator.Over, low = (number("value") ?: return null) - 1)
        Equals -> number("value")?.let { pages -> ListRule(field, ListRuleOperator.Between, low = pages, high = pages) }
        InBetween -> ListRule(
            field,
            ListRuleOperator.Between,
            low = number("valueStart") ?: return null,
            high = number("valueEnd") ?: return null,
        )
        else -> null
    }
}

private fun JsonObject.readPublished(operator: String): ListRule? {
    fun year(key: String, pattern: Regex): Int? = text(key)?.let(pattern::matchEntire)?.groupValues?.get(1)?.toIntOrNull()
    val field = ListRuleField.Published
    return when (operator) {
        LessThan -> ListRule(field, ListRuleOperator.Under, high = year("value", FirstOfJanuary) ?: return null)
        GreaterThan -> ListRule(field, ListRuleOperator.Over, low = year("value", LastOfDecember) ?: return null)
        InBetween -> ListRule(
            field,
            ListRuleOperator.Between,
            low = year("valueStart", IsoDate) ?: return null,
            high = year("valueEnd", IsoDate) ?: return null,
        )
        else -> null
    }
}

private fun JsonObject.readTime(field: ListRuleField, operator: String): ListRule? {
    val appOperator = when (operator) {
        WithinLast -> ListRuleOperator.InLast
        OlderThan -> ListRuleOperator.NotInLast
        else -> return null
    }
    val amount = (this["value"] as? JsonPrimitive)?.wholeNumber()?.takeIf { it > 0 } ?: return null
    // As the server reads it: no unit, or one it doesn't know, is days.
    val (low, unit) = when ((this["valueEnd"] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content?.lowercase()) {
        "weeks" -> amount to ListRuleTimeUnit.Weeks
        "months" -> amount to ListRuleTimeUnit.Months
        "years" -> amount * 12 to ListRuleTimeUnit.Months
        else -> amount to ListRuleTimeUnit.Days
    }
    return ListRule(field, appOperator, low = low, unit = unit)
}

/** A whole number, from a JSON number or a numeric string ("200", 200, 200.0); null otherwise. */
private fun JsonPrimitive.wholeNumber(): Int? {
    if (this == JsonNull) return null
    val text = content.trim()
    text.toIntOrNull()?.let { return it }
    val number = text.toDoubleOrNull() ?: return null
    return number.takeIf { it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
}
