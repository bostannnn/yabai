package com.example.unifiedreader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import com.example.unifiedreader.feature.lists.toSmartDraft
import com.example.unifiedreader.feature.lists.SmartListEditor
import com.example.unifiedreader.feature.lists.SmartListDraft
import com.example.unifiedreader.ui.components.AppMenuItem
import com.example.unifiedreader.ui.components.AppMenu
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarSearchField
import com.example.unifiedreader.feature.library.VisibleResolvedLibraryCover
import com.example.unifiedreader.feature.lists.ListCover
import com.example.unifiedreader.feature.lists.ListsCatalog
import com.example.unifiedreader.feature.lists.ListsFilter
import com.example.unifiedreader.feature.lists.ListsRouteState
import com.example.unifiedreader.feature.lists.ListsScreen
import com.example.unifiedreader.feature.lists.ListsScreenActions
import com.example.unifiedreader.feature.lists.WhatListsShowsSheet
import com.example.unifiedreader.feature.lists.NewListDialog
import com.example.unifiedreader.feature.lists.NewListServer
import com.example.unifiedreader.feature.lists.UserList
import com.example.unifiedreader.feature.lists.UserListBook
import com.example.unifiedreader.feature.lists.UserListEditor
import com.example.unifiedreader.feature.lists.UserListKind
import com.example.unifiedreader.feature.lists.UserListPage
import com.example.unifiedreader.feature.lists.books
import com.example.unifiedreader.feature.lists.keepDownloadedLine
import com.example.unifiedreader.feature.lists.listItemsById
import com.example.unifiedreader.feature.lists.nextToKeepDownloaded
import com.example.unifiedreader.feature.lists.orderedForScreen
import com.example.unifiedreader.feature.lists.readingOrderUnderWay
import com.example.unifiedreader.feature.lists.sortedEntries
import com.example.unifiedreader.feature.lists.stalledList
import com.example.unifiedreader.feature.lists.toDraft
import com.example.unifiedreader.feature.lists.toUserList
import com.example.unifiedreader.feature.lists.withSmartBooks
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppListRowDefaults
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppToastMessage
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.LocalAppToastState
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
    // A Kavita collection's series opens in its library.
    onOpenSeries: (ReadableItem) -> Unit = {},
    // Where you sorted each tag, for the smart list editor's Tag rule.
    tagKindOf: (String) -> LibraryTagKind = { LibraryTagKind.THEME },
    // A book that a search on Lists found opens its page in its library.
    onOpenBookPage: (ReadableItem) -> Unit = {},
) {
    val stored by listsViewModel.lists.collectAsStateWithLifecycle()
    val storedShelves by listsViewModel.shelves.collectAsStateWithLifecycle()
    val preferences by listsViewModel.preferences.collectAsStateWithLifecycle()
    val choices by listsViewModel.choices.collectAsStateWithLifecycle()
    // With the series Kavita's collections hold, which the catalog lists only as issues.
    val itemsById = remember(uiState.items, storedShelves) { listItemsById(uiState.items, storedShelves.orEmpty()) }
    SideEffect { listsViewModel.catalog = itemsById }
    val progress = uiState.progressByItemId
    val stalled = remember(uiState.items, progress) {
        stalledList(uiState.items, progress, nowEpochMillis = System.currentTimeMillis())
    }
    val onDevice = uiState.locallyAvailableRemoteItemIds
    val resolved = remember(stored, uiState.items, progress, onDevice) { stored.withSmartBooks(uiState.items, progress, onDevice) }
    val lists = remember(resolved, stalled) { orderedForScreen(resolved, stalled) }
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
    BackHandler(enabled = state.canGoBack) { state.back() }
    // A server's list reads its books again as its page opens, and every list's page opening puts it first in Recent.
    LaunchedEffect(openList?.id) {
        openList?.takeIf { it.server != null }?.let(listsViewModel::refreshList)
        openList?.let { listsViewModel.markPageOpened(it.id) }
    }
    val catalog = remember(lists, shelves, preferences, choices.showing, progress) {
        ListsCatalog(own = lists, server = shelves, preferences = preferences, showing = choices.showing, progressByItemId = progress)
    }
    // Select all picks what the current place shows.
    SideEffect { state.selectableIds = catalog.visibleOf(state.filter).map(UserList::id) }

    fun openBook(list: UserList, book: UserListBook) {
        val item = book.item ?: return
        // A collection's comic series opens in the library; a book (a book series' first) opens as any list's does.
        if (item.isKavitaSeriesContainer()) onOpenSeries(item) else openBookFromList(viewModel, listsViewModel, list, item)
    }

    val draft = state.editDraft
    val smartDraft = state.smartDraft
    when {
        smartDraft != null -> SmartListEditor(
            draft = smartDraft,
            onDraftChange = { state.smartDraft = it },
            items = uiState.items,
            progressByItemId = progress,
            onDevice = onDevice,
            servers = magicShelfServers(connected, listsViewModel),
            canDelete = !smartDraft.isNew,
            onDelete = {
                val list = openList
                state.smartDraft = null
                if (list != null) {
                    listsViewModel.deleteList(list, onFailure = onShelfFailure)
                    state.back()
                }
            },
            cover = cover,
            contentTopInset = contentTopInset,
            modifier = modifier,
            shelfNames = remember(storedShelves, connected) {
                storedShelves.orEmpty()
                    .filter { shelf -> shelf.sourceType == SourceType.GRIMMORY && shelf.kind == UserListKind.ServerShelf && shelf.sourceType in connected }
                    .map { shelf -> shelf.remote.name }
            },
            tagKindOf = tagKindOf,
        )
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
            canReorder = openList.kind != UserListKind.ServerShelf && openList.kind != UserListKind.ServerCollection,
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
                onEdit = {
                    // A smart list or magic shelf edits its rules; any other list its books.
                    if (openList.smart != null) state.smartDraft = openList.toSmartDraft() else state.editDraft = openList.toDraft()
                },
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
            val actions = remember(state, listsViewModel, onOpenBookPage) {
                ListsScreenActions(
                    onFilterChange = { filter -> state.filter = filter },
                    onPlaceChoiceChange = listsViewModel::setPlaceChoice,
                    onOpenList = { list -> state.openList(list.id) },
                    onContinue = { under -> openBook(under.list, under.next) },
                    onOpenBook = onOpenBookPage,
                    onPin = { chosen, pin -> listsViewModel.setPinned(chosen.map(UserList::id), pin) },
                    onHide = { chosen, hide -> listsViewModel.setHidden(chosen.map(UserList::id), hide) },
                    onEdit = { list ->
                        state.openList(list.id)
                        if (list.smart != null) state.smartDraft = list.toSmartDraft() else state.editDraft = list.toDraft()
                    },
                    onSelectionChange = { selection -> state.selection = selection },
                    onShowWhatListsShows = { state.isWhatListsShowsOpen = true },
                )
            }
            ListsScreen(
                catalog = catalog,
                underWay = underWay.takeIf { state.searchQuery.isBlank() && state.filter == ListsFilter.All },
                itemsById = itemsById,
                searchQuery = state.searchQuery,
                filter = state.filter,
                choices = choices,
                selection = state.selection,
                cellSizeDp = uiState.libraryGridCellSizeDp,
                cover = cover,
                actions = actions,
                contentTopInset = contentTopInset,
                onScrolledChanged = { scrolled -> state.scrolled = scrolled },
                modifier = modifier,
                gridState = state.gridState,
            )
        }
    }
    if (state.isWhatListsShowsOpen) {
        WhatListsShowsSheet(
            catalog = catalog,
            onShowingChange = listsViewModel::setShowing,
            onShowAgain = { list -> listsViewModel.setHidden(listOf(list.id), hidden = false) },
            onDismiss = { state.isWhatListsShowsOpen = false },
        )
    }
    val importCbl = rememberCblImport(listsViewModel, uiState) { listId -> state.openList(listId) }
    LaunchedEffect(state.isImportRequested) {
        if (state.isImportRequested) {
            state.isImportRequested = false
            importCbl()
        }
    }
    if (state.isNewListOpen) {
        NewListDialog(
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
 * The Lists bar: the menu, "Search lists", New (List, Smart list, Import a reading order) and ⋯
 * (What Lists shows, Select several) on the lists; Close, how many are picked, Select all, Pin
 * and Hide while several are selected; Back on a list; Cancel, "Edit list" and Done while one is
 * edited, and Close, "New smart list" and Create in the smart list editor. No glass until
 * something scrolls under it.
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
            val smartDraft = state.smartDraft
            val selection = state.selection
            when {
                selection != null && state.openListId == null -> ListsSelectionBar(state, selection, listsViewModel)
                smartDraft != null -> {
                    AppIconButton(onClick = { state.smartDraft = null }) {
                        Icon(imageVector = AppIcons.Close, contentDescription = if (smartDraft.isNew) "Cancel the new smart list" else "Cancel editing")
                    }
                    Text(
                        text = when {
                            smartDraft.isNew -> "New smart list"
                            smartDraft.onServer != null -> "Edit magic shelf"
                            else -> "Edit smart list"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        onClick = {
                            state.smartDraft = null
                            listsViewModel.saveSmartDraft(smartDraft, onCreated = state::openList, onFailure = onShelfFailure)
                        },
                        enabled = smartDraft.canSave,
                    ) {
                        Text(text = if (smartDraft.isNew) "Create" else "Done", fontWeight = FontWeight.SemiBold)
                    }
                }
                draft != null -> {
                    AppIconButton(onClick = { state.editDraft = null }) {
                        Icon(imageVector = AppIcons.Close, contentDescription = "Cancel editing")
                    }
                    Text(
                        text = when (draft.kind) {
                            UserListKind.ServerShelf -> "Edit shelf"
                            UserListKind.ServerCollection -> "Edit collection"
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
                    Spacer(modifier = Modifier.weight(1f))
                    state.openListId?.let { listId -> ListPageMoreButton(listId, listsViewModel) }
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
                    NewListMenuButton(
                        onList = { state.isNewListOpen = true },
                        onSmartList = { state.smartDraft = SmartListDraft() },
                        onImport = { state.isImportRequested = true },
                    )
                    ListsMoreMenuButton(
                        onWhatListsShows = { state.isWhatListsShowsOpen = true },
                        onSelect = { state.selection = emptySet() },
                    )
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
    if (list.kind == UserListKind.User || list.kind == UserListKind.WantToRead || list.kind == UserListKind.Smart) listsViewModel.markOpened(list.id)
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

/** New: a menu of what to make, each saying what it is. */
@Composable
private fun NewListMenuButton(onList: () -> Unit, onSmartList: () -> Unit, onImport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppIconButton(onClick = { open = true }) {
            Icon(imageVector = AppIcons.Add, contentDescription = "New")
        }
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            AppMenuItem(
                text = { Text("List") },
                supporting = "You pick the books",
                leadingIcon = { Icon(imageVector = AppIcons.Checklist, contentDescription = null) },
                onClick = { open = false; onList() },
            )
            AppMenuItem(
                text = { Text("Smart list") },
                supporting = "Picked by rules, kept up to date",
                leadingIcon = { Icon(imageVector = AppIcons.Tune, contentDescription = null) },
                onClick = { open = false; onSmartList() },
            )
            AppMenuItem(
                text = { Text("Import a reading order") },
                supporting = "From a .cbl file",
                leadingIcon = { Icon(imageVector = AppIcons.Description, contentDescription = null) },
                onClick = { open = false; onImport() },
            )
        }
    }
}

/** ⋯ on a list's page: Pin to the top (or Unpin) and Hide from Lists (or Show again), as its long-press menu offers. */
@Composable
private fun ListPageMoreButton(listId: String, listsViewModel: AppListsViewModel) {
    val preferences by listsViewModel.preferences.collectAsStateWithLifecycle()
    val preference = preferences[listId]
    var open by remember { mutableStateOf(false) }
    Box {
        AppIconButton(onClick = { open = true }) {
            Icon(imageVector = AppIcons.MoreVert, contentDescription = "More for this list")
        }
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            val pinned = preference?.isPinned == true
            AppMenuItem(
                text = { Text(if (pinned) "Unpin" else "Pin to the top") },
                leadingIcon = { Icon(imageVector = if (pinned) AppIcons.KeepOff else AppIcons.Keep, contentDescription = null) },
                onClick = { open = false; listsViewModel.setPinned(listOf(listId), pinned = !pinned) },
            )
            val hidden = preference?.hidden == true
            AppMenuItem(
                text = { Text(if (hidden) "Show again" else "Hide from Lists") },
                leadingIcon = { Icon(imageVector = AppIcons.VisibilityOff, contentDescription = null) },
                onClick = { open = false; listsViewModel.setHidden(listOf(listId), hidden = !hidden) },
            )
        }
    }
}

/** ⋯ on the lists: What Lists shows, and Select several. */
@Composable
private fun ListsMoreMenuButton(onWhatListsShows: () -> Unit, onSelect: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppIconButton(onClick = { open = true }) {
            Icon(imageVector = AppIcons.MoreVert, contentDescription = "More")
        }
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            AppMenuItem(
                text = { Text("What Lists shows") },
                leadingIcon = { Icon(imageVector = AppIcons.VisibilityOff, contentDescription = null) },
                onClick = { open = false; onWhatListsShows() },
            )
            AppMenuItem(
                text = { Text("Select several") },
                leadingIcon = { Icon(imageVector = AppIcons.SelectAll, contentDescription = null) },
                onClick = { open = false; onSelect() },
            )
        }
    }
}

/**
 * The bar while several lists are picked: Close, how many, Select all, then Pin and Hide, which
 * end the selection and say what they did in a toast with Undo.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ListsSelectionBar(
    state: ListsRouteState,
    selection: Set<String>,
    listsViewModel: AppListsViewModel,
) {
    val toasts = LocalAppToastState.current
    AppIconButton(onClick = { state.selection = null }) {
        Icon(imageVector = AppIcons.Close, contentDescription = "Stop selecting")
    }
    Text(
        text = if (selection.isEmpty()) "Select lists" else "${selection.size} selected",
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    AppIconButton(onClick = { state.selection = state.selectableIds.toSet() }) {
        Icon(imageVector = AppIcons.SelectAll, contentDescription = "Select all")
    }
    val ids = selection.toList()
    AppTonalButton(
        onClick = {
            // Only the ones not pinned yet, so Undo leaves the others pinned.
            val preferences = listsViewModel.preferences.value
            val pinning = ids.filter { id -> preferences[id]?.isPinned != true }
            state.selection = null
            listsViewModel.setPinned(pinning, pinned = true)
            toasts?.show(
                AppToastMessage(
                    text = if (pinning.size == 1) "1 list pinned" else "${pinning.size} lists pinned",
                    actionLabel = "Undo",
                    onAction = { listsViewModel.setPinned(pinning, pinned = false) },
                ),
            )
        },
        enabled = ids.isNotEmpty(),
    ) {
        Icon(imageVector = AppIcons.Keep, contentDescription = null, modifier = Modifier.size(AppListRowDefaults.markSize))
        Spacer(modifier = Modifier.width(AppLayout.headingGap))
        Text(text = "Pin", fontWeight = FontWeight.SemiBold)
    }
    AppButton(
        onClick = {
            state.selection = null
            listsViewModel.setHidden(ids, hidden = true)
            toasts?.show(
                AppToastMessage(
                    text = if (ids.size == 1) "1 list hidden" else "${ids.size} lists hidden",
                    actionLabel = "Undo",
                    onAction = { listsViewModel.setHidden(ids, hidden = false) },
                ),
            )
        },
        enabled = ids.isNotEmpty(),
    ) {
        Icon(imageVector = AppIcons.VisibilityOff, contentDescription = null, modifier = Modifier.size(AppListRowDefaults.markSize))
        Spacer(modifier = Modifier.width(AppLayout.headingGap))
        Text(text = "Hide", fontWeight = FontWeight.SemiBold)
    }
}

/** Where else a smart list can be kept: Grimmory, as a magic shelf, while it is connected. */
internal fun magicShelfServers(connected: Set<SourceType>, listsViewModel: AppListsViewModel): List<SourceType> =
    listOf(SourceType.GRIMMORY).filter { source -> source in connected && listsViewModel.keepsRuledLists(source) }

/**
 * Where you sorted [tag]: the first source that puts it under Set in or When, else About (a tag
 * nobody has sorted is About, as in Filters).
 */
internal fun Map<SourceType, LibraryTagSorting>.tagKindOf(tag: String): LibraryTagKind =
    values.map { sorting -> sorting.kindOf(tag) }.firstOrNull { kind -> kind != LibraryTagKind.THEME } ?: LibraryTagKind.THEME
