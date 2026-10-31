package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.ui.components.AppBookRow
import com.example.unifiedreader.ui.components.AppBookRowDefaults
import com.example.unifiedreader.ui.components.AppBookRowLine
import com.example.unifiedreader.ui.components.AppBookRowNumber
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuItem
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppSectionHeading
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.appReorderHandle
import com.example.unifiedreader.ui.components.appReorderableItem
import com.example.unifiedreader.ui.components.movedItem
import com.example.unifiedreader.ui.components.rememberAppReorderState

/**
 * The list page in edit mode: name, description, the order, and the books to drag, move with a
 * row's menu (so it works without dragging and with TalkBack) or remove. Nothing is saved until
 * the bar's Done; moving a book switches the list to Your order.
 *
 * A server's shelf ([serverName] is its server) has no description and no order of its own: its
 * editor renames it, sets the order kept here and removes books, all saved on the server with Done.
 */
@Composable
fun UserListEditor(
    draft: UserListDraft,
    onDraftChange: (UserListDraft) -> Unit,
    entriesById: Map<String, UserListEntry>,
    itemsById: Map<String, ReadableItem>,
    canDelete: Boolean,
    cover: ListCover,
    onDelete: () -> Unit,
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
    orders: List<UserListOrder> = UserListOrder.entries,
    serverName: String? = null,
) {
    val isShelf = serverName != null
    // The rows render from the draft held in the route's state; a drag that reports several moves
    // in one frame reads the latest draft through [currentDraft].
    var currentDraft by remember(draft.listId) { mutableStateOf(draft) }
    currentDraft = draft
    fun update(change: (UserListDraft) -> UserListDraft) {
        val next = change(currentDraft)
        currentDraft = next
        onDraftChange(next)
    }
    fun move(from: Int, to: Int) = update { it.copy(itemIds = it.itemIds.movedItem(from, to), order = UserListOrder.Custom) }
    val reorderState = rememberAppReorderState(onMove = ::move)
    var confirmDelete by remember { mutableStateOf(false) }
    val gutter = appGutter()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = gutter, end = gutter, top = contentTopInset + AppLayout.contentTop, bottom = AppLayout.contentBottom),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            AppTextField(
                value = draft.name,
                onValueChange = { name -> update { it.copy(name = name) } },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isShelf) {
                AppTextField(
                    value = draft.description,
                    onValueChange = { description -> update { it.copy(description = description) } },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
                orders.forEach { option ->
                    AppSegment(
                        selected = option == draft.order,
                        onClick = { update { it.copy(order = option) } },
                        label = { Text(option.label) },
                    )
                }
            }
            Text(
                text = when {
                    isShelf -> "$serverName keeps no order for a shelf, so this one is kept on this tablet. Read next follows each book's series."
                    draft.order == UserListOrder.Custom -> "Numbered, and Read next follows the list when you open a book from it."
                    else -> "Read next follows each book's series."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
            AppSectionHeading(text = "Books", count = draft.itemIds.size.toString())
            if (draft.itemIds.isNotEmpty()) {
                AppPanel(modifier = Modifier.fillMaxWidth()) {
                    draft.itemIds.forEachIndexed { index, itemId ->
                        key(itemId) {
                            val entry = entriesById[itemId]
                            val item = itemsById[itemId] ?: entry?.asReadableItem()
                            Column(modifier = Modifier.appReorderableItem(reorderState, index)) {
                                if (index > 0) AppSettingsRowDivider()
                                EditorBookRow(
                                    number = (index + 1).takeUnless { isShelf },
                                    title = itemsById[itemId]?.libraryBookTitle() ?: entry?.title ?: itemId,
                                    line = entry?.let { it.secondaryText ?: it.primaryText?.takeIf { text -> text != it.title } }.orEmpty(),
                                    item = item,
                                    isFirst = index == 0,
                                    isLast = index == draft.itemIds.lastIndex,
                                    canMove = !isShelf,
                                    cover = cover,
                                    handleModifier = Modifier.appReorderHandle(reorderState, index),
                                    onMoveUp = { move(index, index - 1) },
                                    onMoveDown = { move(index, index + 1) },
                                    onMoveToTop = { move(index, 0) },
                                    onRemove = {
                                        update {
                                            it.copy(
                                                itemIds = it.itemIds - itemId,
                                                removedItemIds = it.removedItemIds + itemId,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (canDelete) {
            AppTextButton(onClick = { confirmDelete = true }) {
                Text(text = if (isShelf) "Delete shelf" else "Delete list", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (confirmDelete) {
        AppDialogCard(
            onDismissRequest = { confirmDelete = false },
            title = "Delete “${draft.name}”?",
            subtitle = if (isShelf) {
                "The shelf goes from $serverName too; its books stay in your library."
            } else {
                "The list goes; its books stay in your library."
            },
            actions = {
                AppDialogDismissButton(text = "Cancel", onClick = { confirmDelete = false })
                AppDialogConfirmButton(
                    text = "Delete",
                    destructive = true,
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                )
            },
        )
    }
}

@Composable
private fun EditorBookRow(
    number: Int?,
    title: String,
    line: String,
    item: ReadableItem?,
    isFirst: Boolean,
    isLast: Boolean,
    canMove: Boolean,
    cover: ListCover,
    handleModifier: Modifier,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppBookRow(
        title = title,
        cover = {
            Box(modifier = Modifier.width(AppBookRowDefaults.coverWidth)) {
                if (item != null) cover(item, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            }
        },
        modifier = Modifier.padding(end = AppLayout.menuInset),
        leading = {
            AppIconButton(onClick = onRemove) {
                Icon(imageVector = AppIcons.Remove, contentDescription = "Remove $title", tint = MaterialTheme.colorScheme.error)
            }
            number?.let { AppBookRowNumber(it) }
        },
        supporting = { if (line.isNotEmpty()) AppBookRowLine(text = line) },
        trailing = {
            if (!canMove) return@AppBookRow
            Box {
                AppIconButton(onClick = { menuOpen = true }) {
                    Icon(imageVector = AppIcons.MoreVert, contentDescription = "Move $title")
                }
                AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!isFirst) {
                        AppMenuItem(text = { Text("Move up") }, onClick = { menuOpen = false; onMoveUp() })
                        AppMenuItem(text = { Text("Move to the top") }, onClick = { menuOpen = false; onMoveToTop() })
                    }
                    if (!isLast) {
                        AppMenuItem(text = { Text("Move down") }, onClick = { menuOpen = false; onMoveDown() })
                    }
                }
            }
            Box(modifier = handleModifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(imageVector = AppIcons.DragHandle, contentDescription = "Drag to move $title")
            }
        },
    )
}
