package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.ListRule
import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRuleGroup
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.summary
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppSectionHeading
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.currentAppWindowWidth

/**
 * What the smart list editor changes, saved together with Create or Done: the name, where the
 * list lives ([onServer]: Grimmory, a magic shelf; fixed once made), its rules, order and how many.
 */
data class SmartListDraft(
    val listId: String? = null,
    val name: String = "",
    val onServer: SourceType? = null,
    val rules: ListRules = ListRules(),
    val order: UserListOrder = UserListOrder.DateAdded,
    val limit: Int? = null,
) {
    val isNew: Boolean
        get() = listId == null

    /** Rules without the groups left empty, as they are saved. */
    val savedRules: ListRules
        get() = rules.copy(groups = rules.groups.filter { it.rules.isNotEmpty() })

    /** A name and at least one rule, each whole, and none a magic shelf can't hold when it is one. */
    val canSave: Boolean
        get() = name.isNotBlank() &&
            !rules.isEmpty &&
            rules.allRules.all(ListRule::isComplete) &&
            (onServer == null || rules.fitsMagicShelf)

    val spec: SmartListSpec
        get() = SmartListSpec(rules = savedRules, limit = limit.takeIf { onServer == null })
}

/** The editor's starting point for a smart list or magic shelf; null for one whose rules the app can't read. */
fun UserList.toSmartDraft(): SmartListDraft? {
    val smart = smart ?: return null
    return SmartListDraft(
        listId = id,
        name = name,
        onServer = server?.sourceType,
        rules = smart.rules,
        order = order,
        limit = smart.limit,
    )
}

/** Where a rule sheet is: adding to the list's rules or to group [group], or changing rule [index] there. */
internal data class RuleSlot(val group: Int?, val index: Int?)

/**
 * The smart list editor: the name, where it is kept, its rules (joined by all or any, with groups
 * one level deep), Sort by and How many, and the books they match now, worked out from what this
 * device knows ([items] with their [progressByItemId] and the books [onDevice]). [servers] are where
 * else a new one can be kept (Grimmory, as a magic shelf). Nothing is saved until the bar's Create
 * or Done.
 */
@Composable
fun SmartListEditor(
    draft: SmartListDraft,
    onDraftChange: (SmartListDraft) -> Unit,
    items: Collection<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    onDevice: Set<String>,
    servers: List<SourceType>,
    canDelete: Boolean,
    onDelete: () -> Unit,
    cover: ListCover,
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
    // Grimmory's shelves by name, for the Grimmory shelf rule; empty hides it.
    shelfNames: List<String> = emptyList(),
    // Where you sorted each tag, so the Tag rule groups them as Browse does.
    tagKindOf: (String) -> LibraryTagKind = { LibraryTagKind.THEME },
) {
    val pool = remember(items, draft.onServer) { smartListPool(items, draft.onServer) }
    val context = remember(progressByItemId, onDevice) { listRuleContext(progressByItemId, onDevice, System.currentTimeMillis()) }
    val matches = remember(draft.rules, draft.order, draft.limit, pool, context) {
        smartListEntries(draft.savedRules, pool, context, draft.order, draft.limit.takeIf { draft.onServer == null })
    }
    val itemsById = remember(pool) { pool.associateBy(ReadableItem::id) }
    var slot by remember { mutableStateOf<RuleSlot?>(null) }
    // A rule sheet takes the focus off the name, so the keyboard doesn't come back when it closes.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(slot != null) { if (slot != null) focusManager.clearFocus() }
    var pickedField by remember { mutableStateOf<ListRuleField?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val noun = if (draft.onServer != null) "magic shelf" else "smart list"
    val gutter = appGutter()
    val expanded = currentAppWindowWidth() == AppWindowWidth.Expanded

    fun setRules(change: (ListRules) -> ListRules) = onDraftChange(draft.copy(rules = change(draft.rules)))

    val form: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap)) {
            AppTextField(
                value = draft.name,
                onValueChange = { name -> onDraftChange(draft.copy(name = name)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KeepItSection(draft = draft, servers = servers, onDraftChange = onDraftChange)
            Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                AppGroupLabel(text = "Rules")
                AppPanel(modifier = Modifier.fillMaxWidth()) {
                    MatchRow(title = "Books that match", matchAll = draft.rules.matchAll, onChange = { all -> setRules { it.copy(matchAll = all) } })
                    draft.rules.rules.forEachIndexed { index, rule ->
                        AppSettingsRowDivider()
                        RuleRow(
                            rule = rule,
                            onServer = draft.onServer,
                            onClick = { slot = RuleSlot(group = null, index = index) },
                            onRemove = { setRules { it.copy(rules = it.rules.filterIndexed { i, _ -> i != index }) } },
                        )
                    }
                    draft.rules.groups.forEachIndexed { groupIndex, group ->
                        AppSettingsRowDivider()
                        RuleGroupBlock(
                            group = group,
                            onServer = draft.onServer,
                            onChange = { changed -> setRules { it.copy(groups = it.groups.mapIndexed { i, g -> if (i == groupIndex) changed else g }) } },
                            onRemove = { setRules { it.copy(groups = it.groups.filterIndexed { i, _ -> i != groupIndex }) } },
                            onEditRule = { index -> slot = RuleSlot(group = groupIndex, index = index) },
                            onAddRule = { slot = RuleSlot(group = groupIndex, index = null) },
                        )
                    }
                    AppSettingsRowDivider()
                    Row(
                        modifier = Modifier.padding(horizontal = AppLayout.inset / 2),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppTextButton(onClick = { slot = RuleSlot(group = null, index = null) }) {
                            Icon(imageVector = AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Add rule", fontWeight = FontWeight.SemiBold)
                        }
                        AppTextButton(onClick = {
                            setRules { it.copy(groups = it.groups + ListRuleGroup(matchAll = false)) }
                            slot = RuleSlot(group = draft.rules.groups.size, index = null)
                        }) {
                            Icon(imageVector = AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Add a group", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (draft.onServer != null && !draft.rules.fitsMagicShelf) {
                    Text(
                        text = "Downloaded and Source work only for lists on this device: take them out to keep this on ${draft.onServer.displayName()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            AppPanel(modifier = Modifier.fillMaxWidth()) {
                ChoiceRow(
                    title = "Sort by",
                    options = listOf(UserListOrder.DateAdded, UserListOrder.Title, UserListOrder.Published),
                    selected = draft.order,
                    label = UserListOrder::sortLabel,
                    onSelected = { order -> onDraftChange(draft.copy(order = order)) },
                )
                // A magic shelf's books are Grimmory's to pick, every match.
                if (draft.onServer == null) {
                    AppSettingsRowDivider()
                    ChoiceRow(
                        title = "How many",
                        options = SmartListLimits,
                        selected = draft.limit,
                        label = { limit -> limit?.toString() ?: "Every match" },
                        onSelected = { limit -> onDraftChange(draft.copy(limit = limit)) },
                    )
                }
            }
            if (canDelete) {
                AppTextButton(onClick = { confirmDelete = true }) {
                    Text(text = "Delete $noun", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    val preview: @Composable () -> Unit = {
        SmartListMatches(matches = matches, itemsById = itemsById, hasRules = !draft.rules.isEmpty, cover = cover)
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = gutter, end = gutter, top = contentTopInset + AppLayout.contentTop, bottom = AppLayout.contentBottom),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
    ) {
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.sectionGap)) {
                Box(modifier = Modifier.weight(1f)) { form() }
                Box(modifier = Modifier.width(SmartListEditorDefaults.previewWidth)) { preview() }
            }
        } else {
            form()
            preview()
        }
    }

    slot?.let { current ->
        val existing = current.index?.let { index ->
            if (current.group == null) draft.rules.rules.getOrNull(index) else draft.rules.groups.getOrNull(current.group)?.rules?.getOrNull(index)
        }
        val field = existing?.field ?: pickedField
        val close = {
            slot = null
            pickedField = null
            // A group opened with Add a group and left without a rule goes again.
            val group = current.group
            if (group != null && current.index == null && draft.rules.groups.getOrNull(group)?.rules?.isEmpty() == true) {
                setRules { it.copy(groups = it.groups.filterIndexed { i, _ -> i != group }) }
            }
        }
        if (field == null) {
            ListRulePickerSheet(
                onServer = draft.onServer,
                showShelf = shelfNames.isNotEmpty(),
                onPick = { picked -> pickedField = picked },
                onDismiss = close,
            )
        } else {
            ListRuleValueSheet(
                field = field,
                initial = existing,
                pool = pool,
                context = context,
                shelfNames = shelfNames,
                tagKindOf = tagKindOf,
                onBack = if (existing == null) ({ pickedField = null }) else null,
                onSave = { rule ->
                    setRules { rules -> rules.withRule(current, rule) }
                    slot = null
                    pickedField = null
                },
                onDismiss = close,
            )
        }
    }
    if (confirmDelete) {
        AppDialogCard(
            onDismissRequest = { confirmDelete = false },
            title = "Delete “${draft.name}”?",
            subtitle = draft.onServer?.let { server -> "The magic shelf goes from ${server.displayName()} too; its books stay in your library." }
                ?: "The list goes; its books stay in your library.",
            actions = {
                AppDialogDismissButton(text = "Cancel", onClick = { confirmDelete = false })
                AppDialogConfirmButton(text = "Delete", onClick = {
                    confirmDelete = false
                    onDelete()
                })
            },
        )
    }
}

object SmartListEditorDefaults {
    /** The matches beside the form on Expanded. */
    val previewWidth: Dp = 320.dp
}

/** "Published, oldest first", "Date added, newest first", "Title". */
private fun UserListOrder.sortLabel(): String = when (this) {
    UserListOrder.Published -> "Published, oldest first"
    UserListOrder.DateAdded -> "Date added, newest first"
    else -> label
}

/** [this] with [rule] put where [slot] says: a new rule at the end of its group or the list's, or in place of the one there. */
internal fun ListRules.withRule(slot: RuleSlot, rule: ListRule): ListRules {
    fun List<ListRule>.put(index: Int?) = if (index == null || index !in indices) this + rule else mapIndexed { i, r -> if (i == index) rule else r }
    return if (slot.group == null) {
        copy(rules = rules.put(slot.index))
    } else {
        copy(groups = groups.mapIndexed { i, group -> if (i == slot.group) group.copy(rules = group.rules.put(slot.index)) else group })
    }
}

@Composable
private fun KeepItSection(
    draft: SmartListDraft,
    servers: List<SourceType>,
    onDraftChange: (SmartListDraft) -> Unit,
) {
    val server = draft.onServer
    // Where a list lives is set when it is made.
    if (!draft.isNew || servers.isEmpty()) {
        if (server != null) {
            Text(
                text = "A ${server.displayName()} magic shelf: ${server.displayName()} picks its books by these rules, and its web app shows it too.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
        AppGroupLabel(text = "Keep it")
        AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
            AppSegment(selected = server == null, onClick = { onDraftChange(draft.copy(onServer = null)) }, label = { Text("On this device") })
            servers.forEach { option ->
                AppSegment(
                    selected = server == option,
                    onClick = { onDraftChange(draft.copy(onServer = option)) },
                    label = { Text("On ${option.displayName()}") },
                )
            }
        }
        Text(
            text = server?.let { "A ${it.displayName()} magic shelf, also in ${it.displayName()}'s web app. ${it.displayName()} books only." }
                ?: "Matches books from every source, by what this device knows about them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Books that match [all ▾] of these": the join of a list's or a group's rules. */
@Composable
private fun MatchRow(title: String, matchAll: Boolean, onChange: (Boolean) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    AppSettingsRow(
        title = title,
        showChevron = false,
        trailing = {
            Box {
                AppChip(
                    selected = false,
                    onClick = { open = true },
                    label = { Text(if (matchAll) "all of these" else "any of these") },
                    trailingIcon = { Icon(imageVector = AppIcons.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                AppMenu(expanded = open, onDismissRequest = { open = false }) {
                    AppMenuChoiceItem(text = "All of these", selected = matchAll, onClick = { open = false; onChange(true) })
                    AppMenuChoiceItem(text = "Any of these", selected = !matchAll, onClick = { open = false; onChange(false) })
                }
            }
            trailing?.invoke()
        },
    )
}

@Composable
private fun RuleRow(rule: ListRule, onServer: SourceType?, onClick: () -> Unit, onRemove: () -> Unit) {
    val unfit = onServer != null && rule.field.onDeviceOnly
    AppSettingsRow(
        title = rule.field.label,
        supporting = if (unfit) "${rule.summary()} · lists on this device only" else rule.summary(),
        supportingColor = if (unfit) MaterialTheme.colorScheme.error else Color.Unspecified,
        onClick = onClick,
        showChevron = false,
        trailing = {
            AppIconButton(onClick = onRemove) {
                Icon(imageVector = AppIcons.Close, contentDescription = "Remove the ${rule.field.label} rule")
            }
        },
    )
}

/** A group: its own all/any, its rules, Add rule and a way to take the whole group out. */
@Composable
private fun RuleGroupBlock(
    group: ListRuleGroup,
    onServer: SourceType?,
    onChange: (ListRuleGroup) -> Unit,
    onRemove: () -> Unit,
    onEditRule: (Int) -> Unit,
    onAddRule: () -> Unit,
) {
    Box(modifier = Modifier.padding(horizontal = AppLayout.inset / 2, vertical = AppLayout.headingGap)) {
        AppPanel(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            MatchRow(
                title = "And one group that matches",
                matchAll = group.matchAll,
                onChange = { all -> onChange(group.copy(matchAll = all)) },
                trailing = {
                    AppIconButton(onClick = onRemove) {
                        Icon(imageVector = AppIcons.Delete, contentDescription = "Remove the group")
                    }
                },
            )
            group.rules.forEachIndexed { index, rule ->
                AppSettingsRowDivider()
                RuleRow(
                    rule = rule,
                    onServer = onServer,
                    onClick = { onEditRule(index) },
                    onRemove = { onChange(group.copy(rules = group.rules.filterIndexed { i, _ -> i != index })) },
                )
            }
            AppSettingsRowDivider()
            AppTextButton(onClick = onAddRule, modifier = Modifier.padding(horizontal = AppLayout.inset / 2)) {
                Icon(imageVector = AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Add rule to the group", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppSettingsRow(title = title, value = label(selected), onClick = { open = true })
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                AppMenuChoiceItem(text = label(option), selected = option == selected, onClick = {
                    open = false
                    onSelected(option)
                })
            }
        }
    }
}

/** "Matches 44", the first nine covers and "and 35 more". */
@Composable
private fun SmartListMatches(
    matches: List<UserListEntry>,
    itemsById: Map<String, ReadableItem>,
    hasRules: Boolean,
    cover: ListCover,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
        AppSectionHeading(text = "Matches", count = matches.size.toString())
        if (!hasRules) {
            Text(
                text = "Add a rule to see which books it picks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        if (matches.isEmpty()) {
            Text(
                text = "Nothing in your library matches these rules now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val shown = matches.take(SmartListPreviewCount)
        shown.chunked(3).forEach { rowEntries ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowEntries.forEach { entry ->
                    Box(modifier = Modifier.weight(1f)) {
                        cover(itemsById[entry.itemId] ?: entry.asReadableItem(), Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                    }
                }
                repeat(3 - rowEntries.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
        if (matches.size > shown.size) {
            Text(
                text = "and ${matches.size - shown.size} more",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val SmartListPreviewCount = 9
