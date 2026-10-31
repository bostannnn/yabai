package com.example.unifiedreader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarSearchField
import com.example.unifiedreader.feature.library.VisibleResolvedLibraryCover
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.feature.lists.AddToListSheet
import com.example.unifiedreader.feature.lists.ListCover
import com.example.unifiedreader.feature.lists.ListsRouteState
import com.example.unifiedreader.feature.lists.ListsScreen
import com.example.unifiedreader.feature.lists.NewListDialog
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.UserListBook
import com.example.unifiedreader.feature.lists.UserListEditor
import com.example.unifiedreader.feature.lists.UserListKind
import com.example.unifiedreader.feature.lists.UserListPage
import com.example.unifiedreader.feature.lists.books
import com.example.unifiedreader.feature.lists.orderedForScreen
import com.example.unifiedreader.feature.lists.readingOrderUnderWay
import com.example.unifiedreader.feature.lists.sortedEntries
import com.example.unifiedreader.feature.lists.stalledList
import com.example.unifiedreader.feature.lists.toDraft
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons

/**
 * The Lists destination: every list, one list, or one list being edited, as [state] says. A book
 * opened from a list in Your order carries the list along, so Read next follows it.
 */
@Composable
internal fun ListsRoute(
    state: ListsRouteState,
    uiState: AppUiState,
    viewModel: AppViewModel,
    listsViewModel: AppListsViewModel,
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
) {
    val stored by listsViewModel.lists.collectAsStateWithLifecycle()
    val itemsById = remember(uiState.items) { uiState.items.associateBy(ReadableItem::id) }
    val progress = uiState.progressByItemId
    val stalled = remember(uiState.items, progress) {
        stalledList(uiState.items, progress, nowEpochMillis = System.currentTimeMillis())
    }
    val lists = remember(stored, stalled) { orderedForScreen(stored, stalled) }
    val cover = rememberListCover(viewModel)
    val openList = state.openListId?.let { id -> lists.firstOrNull { list -> list.id == id } }
    // A list deleted (or never loaded) closes; wait for the first load before deciding.
    LaunchedEffect(state.openListId, stored) {
        if (state.openListId != null && stored.isNotEmpty() && openList == null) state.back()
    }
    BackHandler(enabled = state.openListId != null) { state.back() }

    fun openBook(list: UserList, book: UserListBook) {
        val item = book.item ?: return
        if (list.kind != UserListKind.Stalled) listsViewModel.markOpened(list.id)
        viewModel.onListItemSelected(
            item = item,
            listContext = if (list.isReadingOrder && list.isEditable) {
                AppReaderListContext(
                    listId = list.id,
                    title = list.name,
                    itemIds = list.sortedEntries().map { entry -> entry.itemId },
                )
            } else {
                null
            },
        )
    }

    val draft = state.editDraft
    when {
        openList != null && draft != null && draft.listId == openList.id -> UserListEditor(
            draft = draft,
            onDraftChange = { state.editDraft = it },
            entriesById = remember(openList) { openList.entries.associateBy { entry -> entry.itemId } },
            itemsById = itemsById,
            canDelete = openList.kind == UserListKind.User,
            cover = cover,
            onDelete = {
                listsViewModel.deleteList(openList.id)
                state.editDraft = null
                state.back()
            },
            contentTopInset = contentTopInset,
            modifier = modifier,
        )
        openList != null -> {
            val books = remember(openList, itemsById, progress) { openList.books(itemsById, progress) }
            UserListPage(
                list = openList,
                books = books,
                cover = cover,
                onOpenBook = { book -> openBook(openList, book) },
                onSetOrder = { order -> listsViewModel.setOrder(openList.id, order) },
                onSetHideFinished = { hide -> listsViewModel.setHideFinished(openList.id, hide) },
                onEdit = { state.editDraft = openList.toDraft() },
                contentTopInset = contentTopInset,
                onScrolledChanged = { scrolled -> state.scrolled = scrolled },
                modifier = modifier,
            )
        }
        else -> {
            val underWay = remember(stored, itemsById, progress) { readingOrderUnderWay(stored, itemsById, progress) }
            ListsScreen(
                lists = lists,
                underWay = underWay,
                kavitaLists = uiState.kavitaReadingLists.summaries.takeIf { uiState.kavitaConnection.hasStoredSession }.orEmpty(),
                itemsById = itemsById,
                progressByItemId = progress,
                searchQuery = state.searchQuery,
                cellSizeDp = uiState.libraryGridCellSizeDp,
                cover = cover,
                onOpenList = { list -> state.openList(list.id) },
                onContinue = { under -> openBook(under.list, under.next) },
                onOpenKavitaList = { summary ->
                    viewModel.openLibrarySource(SourceType.KAVITA)
                    viewModel.onKavitaReadingListSelected(summary.id)
                },
                contentTopInset = contentTopInset,
                onScrolledChanged = { scrolled -> state.scrolled = scrolled },
                modifier = modifier,
            )
        }
    }
    if (state.isNewListOpen) {
        NewListDialog(
            onCreate = { name ->
                state.isNewListOpen = false
                listsViewModel.createList(name, items = emptyList()) { id -> state.openList(id) }
            },
            onDismiss = { state.isNewListOpen = false },
        )
    }
}

/**
 * The Lists bar: the menu, "Search lists" and New list on the lists; Back on a list; Cancel,
 * "Edit list" and Done while one is edited. No glass until something scrolls under it.
 */
@Composable
internal fun ListsTopBar(
    state: ListsRouteState,
    listsViewModel: AppListsViewModel,
    onDrawerClick: (() -> Unit)?,
) {
    ThemedTopBarContainer(surfaceVisible = state.scrolled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val draft = state.editDraft
            when {
                draft != null -> {
                    AppIconButton(onClick = { state.editDraft = null }) {
                        Icon(imageVector = AppIcons.Close, contentDescription = "Cancel editing")
                    }
                    Text(
                        text = "Edit list",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        onClick = {
                            listsViewModel.applyDraft(draft)
                            state.editDraft = null
                        },
                        enabled = draft.name.isNotBlank(),
                    ) {
                        Text(text = "Done", fontWeight = FontWeight.SemiBold)
                    }
                }
                state.openListId != null -> {
                    AppIconButton(onClick = { state.back() }) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "Back to lists")
                    }
                }
                else -> {
                    if (onDrawerClick != null) DrawerMenuButton(onClick = onDrawerClick)
                    LibraryBrowseTopBarSearchField(
                        query = state.searchQuery,
                        onQueryChanged = { query -> state.searchQuery = query },
                        placeholder = "Search lists",
                        queryContentDescription = "Search lists",
                        modifier = Modifier.weight(1f),
                    )
                    AppIconButton(onClick = { state.isNewListOpen = true }) {
                        Icon(imageVector = AppIcons.Add, contentDescription = "New list")
                    }
                }
            }
        }
    }
}

/**
 * Add to list for [items] (a book's page, or a selection in the library), with New list, which
 * puts them into the list it makes.
 */
@Composable
internal fun AddToListHost(
    items: List<ReadableItem>,
    listsViewModel: AppListsViewModel,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val lists by listsViewModel.lists.collectAsStateWithLifecycle()
    var isNewListOpen by rememberSaveable { mutableStateOf(false) }
    AddToListSheet(
        items = items,
        lists = lists,
        onSetInList = { list, inList -> listsViewModel.setInList(list, items, inList) },
        onNewList = { isNewListOpen = true },
        onDismiss = onDismiss,
        onBack = onBack,
    )
    if (isNewListOpen) {
        NewListDialog(
            subtitle = items.singleOrNull()?.let { item -> "${item.libraryBookTitle()} goes into it, in your order." }
                ?: "${items.size} books go into it, in your order.",
            onCreate = { name ->
                isNewListOpen = false
                listsViewModel.createList(name, items)
            },
            onDismiss = { isNewListOpen = false },
        )
    }
}

/** The library's cover, with its cache, for list tiles and rows. */
@Composable
private fun rememberListCover(viewModel: AppViewModel): ListCover = remember(viewModel) {
    { item, modifier ->
        VisibleResolvedLibraryCover(
            item = item,
            coverImageUrlProvider = viewModel::coverImageUrlFor,
            onCoverVisible = viewModel::ensureCoverCached,
            modifier = modifier,
        )
    }
}
