package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRuleGroup
import com.example.unifiedreader.core.model.ListRuleOperator
import com.example.unifiedreader.core.model.ListRuleValues
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.summary
import com.example.unifiedreader.core.model.valueLabel
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppRoundCheck
import com.example.unifiedreader.ui.components.AppSectionHeading
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.appCount

// The smart list editor's sections: where it is kept, its rules and the books they match.

/** Where a new smart list is kept: one row per place, the check first, each with what it means. */
@Composable
internal fun SmartListKeepItPanel(
    onServer: SourceType?,
    servers: List<SourceType>,
    onChange: (SourceType?) -> Unit,
) {
    AppPanel(modifier = Modifier.fillMaxWidth()) {
        KeepItPlaceRow(
            title = "On this device",
            line = "Books from every source, by what this device knows about them.",
            selected = onServer == null,
            onClick = { onChange(null) },
        )
        servers.forEach { server ->
            AppSettingsRowDivider()
            KeepItPlaceRow(
                title = "On ${server.displayName()}",
                line = "A magic shelf, also in ${server.displayName()}'s web app. ${server.displayName()} books only.",
                selected = onServer == server,
                onClick = { onChange(server) },
            )
        }
    }
}

@Composable
private fun KeepItPlaceRow(title: String, line: String, selected: Boolean, onClick: () -> Unit) {
    AppSettingsRow(
        title = title,
        supporting = line,
        onClick = onClick,
        showChevron = false,
        leading = { AppRoundCheck(checked = selected) },
        modifier = Modifier.semantics { this.selected = selected },
    )
}

/**
 * The rules as one panel: "Books that match" all or any (once there are two rules or a group),
 * a row per rule with how many books it picks on its own ([counts]), the groups indented under
 * their own all or any, then Add rule and Add a group.
 */
@Composable
internal fun SmartListRulesPanel(
    rules: ListRules,
    counts: Map<ListRule, Int>,
    onServer: SourceType?,
    onRulesChange: (ListRules) -> Unit,
    onEditRule: (RuleSlot) -> Unit,
    onAddGroup: () -> Unit,
) {
    AppPanel(modifier = Modifier.fillMaxWidth()) {
        // Rows read top to bottom; each after the first gets a divider.
        var hasRow = false
        if (rules.rules.size + rules.groups.size > 1) {
            JoinRow(
                title = "Books that match",
                labels = "All of these" to "Any of these",
                matchAll = rules.matchAll,
                onChange = { all -> onRulesChange(rules.copy(matchAll = all)) },
                width = SmartListEditorDefaults.joinWidth,
            )
            hasRow = true
        }
        rules.rules.forEachIndexed { index, rule ->
            if (hasRow) AppSettingsRowDivider()
            RuleRow(
                rule = rule,
                count = counts[rule],
                onServer = onServer,
                inset = AppSettingsDefaults.horizontalPadding,
                onClick = { onEditRule(RuleSlot(group = null, index = index)) },
                onRemove = { onRulesChange(rules.copy(rules = rules.rules.filterIndexed { i, _ -> i != index })) },
            )
            hasRow = true
        }
        rules.groups.forEachIndexed { groupIndex, group ->
            fun change(changed: ListRuleGroup) = onRulesChange(rules.copy(groups = rules.groups.mapIndexed { i, g -> if (i == groupIndex) changed else g }))
            if (hasRow) AppSettingsRowDivider(inset = AppSettingsDefaults.horizontalPadding)
            JoinRow(
                title = if (rules.matchAll) "And this group" else "Or this group",
                labels = "All" to "Any",
                matchAll = group.matchAll,
                onChange = { all -> change(group.copy(matchAll = all)) },
                width = SmartListEditorDefaults.groupJoinWidth,
                icon = AppIcons.FilterList,
                onRemove = { onRulesChange(rules.copy(groups = rules.groups.filterIndexed { i, _ -> i != groupIndex })) },
            )
            hasRow = true
            group.rules.forEachIndexed { index, rule ->
                AppSettingsRowDivider(inset = GroupRuleInset + AppSettingsDefaults.iconSize + AppSettingsDefaults.iconGap)
                RuleRow(
                    rule = rule,
                    count = counts[rule],
                    onServer = onServer,
                    inset = GroupRuleInset,
                    onClick = { onEditRule(RuleSlot(group = groupIndex, index = index)) },
                    onRemove = { change(group.copy(rules = group.rules.filterIndexed { i, _ -> i != index })) },
                )
            }
            AppSettingsRowDivider(inset = GroupRuleInset + AppSettingsDefaults.iconSize + AppSettingsDefaults.iconGap)
            AppSettingsRow(
                title = "Add a rule to the group",
                icon = AppIcons.Add,
                onClick = { onEditRule(RuleSlot(group = groupIndex, index = null)) },
                showChevron = false,
                horizontalInset = GroupRuleInset,
            )
        }
        val none = rules.isEmpty
        if (hasRow) AppSettingsRowDivider()
        AppSettingsRow(
            title = "Add rule",
            supporting = if (none) "Author, genre, tag, mood, pages, status and more" else null,
            icon = AppIcons.Add,
            onClick = { onEditRule(RuleSlot(group = null, index = null)) },
            showChevron = false,
        )
        if (!none) {
            AppSettingsRowDivider()
            AppSettingsRow(
                title = "Add a group",
                supporting = "Rules where any one is enough",
                icon = AppIcons.Add,
                onClick = onAddGroup,
                showChevron = false,
            )
        }
    }
}

/** "Books that match [All of these | Any of these]", or a group's own row with its remove button. */
@Composable
private fun JoinRow(
    title: String,
    labels: Pair<String, String>,
    matchAll: Boolean,
    onChange: (Boolean) -> Unit,
    width: Dp,
    icon: ImageVector? = null,
    onRemove: (() -> Unit)? = null,
) {
    AppSettingsRow(
        title = title,
        icon = icon,
        showChevron = false,
        trailing = {
            AppSegmentedRow(modifier = Modifier.width(width)) {
                AppSegment(selected = matchAll, onClick = { onChange(true) }, label = { Text(labels.first, maxLines = 1) })
                AppSegment(selected = !matchAll, onClick = { onChange(false) }, label = { Text(labels.second, maxLines = 1) })
            }
            if (onRemove != null) {
                AppIconButton(onClick = onRemove) {
                    Icon(imageVector = AppIcons.Close, contentDescription = "Remove the group")
                }
            }
        },
    )
}

@Composable
private fun RuleRow(
    rule: ListRule,
    count: Int?,
    onServer: SourceType?,
    inset: Dp,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val unfit = onServer != null && rule.field.onDeviceOnly
    val line = when {
        unfit -> "${rule.phrase()} · lists on this device only"
        count != null -> "${rule.phrase()} · ${appCount(count)} ${if (count == 1) "book" else "books"}"
        else -> rule.phrase()
    }
    AppSettingsRow(
        title = rule.field.label,
        supporting = line,
        supportingColor = if (unfit) MaterialTheme.colorScheme.error else Color.Unspecified,
        icon = rule.field.icon(),
        onClick = onClick,
        showChevron = false,
        horizontalInset = inset,
        trailing = {
            AppIconButton(onClick = onRemove) {
                Icon(imageVector = AppIcons.Close, contentDescription = "Remove the ${rule.field.label} rule")
            }
        },
    )
}

/** What a rule asks in plain words, after its field's name: "Fantasy or Horror", "Under 400 pages", "In the last 6 months". */
internal fun ListRule.phrase(): String {
    val shown = values.map(field::valueLabel)
    fun joined(word: String): String = when {
        shown.size <= 1 -> shown.firstOrNull().orEmpty()
        shown.size > PhraseValueLimit -> shown.take(PhraseValueLimit - 1).joinToString(", ") + " $word ${shown.size - PhraseValueLimit + 1} more"
        else -> shown.dropLast(1).joinToString(", ") + " $word " + shown.last()
    }
    val pages = if (field == ListRuleField.Pages) " pages" else ""
    val year = field.values == ListRuleValues.Year
    return when (operator) {
        ListRuleOperator.AnyOf -> joined("or")
        ListRuleOperator.AllOf -> joined("and")
        ListRuleOperator.NoneOf -> "Not ${joined("or")}"
        ListRuleOperator.Under -> if (year) "Before ${high ?: ""}" else "Under ${high ?: ""}$pages"
        ListRuleOperator.Over -> if (year) "After ${low ?: ""}" else "Over ${low ?: ""}$pages"
        ListRuleOperator.Between -> "${low ?: ""}–${high ?: ""}$pages"
        ListRuleOperator.Is -> "Yes"
        ListRuleOperator.IsNot -> "No"
        else -> summary().replaceFirstChar(Char::uppercaseChar)
    }
}

/** The icon a rule's row leads with. */
@Composable
private fun ListRuleField.icon(): ImageVector = when (this) {
    ListRuleField.Genre, ListRuleField.Tag, ListRuleField.Mood -> AppIcons.Sell
    ListRuleField.Pages -> AppIcons.MenuBook
    ListRuleField.Status -> AppIcons.Checklist
    ListRuleField.Published, ListRuleField.LastRead, ListRuleField.Added -> AppIcons.History
    ListRuleField.Series, ListRuleField.Shelf -> AppIcons.CollectionsBookmark
    ListRuleField.Downloaded -> AppIcons.Download
    ListRuleField.Source -> AppIcons.Cloud
    ListRuleField.Format, ListRuleField.Library -> AppIcons.Folder
    ListRuleField.Author, ListRuleField.Language, ListRuleField.Publisher -> AppIcons.Description
}

/**
 * "Matches 1,284", what they are worked out from, then the covers: a grid that fills the pane
 * ([asGrid], beside the form) or one row that scrolls sideways (under the rules). No rule yet, or
 * nothing that fits, says so; [emptiedBy] is the rule that leaves nothing, with what would match
 * without it.
 */
@Composable
internal fun SmartListMatches(
    matches: List<UserListEntry>,
    itemsById: Map<String, ReadableItem>,
    hasRules: Boolean,
    emptiedBy: Pair<ListRule, Int>?,
    asGrid: Boolean,
    cover: ListCover,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
        AppSectionHeading(text = "Matches", count = if (hasRules) appCount(matches.size) else null)
        when {
            !hasRules -> MatchesNote(title = "No rules yet", line = "Add a rule and the books it picks show here.")
            matches.isEmpty() -> MatchesNote(
                title = "No book fits every rule",
                line = emptiedBy?.let { (rule, count) ->
                    "Without “${rule.field.label}: ${rule.phrase()}”, ${appCount(count)} ${if (count == 1) "book" else "books"} would match."
                } ?: "Take a rule out, or match any of them instead of all.",
            )
            else -> {
                Text(
                    text = "Worked out from what this device knows",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (asGrid) MatchesGrid(matches, itemsById, cover) else MatchesStrip(matches, itemsById, cover)
            }
        }
    }
}

@Composable
private fun MatchesNote(title: String, line: String) {
    AppPanel(modifier = Modifier.fillMaxWidth()) {
        AppSettingsRow(title = title, supporting = line)
    }
}

/** As many columns as fit at [SmartListEditorDefaults.matchCoverMinWidth], three rows of them, then how many more. */
@Composable
private fun MatchesGrid(matches: List<UserListEntry>, itemsById: Map<String, ReadableItem>, cover: ListCover) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = AppLayout.gridGap
        val columns = ((maxWidth + gap) / (SmartListEditorDefaults.matchCoverMinWidth + gap)).toInt().coerceAtLeast(2)
        val shown = matches.take(columns * MatchRows)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            shown.chunked(columns).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    line.forEach { entry ->
                        Box(modifier = Modifier.weight(1f)) {
                            cover(itemsById[entry.itemId] ?: entry.asReadableItem(), Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                        }
                    }
                    repeat(columns - line.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
            MoreMatches(matches.size - shown.size)
        }
    }
}

@Composable
private fun MatchesStrip(matches: List<UserListEntry>, itemsById: Map<String, ReadableItem>, cover: ListCover) {
    val shown = matches.take(MatchStripCount)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
    ) {
        shown.forEach { entry ->
            cover(
                itemsById[entry.itemId] ?: entry.asReadableItem(),
                Modifier.width(SmartListEditorDefaults.matchStripCoverWidth).aspectRatio(2f / 3f),
            )
        }
    }
    MoreMatches(matches.size - shown.size)
}

@Composable
private fun MoreMatches(count: Int) {
    if (count <= 0) return
    Text(
        text = "and ${appCount(count)} more",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// A group's rules start under the group row's title.
private val GroupRuleInset: Dp = AppSettingsDefaults.horizontalPadding + AppSettingsDefaults.iconSize + AppSettingsDefaults.iconGap
private const val PhraseValueLimit = 4
private const val MatchRows = 3
private const val MatchStripCount = 12
