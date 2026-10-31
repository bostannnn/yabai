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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarSearchField
import com.example.unifiedreader.feature.library.VisibleResolvedLibraryCover
import com.example.unifiedreader.feature.lists.ListCover
import com.example.unifiedreader.feature.lists.ListsRouteState
import com.example.unifiedreader.feature.lists.ListsScreen
import com.example.unifiedreader.feature.lists.NewListDialog
import com.example.unifiedreader.feature.lists.NewListServer
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.UserListBook
import com.example.unifiedreader.feature.lists.UserListEditor
import com.example.unifiedreader.feature.lists.UserListKind
import com.example.unifiedreader.feature.lists.UserListPage
import com.example.unifiedreader.feature.lists.books
import com.example.unifiedreader.feature.lists.keepDownloadedLine
import com.example.unifiedreader.feature.lists.nextToKeepDownloaded
import com.example.unifiedreader.feature.lists.orderedForScreen
import com.example.unifiedreader.feature.lists.readingOrderUnderWay
import com.example.unifiedreader.feature.lists.sortedEntries
import com.example.unifiedreader.feature.lists.stalledList
import com.example.unifiedreader.feature.lists.toDraft
import com.example.unifiedreader.feature.lists.toUserList
import com.example.unifiedreader.feature.lists.withSmartBooks
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import kotlinx.coroutines.delay

/**
 * The Lists destination: every list, one list, or one list being edited, as [state] says. A book
 * opened from a list in Your order carries the list along, so Read next follows it. Grimmory's
 * shelves show while Grimmory is connected, and are read again from the server when Lists opens
 * (the store keeps that to once every few minutes).
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
    SideEffect { listsViewModel.catalog = itemsById }
    val progress = uiState.progressByItemId
    val stalled = remember(uiState.items, progress) {
        stalledList(uiState.items, progress, nowEpochMillis = System.currentTimeMillis())
    }
    val onDevice = uiState.locallyAvailableRemoteItemIds
    val resolved = remember(stored, uiState.items, progress, onDevice) { stored.withSmartBooks(uiState.items, progress, onDevice) }
    val lists = remember(resolved, stalled) { orderedForScreen(resolved, stalled) }
    val storedShelves by listsViewModel.shelves.collectAsStateWithLifecycle()
    val connected = connectedListServers(uiState)
    val shelves = remember(storedShelves, itemsById, connected) {
        storedShelves.orEmpty().filter { shelf -> shelf.sourceType in connected }.map { shelf -> shelf.toUserList(itemsById) }
    }
    LaunchedEffect(connected) { if (connected.isNotEmpty()) listsViewModel.refreshShelves() }
    val onShelfFailure = rememberShelfFailureToast()
    val cover = rememberListCover(viewModel)
    val openList = state.openListId?.let { id ->
        lists.firstOrNull { list -> list.id == id } ?: shelves.firstOrNull { shelf -> shelf.id == id }
    }
    // A list deleted (or never loaded) closes. Wait for the first load, and a moment more, since a
    // list just made is opened before the database hands it back.
    val loaded = stored.isNotEmpty() && storedShelves != null
    LaunchedEffect(state.openListId, openList == null, loaded) {
        if (state.openListId != null && openList == null && loaded) {
            delay(500)
            state.back()
        }
    }
    BackHandler(enabled = state.openListId != null) { state.back() }
    // A server's list reads its books again as its page opens.
    LaunchedEffect(openList?.id) { openList?.takeIf { it.server != null }?.let(listsViewModel::refreshList) }

    fun openBook(list: UserList, book: UserListBook) {
        openBookFromList(viewModel, listsViewModel, list, book.item ?: return)
    }

    val draft = state.editDraft
    when {
        openList != null && draft != null && draft.listId == openList.id -> UserListEditor(
            draft = draft,
            onDraftChange = { state.editDraft = it },
            entriesById = remember(openList) { openList.entries.associateBy { entry -> entry.itemId } },
            itemsById = itemsById,
            canDelete = openList.kind == UserListKind.User || openList.kind == UserListKind.Smart || openList.server != null,
            cover = cover,
            onDelete = {
                listsViewModel.deleteList(openList, onFailure = onShelfFailure)
                state.editDraft = null
                state.back()
            },
            contentTopInset = contentTopInset,
            modifier = modifier,
            orders = openList.orders,
            serverName = openList.server?.sourceType?.displayName(),
            canReorder = openList.kind != UserListKind.ServerShelf,
            showsBooks = openList.kind != UserListKind.Smart,
        )
        openList != null -> {
            val books = remember(openList, itemsById, progress) { openList.books(itemsById, progress) }
            UserListPage(
                list = openList,
                books = books,
                cover = cover,
                onOpenBook = { book -> openBook(openList, book) },
                onSetOrder = { order -> listsViewModel.setOrder(openList, order) },
                onSetHideFinished = { hide -> listsViewModel.setHideFinished(openList, hide) },
                onEdit = { state.editDraft = openList.toDraft() },
                contentTopInset = contentTopInset,
                onScrolledChanged = { scrolled -> state.scrolled = scrolled },
                modifier = modifier,
                keepDownloadedLine = if (openList.kind == UserListKind.User || openList.kind == UserListKind.WantToRead) {
                    keepDownloadedLine(
                        next = remember(openList, itemsById, progress) { openList.nextToKeepDownloaded(itemsById, progress) },
                        onDevice = uiState.locallyAvailableRemoteItemIds,
                        keeping = openList.keepNextDownloaded,
                    )
                } else {
                    null
                },
                onSetKeepDownloaded = { keep -> listsViewModel.setKeepNextDownloaded(openList, keep) },
            )
        }
        else -> {
            val underWay = remember(stored, itemsById, progress) { readingOrderUnderWay(stored, itemsById, progress) }
            ListsScreen(
                lists = lists,
                underWay = underWay,
                serverLists = shelves,
                itemsById = itemsById,
                progressByItemId = progress,
                searchQuery = state.searchQuery,
                cellSizeDp = uiState.libraryGridCellSizeDp,
                cover = cover,
                onOpenList = { list -> state.openList(list.id) },
                onContinue = { under -> openBook(under.list, under.next) },
                contentTopInset = contentTopInset,
                onScrolledChanged = { scrolled -> state.scrolled = scrolled },
                modifier = modifier,
            )
        }
    }
    val importCbl = rememberCblImport(listsViewModel, uiState) { listId -> state.openList(listId) }
    if (state.isNewListOpen) {
        NewListDialog(
            onImport = {
                state.isNewListOpen = false
                importCbl()
            },
            onCreate = { name, server ->
                state.isNewListOpen = false
                if (server != null) {
                    listsViewModel.createServerList(server.sourceType, name, items = emptyList(), onCreated = state::openList, onFailure = onShelfFailure)
                } else {
                    listsViewModel.createList(name, items = emptyList()) { id -> state.openList(id) }
                }
            },
            onDismiss = { state.isNewListOpen = false },
            servers = newListServers(connected),
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
    val onShelfFailure = rememberShelfFailureToast()
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
                        text = when (draft.kind) {
                            UserListKind.ServerShelf -> "Edit shelf"
                            UserListKind.ServerReadingList -> "Edit reading list"
                            else -> "Edit list"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        onClick = {
                            listsViewModel.applyDraft(draft, onFailure = onShelfFailure)
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

/**
 * Opens [item] from [list]: the list becomes the one under way, and a reading order goes along
 * with the book, so Read next follows it.
 */
internal fun openBookFromList(
    viewModel: AppViewModel,
    listsViewModel: AppListsViewModel,
    list: UserList,
    item: ReadableItem,
) {
    if (list.kind == UserListKind.User || list.kind == UserListKind.WantToRead) listsViewModel.markOpened(list.id)
    viewModel.onListItemSelected(
        item = item,
        listContext = if (list.isReadingOrder) {
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

/** The servers whose lists show: Kavita's reading lists and Grimmory's shelves, while each is connected. */
internal fun connectedListServers(uiState: AppUiState): Set<SourceType> = buildSet {
    if (uiState.kavitaConnection.hasStoredSession) add(SourceType.KAVITA)
    if (uiState.grimmoryConnection.hasStoredSession) add(SourceType.GRIMMORY)
}

/** Where New list can keep a list besides this device: a Kavita reading list, a Grimmory shelf. */
internal fun newListServers(sources: Set<SourceType>): List<NewListServer> = buildList {
    if (SourceType.KAVITA in sources) add(NewListServer.readingList(SourceType.KAVITA))
    if (SourceType.GRIMMORY in sources) add(NewListServer.shelf(SourceType.GRIMMORY))
}
