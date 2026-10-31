package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.ui.components.AppCheckbox
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSheetDivider
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet

/**
 * Adds [items] to the user's lists: Want to read, then their own lists, then a server's [shelves]
 * (given only when every one of [items] is that server's), each with a checkbox (on when every one
 * of [items] is in it), and New list. A tap saves at once: on adds what is missing, off takes them
 * out. Opened from a book's page ([onBack] returns to it) or from a selection.
 */
@Composable
fun AddToListSheet(
    items: List<ReadableItem>,
    lists: List<UserList>,
    onSetInList: (list: UserList, inList: Boolean) -> Unit,
    onNewList: () -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    shelves: List<UserList> = emptyList(),
    // Said under Want to read when ticking it also changes a server's Want to read.
    wantToReadNote: String? = null,
) {
    val ownShelves = shelves.filter(UserList::isEditable)
    val shelfSource = ownShelves.firstOrNull()?.server?.sourceType?.displayName()
    val editable = lists.filter(UserList::isEditable)
    val wantToRead = editable.filter { it.kind == UserListKind.WantToRead }
    val own = editable.filter { it.kind == UserListKind.User }
    AppSideSheet(onDismissRequest = onDismiss) {
        AppPanelHeader(
            title = "Add to list",
            subtitle = items.singleOrNull()?.libraryBookTitle() ?: bookCountLabel(items.size),
            onBack = onBack?.let { back -> { back() } },
            onClose = { dismiss() },
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            wantToRead.forEach { list -> ListCheckRow(list, items, onSetInList, note = wantToReadNote) }
            AppSheetDivider()
            AppSheetLabel(text = "Your lists")
            own.forEach { list -> ListCheckRow(list, items, onSetInList) }
            if (shelfSource != null) {
                AppSheetDivider()
                AppSheetLabel(text = "$shelfSource shelves")
                ownShelves.forEach { shelf -> ListCheckRow(shelf, items, onSetInList) }
            }
            AppSettingsRow(
                title = "New list",
                icon = AppIcons.Add,
                onClick = onNewList,
                showChevron = false,
            )
            Text(
                text = if (shelfSource != null) {
                    "Your lists stay on this tablet and can mix books from every source. $shelfSource shelves hold only $shelfSource books and are kept on the server."
                } else {
                    "Your lists are kept on this tablet and can mix books from every source."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(AppLayout.inset),
            )
        }
    }
}

@Composable
private fun ListCheckRow(
    list: UserList,
    items: List<ReadableItem>,
    onSetInList: (UserList, Boolean) -> Unit,
    note: String? = null,
) {
    val inList = items.isNotEmpty() && items.all { item -> list.contains(item.id) }
    AppSettingsRow(
        title = list.name,
        supporting = note ?: list.checkRowLine(items),
        onClick = { onSetInList(list, !inList) },
        showChevron = false,
        trailing = {
            AppCheckbox(checked = inList, onCheckedChange = { checked -> onSetInList(list, checked) })
        },
    )
}

/** "Number 7 of 14" for the one book in a list in Your order, else the list's size. */
private fun UserList.checkRowLine(items: List<ReadableItem>): String {
    val single = items.singleOrNull()
    val ordered = entries.sortedBy(UserListEntry::position)
    val index = single?.let { item -> ordered.indexOfFirst { it.itemId == item.id } } ?: -1
    return if (index >= 0 && isReadingOrder) "Number ${index + 1} of ${ordered.size}" else bookCountLabel(entries.size)
}
