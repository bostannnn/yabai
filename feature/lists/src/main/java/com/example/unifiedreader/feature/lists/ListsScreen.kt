package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppLetterStrip
import com.example.unifiedreader.ui.components.AppLetterStripDefaults
import com.example.unifiedreader.ui.components.AppListRowDefaults
import com.example.unifiedreader.ui.components.AppListRowDivider
import com.example.unifiedreader.ui.components.AppPanelDefaults
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import kotlinx.coroutines.launch

/** Draws a book's cover filling the modifier it is given (the library's cover, with its cache). */
typealias ListCover = @Composable (item: ReadableItem, modifier: Modifier) -> Unit

/** What the Lists screen asks of whoever shows it. */
class ListsScreenActions(
    val onFilterChange: (ListsFilter) -> Unit,
    val onPlaceChoiceChange: (ListsFilter, ListsPlaceChoice) -> Unit,
    val onOpenList: (UserList) -> Unit,
    val onContinue: (UserListUnderWay) -> Unit,
    val onOpenBook: (ReadableItem) -> Unit,
    val onPin: (List<UserList>, Boolean) -> Unit,
    val onHide: (List<UserList>, Boolean) -> Unit,
    val onEdit: (UserList) -> Unit,
    val onSelectionChange: (Set<String>?) -> Unit,
    val onShowWhatListsShows: () -> Unit,
)

/**
 * Every list in one place. The place row at the top; on All, the overview (the reading order under
 * way, Pinned, Your lists, a row per server); under a place, its tool row and all its lists, as rows
 * (Recent, then A to Z under letters with the letter strip) or covers, then Finished and Hidden
 * folded. [searchQuery] turns it into the search: lists by name, then the books in your lists.
 * [selection] is non-null while several lists are being picked.
 */
@Composable
fun ListsScreen(
    catalog: ListsCatalog,
    underWay: UserListUnderWay?,
    itemsById: Map<String, ReadableItem>,
    searchQuery: String,
    filter: ListsFilter,
    choices: ListsChoices,
    selection: Set<String>?,
    cellSizeDp: Int,
    cover: ListCover,
    actions: ListsScreenActions,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val query = searchQuery.trim()
    val places = catalog.places
    // A server's chip goes when its lists do (disconnected); the row falls back to All.
    val shown = filter.takeIf { chosen -> places.any { it.first == chosen } } ?: ListsFilter.All
    val rowColumns = if (currentAppWindowWidth() == AppWindowWidth.Expanded) 2 else 1
    var openFold by remember(shown) { mutableStateOf<ListsFold?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    LaunchedEffect(scrolled) { onScrolledChanged(scrolled) }
    // Back at the top when the place changes here; coming back from a list's page keeps the place.
    var shownBefore by rememberSaveable { mutableStateOf(shown.key) }
    LaunchedEffect(shown) {
        if (shown.key != shownBefore) {
            shownBefore = shown.key
            gridState.scrollToItem(0)
        }
    }
    val gutter = appGutter()
    val gap = AppLayout.gridGap
    BoxWithConstraints(modifier = modifier) {
        val available = maxWidth - gutter * 2
        val columns = ((available + gap) / (cellSizeDp.dp + gap)).toInt().coerceAtLeast(1)
        val cellWidth = (available - gap * (columns - 1)) / columns
        val page = remember(catalog, shown, choices) {
            if (shown == ListsFilter.All) null else catalog.placePage(shown, choices.placeChoice(shown))
        }
        val cells = remember(catalog, underWay, shown, page, query, openFold, columns, rowColumns) {
            buildListsCells(catalog, shown, page, underWay, query, openFold, columns, rowColumns)
        }
        val progress = catalog.progressByItemId

        fun tap(list: UserList) {
            if (selection == null) {
                actions.onOpenList(list)
            } else {
                actions.onSelectionChange(if (list.id in selection) selection - list.id else selection + list.id)
            }
        }

        @Composable
        fun WithMenu(menuKey: String, list: UserList, content: @Composable (onLongClick: (() -> Unit)?, pressed: Boolean) -> Unit) {
            Box {
                content(if (selection == null) ({ menuFor = menuKey }) else null, menuFor == menuKey)
                ListMenu(
                    list = list,
                    preference = catalog.preference(list),
                    expanded = menuFor == menuKey,
                    onDismiss = { menuFor = null },
                    onPin = { pin -> actions.onPin(listOf(list), pin) },
                    onHide = { hide -> actions.onHide(listOf(list), hide) },
                    onSelect = { actions.onSelectionChange(setOf(list.id)) },
                    onEdit = { actions.onEdit(list) },
                )
            }
        }

        @Composable
        fun Tile(section: String, list: UserList, caption: String, modifier: Modifier = Modifier) {
            WithMenu("$section:${list.id}", list) { onLongClick, _ ->
                ListTile(
                    list = list,
                    caption = caption,
                    itemsById = itemsById,
                    cover = cover,
                    onClick = { tap(list) },
                    onLongClick = onLongClick,
                    selected = selection?.contains(list.id),
                    modifier = modifier,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = contentTopInset + AppLayout.contentTop,
                bottom = AppLayout.contentBottom,
            ),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(
                items = cells,
                key = { placed -> placed.cell.key },
                span = { placed -> if (placed.cell is ListsCell.Tile) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
                contentType = { placed -> placed.cell::class },
            ) { placed ->
                val cellModifier = Modifier.padding(top = placed.top, bottom = placed.bottom)
                when (val cell = placed.cell) {
                    is ListsCell.Places -> Row(
                        modifier = cellModifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
                    ) {
                        cell.places.forEach { (place, count) ->
                            AppChip(
                                selected = place == shown,
                                onClick = { actions.onFilterChange(place) },
                                label = { Text(place.label()) },
                                count = count,
                            )
                        }
                    }
                    is ListsCell.Tools -> ListsToolRow(
                        page = cell.page,
                        onChoiceChange = { choice -> actions.onPlaceChoiceChange(shown, choice) },
                        modifier = cellModifier,
                    )
                    is ListsCell.Card -> ListUnderWayCard(
                        underWay = cell.underWay,
                        cover = cover,
                        onContinue = { actions.onContinue(cell.underWay) },
                        onOpen = { actions.onOpenList(cell.underWay.list) },
                        modifier = cellModifier,
                    )
                    is ListsCell.Heading -> ListsHeading(
                        title = cell.title,
                        count = cell.count,
                        seeAll = cell.seeAll?.let { place -> { actions.onFilterChange(place) } },
                        seeAllCount = cell.seeAllCount,
                        modifier = cellModifier,
                    )
                    is ListsCell.Tile -> Tile(cell.section, cell.list, cell.caption, cellModifier)
                    is ListsCell.Strip -> LazyRow(
                        // Runs past the end gutter, so the row reads as one that scrolls.
                        modifier = cellModifier.layout { measurable, constraints ->
                            val wide = constraints.maxWidth + gutter.roundToPx()
                            val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
                            layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(0, 0) }
                        },
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(end = gutter),
                    ) {
                        items(cell.row.lists, key = { list -> list.id }) { list ->
                            Tile("strip", list, list.tileCaption(progress), Modifier.width(cellWidth))
                        }
                    }
                    is ListsCell.Rows -> PanelSegment(cell.top, cell.nextCount, rowColumns, cell.lists.size, cellModifier) { index ->
                        val list = cell.lists[index]
                        WithMenu("${cell.section}:${list.id}", list) { onLongClick, pressed ->
                            ListRow(
                                list = list,
                                line = cell.lineOf(list, progress),
                                itemsById = itemsById,
                                cover = cover,
                                onClick = { tap(list) },
                                onLongClick = onLongClick,
                                progress = list.readFraction(progress),
                                pinned = catalog.preference(list).isPinned,
                                selected = selection?.contains(list.id),
                                pressed = pressed,
                                highlight = cell.highlight,
                            )
                        }
                    }
                    is ListsCell.Books -> PanelSegment(cell.top, cell.nextCount, rowColumns, cell.books.size, cellModifier) { index ->
                        val book = cell.books[index]
                        FoundBookRow(
                            book = book,
                            itemsById = itemsById,
                            cover = cover,
                            onClick = { actions.onOpenBook(itemsById[book.entry.itemId] ?: book.entry.asReadableItem()) },
                        )
                    }
                    is ListsCell.Letter -> Text(
                        text = cell.letter.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = cellModifier,
                    )
                    is ListsCell.Folds -> ListsFolds(
                        finished = cell.finished,
                        hidden = cell.hidden,
                        openFold = openFold,
                        onToggle = { fold -> openFold = if (openFold == fold) null else fold },
                        modifier = cellModifier,
                    )
                    is ListsCell.Hidden -> Row(modifier = cellModifier, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (cell.count == 1) "1 list hidden" else "${cell.count} lists hidden",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AppTextButton(onClick = actions.onShowWhatListsShows) {
                            Text(text = "What Lists shows", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    is ListsCell.NoMatch -> Text(
                        text = "No list or book in a list matches “${cell.query}”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = cellModifier,
                    )
                }
            }
        }

        val letters = page?.letters
        if (letters != null && query.isEmpty() && page.choice.view == ListsView.Rows) {
            val letterCells = remember(cells) {
                cells.mapIndexedNotNull { index, placed -> (placed.cell as? ListsCell.Letter)?.let { index to it.letter } }
            }
            val current by remember(letterCells) {
                derivedStateOf {
                    val first = gridState.firstVisibleItemIndex
                    letterCells.lastOrNull { (index, _) -> index <= first }?.second
                }
            }
            val strip = remember(letters) { AppLetterStripDefaults.lettersFor(letters) }
            val top = contentTopInset + AppLayout.contentTop
            val fitted = (maxHeight - top - AppLayout.contentBottom) / strip.size
            val wanted = if (currentAppWindowWidth() == AppWindowWidth.Compact) AppLetterStripDefaults.letterHeightCompact else AppLetterStripDefaults.letterHeight
            // The strip sits in the end gutter, centred in it where it fits.
            val stripEnd = ((gutter - AppLetterStripDefaults.width) / 2).coerceAtLeast(Dp.Hairline)
            AppLetterStrip(
                letters = strip,
                present = letters,
                current = current,
                onLetter = { letter ->
                    letterCells.firstOrNull { it.second == letter }?.let { (index, _) -> scope.launch { gridState.scrollToItem(index) } }
                },
                letterHeight = minOf(wanted, fitted),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = top, end = stripEnd),
            )
        }
    }
}

/**
 * One line of panels of rows split over lazy items: [count] of [columns] slots filled, each column
 * its own panel, a [gap] apart. A panel has its corners only where it starts ([top]) and where it
 * ends: on the last line, or where the next line ([nextCount]) leaves its column empty. Rows below
 * the first are divided.
 */
@Composable
private fun PanelSegment(
    top: Boolean,
    nextCount: Int,
    columns: Int,
    count: Int,
    modifier: Modifier,
    gap: Dp = AppLayout.gridGap,
    row: @Composable (index: Int) -> Unit,
) {
    // Both columns as tall as the line: one may end its panel (padding below) while the other runs on.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(columns) { index ->
            if (index >= count) {
                Spacer(modifier = Modifier.weight(1f))
                return@repeat
            }
            val bottom = index >= nextCount
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(AppListRowDefaults.segmentShape(top, bottom))
                    .background(AppPanelDefaults.containerColor)
                    .padding(
                        top = if (top) AppListRowDefaults.panelPadding else Dp.Hairline,
                        bottom = if (bottom) AppListRowDefaults.panelPadding else Dp.Hairline,
                    ),
            ) {
                if (!top) AppListRowDivider()
                row(index)
            }
        }
    }
}
