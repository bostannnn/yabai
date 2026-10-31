package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppListCover
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppProgressBar
import com.example.unifiedreader.ui.components.AppSectionHeading
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** Draws a book's cover filling the modifier it is given (the library's cover, with its cache). */
typealias ListCover = @Composable (item: ReadableItem, modifier: Modifier) -> Unit

/** Which lists the Lists screen shows: every one, yours, the ones that update themselves, or one server's. */
sealed interface ListsFilter {
    data object All : ListsFilter

    data object Yours : ListsFilter

    data object Smart : ListsFilter

    data class Server(val sourceType: SourceType) : ListsFilter

    /** "all", "yours", "smart", "server:KAVITA": for the route's saved state. */
    val key: String
        get() = when (this) {
            All -> "all"
            Yours -> "yours"
            Smart -> "smart"
            is Server -> "server:${sourceType.name}"
        }

    companion object {
        fun fromKey(key: String?): ListsFilter = when {
            key == "yours" -> Yours
            key == "smart" -> Smart
            key?.startsWith("server:") == true ->
                SourceType.entries.firstOrNull { it.name == key.removePrefix("server:") }?.let(::Server) ?: All
            else -> All
        }
    }
}

/** A section of the Lists screen: "Your lists", or a server's ("Kavita", "Grimmory"), with its lists in order. */
data class ListsSection(
    val key: String,
    val title: String,
    val lists: List<UserList>,
)

/**
 * The Lists screen's sections for [filter]: Your lists (Want to read, Stalled while it holds a
 * book, then yours and your smart lists in [lists]' order), then each server's in [serverLists]:
 * Kavita's reading lists then collections, Grimmory's magic shelves then shelves, each by name.
 * Smart keeps only the lists that update themselves, wherever they live.
 */
fun listsSections(
    lists: List<UserList>,
    serverLists: List<UserList>,
    filter: ListsFilter,
    query: String,
): List<ListsSection> {
    fun shown(list: UserList) = (query.isEmpty() || list.matches(query)) && (filter != ListsFilter.Smart || list.isBuiltFromRules)
    val own = lists
        .filter { list -> list.kind != UserListKind.Stalled || list.entries.isNotEmpty() }
        .filter(::shown)
    val servers = serverLists
        .filter(::shown)
        .groupBy { list -> list.server?.sourceType }
        .entries
        .sortedBy { (source, _) -> source?.ordinal ?: Int.MAX_VALUE }
        .map { (source, sourceLists) ->
            ListsSection(
                key = "server:${source?.name}",
                title = source?.displayName().orEmpty(),
                lists = sourceLists.sortedWith(compareBy<UserList> { it.kind.sectionRank() }.thenBy(String.CASE_INSENSITIVE_ORDER, UserList::name)),
            )
        }
    val ownSection = ListsSection(key = "own", title = "Your lists", lists = own)
    return when (filter) {
        // Your lists always has Want to read, so it shows under All even while a search empties it.
        ListsFilter.All -> listOf(ownSection) + servers
        ListsFilter.Smart -> (listOf(ownSection) + servers).filter { it.lists.isNotEmpty() }
        ListsFilter.Yours -> listOf(ownSection)
        is ListsFilter.Server -> servers.filter { it.key == "server:${filter.sourceType.name}" }
    }
}

/** The filter row's chips with their counts: All, Your lists, Smart, then each server that has lists. */
fun listsFilters(lists: List<UserList>, serverLists: List<UserList>): List<Pair<ListsFilter, Int?>> {
    val own = lists.filter { list -> list.kind != UserListKind.Stalled || list.entries.isNotEmpty() }
    val smart = (own + serverLists).count(UserList::isBuiltFromRules)
    val servers = serverLists.groupBy { it.server?.sourceType }.entries
        .mapNotNull { (source, sourceLists) -> source?.let { ListsFilter.Server(it) to sourceLists.size } }
        .sortedBy { (filter, _) -> (filter as ListsFilter.Server).sourceType.ordinal }
    return buildList {
        add(ListsFilter.All to null)
        add(ListsFilter.Yours to own.size)
        if (smart > 0) add(ListsFilter.Smart to smart)
        addAll(servers)
    }
}

private fun UserListKind.sectionRank(): Int = when (this) {
    UserListKind.ServerReadingList, UserListKind.ServerMagicShelf -> 0
    else -> 1
}

/** "All", "Your lists", "Smart", "Kavita". */
fun ListsFilter.label(): String = when (this) {
    ListsFilter.All -> "All"
    ListsFilter.Yours -> "Your lists"
    ListsFilter.Smart -> "Smart"
    is ListsFilter.Server -> sourceType.displayName()
}

/**
 * Every list in one place: the filter row, the reading order under way as a card, then a section
 * per place ([listsSections]). [lists] come in the screen's order ([orderedForScreen]);
 * [searchQuery] narrows them all by name or by a book in them.
 */
@Composable
fun ListsScreen(
    lists: List<UserList>,
    underWay: UserListUnderWay?,
    serverLists: List<UserList>,
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    searchQuery: String,
    filter: ListsFilter,
    onFilterChange: (ListsFilter) -> Unit,
    cellSizeDp: Int,
    cover: ListCover,
    onOpenList: (UserList) -> Unit,
    onContinue: (UserListUnderWay) -> Unit,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = searchQuery.trim()
    val filters = remember(lists, serverLists) { listsFilters(lists, serverLists) }
    // A server's chip goes when its lists do (disconnected); the row falls back to All.
    val shownFilter = filter.takeIf { chosen -> filters.any { it.first == chosen } } ?: ListsFilter.All
    val sections = remember(lists, serverLists, shownFilter, query) { listsSections(lists, serverLists, shownFilter, query) }
    val gridState = rememberLazyGridState()
    val scrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    LaunchedEffect(scrolled) { onScrolledChanged(scrolled) }
    val gutter = appGutter()
    // A heading after tiles sits a section gap below them; the grid's own gap is part of it.
    val sectionTop = AppLayout.sectionGap - AppLayout.gridGap
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cellSizeDp.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = contentTopInset + AppLayout.contentTop,
            bottom = AppLayout.contentBottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
        verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
    ) {
        if (filters.size > 2) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.forEach { (option, count) ->
                        AppChip(
                            selected = option == shownFilter,
                            onClick = { onFilterChange(option) },
                            label = { Text(option.label()) },
                            count = count,
                        )
                    }
                }
            }
        }
        val showsCard = shownFilter == ListsFilter.All || shownFilter == ListsFilter.Yours
        if (underWay != null && query.isEmpty() && showsCard) {
            item(key = "under-way", span = { GridItemSpan(maxLineSpan) }) {
                ListUnderWayCard(
                    underWay = underWay,
                    cover = cover,
                    onContinue = { onContinue(underWay) },
                    onOpen = { onOpenList(underWay.list) },
                    modifier = Modifier.padding(bottom = sectionTop),
                )
            }
        }
        sections.forEachIndexed { index, section ->
            item(key = "heading:${section.key}", span = { GridItemSpan(maxLineSpan) }) {
                AppSectionHeading(
                    text = section.title,
                    count = section.lists.size.toString(),
                    modifier = if (index > 0) Modifier.padding(top = sectionTop) else Modifier,
                )
            }
            items(section.lists, key = { list -> "list:${list.id}" }) { list ->
                UserListTile(list = list, itemsById = itemsById, progressByItemId = progressByItemId, cover = cover, onClick = { onOpenList(list) })
            }
        }
        if (query.isNotEmpty() && sections.all { it.lists.isEmpty() }) {
            item(key = "no-match", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "No list or book in a list matches “$query”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The reading order under way: what is next, how far you are, and Continue. A tap elsewhere opens the list. */
@Composable
private fun ListUnderWayCard(
    underWay: UserListUnderWay,
    cover: ListCover,
    onContinue: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = underWay.next
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    AppPanel(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AppLayout.inset),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(88.dp)) {
                cover(next.item ?: next.entry.asReadableItem(), Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Reading order · book ${underWay.position} of ${underWay.count}",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = underWay.list.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Next: ${next.item?.libraryBookTitle() ?: next.entry.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                AppProgressBar(
                    fraction = underWay.finishedCount.toFloat() / underWay.count.coerceAtLeast(1),
                    modifier = Modifier.fillMaxWidth(),
                )
                // On a phone Continue goes under the text, which needs the whole width.
                if (compact) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AppButton(onClick = onContinue) {
                        Text(text = "Continue", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!compact) {
                AppButton(onClick = onContinue) {
                    Text(text = "Continue", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun UserListTile(
    list: UserList,
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    cover: ListCover,
    onClick: () -> Unit,
) {
    val coverItems = list.sortedEntries().take(4).map { entry -> itemsById[entry.itemId] ?: entry.asReadableItem() }
    ListTileFrame(name = list.name, caption = list.tileCaption(progressByItemId), onClick = onClick) {
        Box {
            AppListCover(count = coverItems.size) { index, cellModifier -> cover(coverItems[index], cellModifier) }
            if (list.isBuiltFromRules) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = AppIcons.Tune, contentDescription = "Updates itself", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ListTileFrame(
    name: String,
    caption: String,
    onClick: () -> Unit,
    coverContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        coverContent()
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun UserList.matches(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        entries.any { entry ->
            entry.title.contains(query, ignoreCase = true) ||
                entry.primaryText?.contains(query, ignoreCase = true) == true
        }

