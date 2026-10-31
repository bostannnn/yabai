package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleContext
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRuleOperator
import com.example.unifiedreader.core.model.ListRuleSection
import com.example.unifiedreader.core.model.ListRuleTimeUnit
import com.example.unifiedreader.core.model.ListRuleValues
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.label
import com.example.unifiedreader.core.model.matches
import com.example.unifiedreader.core.model.namesOf
import com.example.unifiedreader.core.model.operators
import com.example.unifiedreader.core.model.summary
import com.example.unifiedreader.core.model.valueLabel
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppCheckbox
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppSearchField
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSheetDivider
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextField

/**
 * Add a rule: the fields a smart list can ask about, under About the book, Your reading and In
 * your library. For a magic shelf ([onServer]) the fields only this device knows are greyed.
 */
@Composable
internal fun ListRulePickerSheet(
    onServer: SourceType?,
    showShelf: Boolean,
    onPick: (ListRuleField) -> Unit,
    onDismiss: () -> Unit,
) {
    AppSideSheet(onDismissRequest = onDismiss) {
        AppPanelHeader(title = "Add a rule", onClose = { dismiss() })
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ListRuleSection.entries.forEachIndexed { index, section ->
                val fields = ListRuleField.entries.filter { field ->
                    field.section == section && field.isPickable && (field != ListRuleField.Shelf || showShelf)
                }
                if (fields.isEmpty()) return@forEachIndexed
                if (index > 0) AppSheetDivider()
                AppSheetLabel(text = section.label)
                fields.forEach { field ->
                    val unfit = onServer != null && field.onDeviceOnly
                    AppSettingsRow(
                        title = field.label,
                        supporting = if (unfit) "Lists on this device only" else field.hint,
                        enabled = !unfit,
                        onClick = { onPick(field) },
                    )
                }
            }
        }
    }
}

/**
 * A rule's values: how it asks (Any of / All of / None of, Under / Over / Between, In the last /
 * Not in the last), then what, and at the foot how many books in [pool] it matches now, beside
 * Save rule. Names come from the books themselves with how many have each; tags group under Set
 * in, When and About as Browse groups them.
 */
@Composable
internal fun ListRuleValueSheet(
    field: ListRuleField,
    initial: ListRule?,
    pool: List<ReadableItem>,
    context: ListRuleContext,
    shelfNames: List<String>,
    onBack: (() -> Unit)?,
    onSave: (ListRule) -> Unit,
    onDismiss: () -> Unit,
    // Where you sorted each tag (Set in, When, About), as Browse shows them.
    tagKindOf: (String) -> LibraryTagKind = { LibraryTagKind.THEME },
) {
    var rule by remember(field) { mutableStateOf(initial ?: newRule(field)) }
    val matching = remember(rule, pool, context) { if (rule.isComplete) pool.count { rule.matches(it, context) } else null }
    AppSideSheet(onDismissRequest = onDismiss) {
        AppPanelHeader(title = field.label, onBack = onBack, onClose = { dismiss() })
        val operators = field.operators()
        Column(
            modifier = Modifier.padding(start = AppLayout.inset, end = AppLayout.inset, bottom = AppLayout.headingGap),
            verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
        ) {
            AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
                operators.forEach { operator ->
                    AppSegment(
                        selected = rule.operator == operator,
                        onClick = { rule = rule.withOperator(operator) },
                        label = { Text(operator.label(field)) },
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            when (field.values) {
                ListRuleValues.Names, ListRuleValues.ReadingStatus -> NamesValues(
                    field = field,
                    rule = rule,
                    pool = pool,
                    context = context,
                    shelfNames = shelfNames,
                    tagKindOf = tagKindOf,
                    onChange = { rule = it },
                )
                ListRuleValues.Number, ListRuleValues.Year -> NumberValues(rule = rule, onChange = { rule = it })
                ListRuleValues.Time -> TimeValues(rule = rule, onChange = { rule = it })
                ListRuleValues.YesNo -> Unit
            }
        }
        AppSheetDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppLayout.inset),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (matching != null) {
                    "${field.label} ${rule.summary()} · ${bookCountLabel(matching).lowercase()} now"
                } else {
                    field.missingHint()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AppButton(onClick = { onSave(rule.cleaned()) }, enabled = rule.isComplete) {
                Text(text = "Save rule", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun newRule(field: ListRuleField): ListRule = ListRule(
    field = field,
    operator = field.operators().first(),
    unit = ListRuleTimeUnit.Days.takeIf { field.values == ListRuleValues.Time },
    low = 30.takeIf { field.values == ListRuleValues.Time },
)

/** The rule asked another way, keeping what still makes sense: the names, or the number typed. */
private fun ListRule.withOperator(operator: ListRuleOperator): ListRule = when (operator) {
    ListRuleOperator.Under -> copy(operator = operator, high = high ?: low, low = null)
    ListRuleOperator.Over -> copy(operator = operator, low = low ?: high, high = null)
    else -> copy(operator = operator)
}

/** What a rule keeps once saved: only the values its operator reads. */
private fun ListRule.cleaned(): ListRule = when (operator) {
    ListRuleOperator.Under -> copy(low = null, values = emptyList())
    ListRuleOperator.Over -> copy(high = null, values = emptyList())
    ListRuleOperator.Between -> copy(values = emptyList())
    ListRuleOperator.InLast, ListRuleOperator.NotInLast -> copy(high = null, values = emptyList())
    ListRuleOperator.Is, ListRuleOperator.IsNot -> copy(values = emptyList(), low = null, high = null)
    else -> copy(low = null, high = null, unit = null)
}

private fun ListRuleField.missingHint(): String = when (values) {
    ListRuleValues.Names, ListRuleValues.ReadingStatus -> "Pick at least one."
    ListRuleValues.Number, ListRuleValues.Year -> "Type a number."
    ListRuleValues.Time -> "Type how long."
    ListRuleValues.YesNo -> ""
}

/** A names field's values with how many books in [pool] have each, the most common first. */
internal fun ruleValueCounts(field: ListRuleField, pool: List<ReadableItem>, context: ListRuleContext): List<Pair<String, Int>> {
    if (field == ListRuleField.Status) {
        val counts = pool.groupingBy { context.readingStatus(it) }.eachCount()
        return LibraryReadingStatus.entries.map { status -> status.name to (counts[status] ?: 0) }
    }
    val counts = HashMap<String, Int>()
    val spelling = HashMap<String, String>()
    pool.forEach { item ->
        field.namesOf(item).forEach { value ->
            val key = value.trim().lowercase()
            if (key.isEmpty()) return@forEach
            spelling.putIfAbsent(key, value.trim())
            counts[key] = (counts[key] ?: 0) + 1
        }
    }
    return counts.entries
        .map { (key, count) -> spelling.getValue(key) to count }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first })
}

@Composable
private fun NamesValues(
    field: ListRuleField,
    rule: ListRule,
    pool: List<ReadableItem>,
    context: ListRuleContext,
    shelfNames: List<String>,
    tagKindOf: (String) -> LibraryTagKind,
    onChange: (ListRule) -> Unit,
) {
    var query by rememberSaveable(field) { mutableStateOf("") }
    val counted = remember(field, pool, context, shelfNames) {
        val counts = ruleValueCounts(field, pool, context)
        val known = counts.mapTo(HashSet()) { it.first.lowercase() }
        // A shelf with no book of this library on it still shows, at 0.
        counts + shelfNames.filter { field == ListRuleField.Shelf && it.lowercase() !in known }.map { it to 0 }
    }
    // A value saved before that no book has now stays, ticked, so it can be taken out.
    val values = remember(counted, rule.values) {
        val shown = counted.mapTo(HashSet()) { it.first.lowercase() }
        rule.values.filter { it.lowercase() !in shown }.map { it to 0 } + counted
    }
    val chosen = remember(rule.values) { rule.values.mapTo(HashSet()) { it.lowercase() } }
    val trimmed = query.trim()
    val shown = if (trimmed.isEmpty()) values else values.filter { (value, _) -> field.valueLabel(value).contains(trimmed, ignoreCase = true) }
    val sections: List<Pair<String?, List<Pair<String, Int>>>> = if (field == ListRuleField.Tag) {
        LibraryTagKind.entries.map { kind -> kind.label() to shown.filter { tagKindOf(it.first) == kind } }.filter { it.second.isNotEmpty() }
    } else {
        listOf(null to shown)
    }
    fun toggle(value: String) {
        val on = value.lowercase() in chosen
        onChange(rule.copy(values = if (on) rule.values.filterNot { it.equals(value, ignoreCase = true) } else rule.values + value))
    }
    if (values.size > SearchFromCount) {
        AppSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Search ${values.size} ${field.plural()}",
            modifier = Modifier.fillMaxWidth().padding(start = AppLayout.inset, end = AppLayout.inset, bottom = AppLayout.headingGap),
        )
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        sections.forEach { (title, sectionValues) ->
            if (title != null) item(key = "label:$title") { AppSheetLabel(text = title) }
            items(sectionValues, key = { (value, _) -> "value:${title.orEmpty()}:$value" }) { (value, count) ->
                val on = value.lowercase() in chosen
                AppSettingsRow(
                    title = field.valueLabel(value),
                    value = count.toString(),
                    onClick = { toggle(value) },
                    showChevron = false,
                    trailing = { AppCheckbox(checked = on, onCheckedChange = { toggle(value) }) },
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "none") {
                Text(
                    text = if (trimmed.isEmpty()) "No book in your library has any ${field.plural()} yet." else "Nothing matches “$trimmed”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AppLayout.inset),
                )
            }
        }
    }
}

/** "Set in", "When", "About": tags grouped as Browse groups them. */
private fun LibraryTagKind.label(): String = when (this) {
    LibraryTagKind.PLACE -> "Set in"
    LibraryTagKind.ERA -> "When"
    LibraryTagKind.THEME -> "About"
}

/** "authors", "series", "shelves": a names field's values, said together. */
private fun ListRuleField.plural(): String = when (this) {
    ListRuleField.Series -> "series"
    ListRuleField.Shelf -> "shelves"
    ListRuleField.Library -> "libraries"
    ListRuleField.Status -> "statuses"
    else -> label.lowercase() + "s"
}

/** A values list longer than this gets a search field. */
private const val SearchFromCount = 12

@Composable
private fun NumberValues(rule: ListRule, onChange: (ListRule) -> Unit) {
    val year = rule.field.values == ListRuleValues.Year
    Column(
        modifier = Modifier.padding(horizontal = AppLayout.inset),
        verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
    ) {
        when (rule.operator) {
            ListRuleOperator.Under -> NumberField(label = if (year) "Before" else "Under", value = rule.high, onChange = { onChange(rule.copy(high = it)) })
            ListRuleOperator.Over -> NumberField(label = if (year) "After" else "Over", value = rule.low, onChange = { onChange(rule.copy(low = it)) })
            else -> Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                NumberField(label = "From", value = rule.low, onChange = { onChange(rule.copy(low = it)) }, modifier = Modifier.weight(1f))
                NumberField(label = "To", value = rule.high, onChange = { onChange(rule.copy(high = it)) }, modifier = Modifier.weight(1f))
            }
        }
        Text(
            text = if (year) "Years the books came out; a book with no year doesn't match." else "A book with no page count doesn't match.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeValues(rule: ListRule, onChange: (ListRule) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = AppLayout.inset),
        verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
    ) {
        NumberField(label = "How long", value = rule.low, onChange = { onChange(rule.copy(low = it)) }, modifier = Modifier.fillMaxWidth())
        AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
            ListRuleTimeUnit.entries.forEach { unit ->
                AppSegment(
                    selected = rule.unit == unit,
                    onClick = { onChange(rule.copy(unit = unit)) },
                    label = { Text(unit.plural.replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        if (rule.field == ListRuleField.LastRead && rule.operator == ListRuleOperator.NotInLast) {
            Text(
                text = "Books you have opened, but not for that long; a book never opened doesn't match.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    AppTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(6).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
