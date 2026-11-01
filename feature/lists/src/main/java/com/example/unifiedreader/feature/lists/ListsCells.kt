package com.example.unifiedreader.feature.lists

import androidx.compose.ui.unit.Dp
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.ui.components.AppLayout

/** One item of the Lists screen's grid. */
internal sealed interface ListsCell {
    val key: String

    data class Places(val places: List<Pair<ListsFilter, Int?>>) : ListsCell {
        override val key = "places"
    }

    data class Tools(val page: ListsPlacePage) : ListsCell {
        override val key = "tools"
    }

    data class Card(val underWay: UserListUnderWay) : ListsCell {
        override val key = "card"
    }

    data class Heading(
        val id: String,
        val title: String,
        val count: Int?,
        val seeAll: ListsFilter? = null,
        val seeAllCount: Int = 0,
    ) : ListsCell {
        override val key = "heading:$id"
    }

    data class Tile(val section: String, val list: UserList, val caption: String) : ListsCell {
        override val key = "tile:$section:${list.id}"
    }

    data class Strip(val row: ListsServerRow) : ListsCell {
        override val key = "strip:${row.filter.key}"
    }

    /**
     * One line of a panel of rows (one list, or two side by side on Expanded): [top] and [bottom]
     * say whether it starts or ends its panel. A row's line leaves out the kind under a kind's tab
     * ([withKind]) and names the place on All's search ([withPlace]).
     */
    data class Rows(
        val section: String,
        val lists: List<UserList>,
        val top: Boolean,
        val bottom: Boolean,
        val withKind: Boolean = true,
        val withPlace: Boolean = false,
        val highlight: String? = null,
    ) : ListsCell {
        override val key = "rows:$section:${lists.first().id}"

        fun lineOf(list: UserList, progressByItemId: Map<String, ItemProgressSummary>): String {
            val line = list.rowLine(progressByItemId, withKind)
            if (!withPlace) return line
            val place = list.server?.sourceType?.displayName() ?: "This device"
            return "$place · ${line.replaceFirstChar(Char::lowercaseChar)}"
        }
    }

    data class Letter(val letter: Char) : ListsCell {
        override val key = "letter:$letter"
    }

    data class Books(val books: List<ListsFoundBook>, val top: Boolean, val bottom: Boolean) : ListsCell {
        override val key = "books:${books.first().entry.itemId}"
    }

    data class Folds(val finished: List<UserList>, val hidden: List<UserList>) : ListsCell {
        override val key = "folds"
    }

    data class Hidden(val count: Int) : ListsCell {
        override val key = "hidden"
    }

    data class NoMatch(val query: String) : ListsCell {
        override val key = "no-match"
    }
}

/** A cell with the space above and below it, so the grid itself has no vertical spacing of its own. */
internal data class PlacedListsCell(
    val cell: ListsCell,
    val top: Dp,
    val bottom: Dp,
)

/**
 * The screen's cells: the place row, then the search's results while there is a [query]; else
 * All's overview, or [page] for a place. [columns] is how many tiles a grid row holds (Your
 * lists' one row on All), [rowColumns] how many list rows sit side by side.
 */
internal fun buildListsCells(
    catalog: ListsCatalog,
    filter: ListsFilter,
    page: ListsPlacePage?,
    underWay: UserListUnderWay?,
    query: String,
    openFold: ListsFold?,
    columns: Int,
    rowColumns: Int,
): List<PlacedListsCell> {
    val progress = catalog.progressByItemId
    val cells = buildList {
        add(ListsCell.Places(catalog.places))

        fun rows(section: String, lists: List<UserList>, withKind: Boolean = true, withPlace: Boolean = false, highlight: String? = null) {
            val lines = lists.chunked(rowColumns)
            lines.forEachIndexed { index, line ->
                add(ListsCell.Rows(section, line, top = index == 0, bottom = index == lines.lastIndex, withKind, withPlace, highlight))
            }
        }

        when {
            query.isNotEmpty() -> {
                val found = catalog.search(query, filter)
                if (found.lists.isEmpty() && found.books.isEmpty()) {
                    add(ListsCell.NoMatch(query))
                }
                if (found.lists.isNotEmpty()) {
                    add(ListsCell.Heading("found-lists", "Lists", found.lists.size))
                    rows("found", found.lists, withPlace = filter == ListsFilter.All, highlight = query)
                }
                if (found.books.isNotEmpty()) {
                    add(ListsCell.Heading("found-books", "Books in your lists", found.books.size))
                    val lines = found.books.chunked(rowColumns)
                    lines.forEachIndexed { index, line -> add(ListsCell.Books(line, top = index == 0, bottom = index == lines.lastIndex)) }
                }
            }
            page == null -> {
                underWay?.let { add(ListsCell.Card(it)) }
                val overview = catalog.overview()
                if (overview.pinned.isNotEmpty()) {
                    add(ListsCell.Heading("pinned", "Pinned", overview.pinned.size))
                    overview.pinned.forEach { list -> add(ListsCell.Tile("pinned", list, list.placeCaption(progress))) }
                }
                if (overview.yours.isNotEmpty()) {
                    val more = overview.yours.size > columns
                    val count = catalog.visibleOf(ListsFilter.Yours).size
                    add(ListsCell.Heading("yours", "Your lists", count, seeAll = ListsFilter.Yours.takeIf { more }, seeAllCount = count))
                    overview.yours.take(columns).forEach { list -> add(ListsCell.Tile("yours", list, list.tileCaption(progress))) }
                }
                overview.servers.forEach { row ->
                    add(ListsCell.Heading(row.filter.key, row.filter.label(), row.count, seeAll = row.filter, seeAllCount = row.count))
                    if (row.lists.isNotEmpty()) add(ListsCell.Strip(row))
                }
                if (overview.hiddenCount > 0) add(ListsCell.Hidden(overview.hiddenCount))
            }
            else -> {
                add(ListsCell.Tools(page))
                val asRows = page.choice.view == ListsView.Rows
                val withKind = page.choice.kind == ListsKind.All
                fun lists(section: String, lists: List<UserList>) {
                    if (asRows) rows(section, lists, withKind) else lists.forEach { add(ListsCell.Tile(section, it, it.tileCaption(progress))) }
                }
                if (page.recent.isNotEmpty()) {
                    add(ListsCell.Heading("recent", "Recent", page.recent.size))
                    lists("recent", page.recent)
                }
                add(ListsCell.Heading("main", page.heading, page.count))
                if (asRows && page.groups.any { it.letter != null }) {
                    page.groups.forEach { group ->
                        add(ListsCell.Letter(group.letter ?: '#'))
                        rows("letter:${group.letter}", group.lists, withKind)
                    }
                } else {
                    lists("main", page.groups.flatMap(ListsGroup::lists))
                }
                if (page.finished.isNotEmpty() || page.hidden.isNotEmpty()) {
                    add(ListsCell.Folds(page.finished, page.hidden))
                    when (openFold) {
                        ListsFold.Finished -> rows("finished", page.finished, withKind)
                        ListsFold.Hidden -> rows("hidden", page.hidden, withKind)
                        null -> Unit
                    }
                }
            }
        }
    }
    var previous: ListsCell? = null
    return cells.map { cell ->
        PlacedListsCell(cell, top = gapBefore(previous, cell), bottom = gapAfter(cell)).also { previous = cell }
    }
}

/**
 * The space above [cell]: none at the top, inside a panel, under a heading or a letter (they keep
 * their own gap below) or between tiles; a grid gap after the place row and before a letter; a
 * section gap otherwise (less the gap a tile keeps below it).
 */
private fun gapBefore(previous: ListsCell?, cell: ListsCell): Dp = when {
    previous == null -> Dp.Hairline
    previous is ListsCell.Heading || previous is ListsCell.Letter -> Dp.Hairline
    cell is ListsCell.Rows && !cell.top -> Dp.Hairline
    cell is ListsCell.Books && !cell.top -> Dp.Hairline
    cell is ListsCell.Tile && previous is ListsCell.Tile -> Dp.Hairline
    previous is ListsCell.Places -> AppLayout.gridGap
    previous is ListsCell.Folds -> AppLayout.headingGap
    cell is ListsCell.Letter -> AppLayout.gridGap
    previous is ListsCell.Tile -> AppLayout.sectionGap - AppLayout.gridGap
    cell is ListsCell.Hidden -> AppLayout.headingGap
    else -> AppLayout.sectionGap
}

private fun gapAfter(cell: ListsCell): Dp = when (cell) {
    is ListsCell.Heading, is ListsCell.Letter -> AppLayout.headingGap
    is ListsCell.Tile -> AppLayout.gridGap
    else -> Dp.Hairline
}
