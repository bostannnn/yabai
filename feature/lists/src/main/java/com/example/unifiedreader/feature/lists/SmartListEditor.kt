package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    // The library Filters picks it was saved from; changing its rules here leaves them behind.
    val filter: SmartListFilter? = null,
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
        get() = SmartListSpec(rules = savedRules, limit = limit.takeIf { onServer == null }, filter = filter)
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
        filter = smart.filter,
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
    // A pass over the library per rule: worked out off the main thread, shown when ready.
    val ruleCounts by produceState(emptyMap<ListRule, Int>(), draft.rules, pool, context) {
        value = withContext(Dispatchers.Default) { smartListRuleCounts(draft.rules, pool, context) }
    }
    val emptiedBy by produceState<Pair<ListRule, Int>?>(null, draft.savedRules, pool, context) {
        value = withContext(Dispatchers.Default) { smartListEmptiedBy(draft.savedRules, pool, context) }
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
    val hasRules = !draft.rules.isEmpty

    // Rules changed here no longer match the Filters picks the list was saved from.
    fun setRules(change: (ListRules) -> ListRules) = onDraftChange(draft.copy(rules = change(draft.rules), filter = null))

    val matchesSection: @Composable (asGrid: Boolean) -> Unit = { asGrid ->
        SmartListMatches(matches = matches, itemsById = itemsById, hasRules = hasRules, emptiedBy = emptiedBy, asGrid = asGrid, cover = cover)
    }
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
                // Beside the matches the count is there; under the form it is far below, so the label carries it.
                AppGroupLabel(text = "Rules", count = if (!expanded && hasRules) "${appCount(matches.size)} match" else null)
                SmartListRulesPanel(
                    rules = draft.rules,
                    counts = ruleCounts,
                    onServer = draft.onServer,
                    onRulesChange = { rules -> setRules { rules } },
                    onEditRule = { picked -> slot = picked },
                    onAddGroup = {
                        setRules { it.copy(groups = it.groups + ListRuleGroup(matchAll = false)) }
                        slot = RuleSlot(group = draft.rules.groups.size, index = null)
                    },
                )
                if (draft.onServer != null && !draft.rules.fitsMagicShelf) {
                    Text(
                        text = "Downloaded and Source work only for lists on this device: take them out to keep this on ${draft.onServer.displayName()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (!expanded) matchesSection(false)
            Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                AppGroupLabel(text = "Order")
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
            }
            if (canDelete) {
                AppTextButton(onClick = { confirmDelete = true }) {
                    Text(text = "Delete $noun", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = gutter, end = gutter, top = contentTopInset + AppLayout.contentTop, bottom = AppLayout.contentBottom),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
    ) {
        if (expanded) {
            // The form and the books it picks, side by side and as wide as each other.
            Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.sectionGap)) {
                Box(modifier = Modifier.weight(1f)) { form() }
                Box(modifier = Modifier.weight(1f)) { matchesSection(true) }
            }
        } else {
            form()
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
    /** The All of these / Any of these switch in "Books that match". */
    val joinWidth: Dp = 232.dp

    /** A group's All / Any switch. */
    val groupJoinWidth: Dp = 120.dp

    /** The narrowest a cover gets in the matches grid beside the form. */
    val matchCoverMinWidth: Dp = 104.dp

    /** A cover in the matches row under the rules (below Expanded). */
    val matchStripCoverWidth: Dp = 88.dp
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
        SmartListKeepItPanel(onServer = server, servers = servers, onChange = { picked -> onDraftChange(draft.copy(onServer = picked)) })
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
