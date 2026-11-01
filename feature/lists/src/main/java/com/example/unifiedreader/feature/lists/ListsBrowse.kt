package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.ui.components.AppLetterStripDefaults

/** Where Lists is: All (the overview), Your lists, or one server's lists. */
sealed interface ListsFilter {
    data object All : ListsFilter

    data object Yours : ListsFilter

    data class Server(val sourceType: SourceType) : ListsFilter

    /** "all", "yours", "server:KAVITA": for the route's saved state and each place's choices. */
    val key: String
        get() = when (this) {
            All -> "all"
            Yours -> "yours"
            is Server -> "server:${sourceType.name}"
        }

    companion object {
        /** A saved key back; one this version doesn't know (the Smart chip's, say) is All. */
        fun fromKey(key: String?): ListsFilter = when {
            key == "yours" -> Yours
            key?.startsWith("server:") == true ->
                SourceType.entries.firstOrNull { it.name == key.removePrefix("server:") }?.let(::Server) ?: All
            else -> All
        }
    }
}

/** "All", "Your lists", "Kavita". */
fun ListsFilter.label(): String = when (this) {
    ListsFilter.All -> "All"
    ListsFilter.Yours -> "Your lists"
    is ListsFilter.Server -> sourceType.displayName()
}

/** How many of a server's lists the overview shows in its row. */
const val ServerRowLimit: Int = 12

/** How many lists a place's Recent holds. */
const val RecentLimit: Int = 6

/** How many books in your lists a search shows. */
const val FoundBookLimit: Int = 60

/**
 * Every list Lists works from: [own] (Want to read, Stalled and the user's, in the screen's
 * order) and [server] (every server's), with what this device keeps about each ([preferences])
 * and what What Lists shows leaves out ([showing]).
 */
class ListsCatalog(
    own: List<UserList>,
    val server: List<UserList>,
    val preferences: Map<String, ListPreference>,
    val showing: ListsShowing,
    val progressByItemId: Map<String, ItemProgressSummary>,
) {
    /** The user's lists that can show at all: Stalled only while it holds a book. */
    val own: List<UserList> = own.filter { list -> list.kind != UserListKind.Stalled || list.entries.isNotEmpty() }

    fun preference(list: UserList): ListPreference = preferences[list.id] ?: ListPreference()

    /** Hidden one by one, or left out by a switch in What Lists shows. */
    fun isLeftOut(list: UserList): Boolean = preference(list).hidden || list.isLeftOutBy(showing)

    /** Every book read; an empty list never is. */
    fun isFinished(list: UserList): Boolean =
        list.entries.isNotEmpty() && list.entries.all { entry -> progressByItemId[entry.itemId]?.isFinished == true }

    /** When the list was last opened: its page, or a book from it. */
    fun openedAt(list: UserList): Long = maxOf(preference(list).openedAtEpochMillis, list.lastOpenedAtEpochMillis)

    /** The servers that have lists, in the order the app lists sources. */
    val servers: List<SourceType> = server.mapNotNull { it.server?.sourceType }.distinct().sortedBy(SourceType::ordinal)

    /** Every list of [filter]'s place, left-out ones too. */
    fun listsOf(filter: ListsFilter): List<UserList> = when (filter) {
        ListsFilter.All -> own + server
        ListsFilter.Yours -> own
        is ListsFilter.Server -> server.filter { list -> list.server?.sourceType == filter.sourceType }
    }

    fun visibleOf(filter: ListsFilter): List<UserList> = listsOf(filter).filterNot(::isLeftOut)

    /** The place row: All, Your lists, then each server that has lists, with how many show. */
    val places: List<Pair<ListsFilter, Int?>>
        get() = buildList {
            add(ListsFilter.All to null)
            add(ListsFilter.Yours to visibleOf(ListsFilter.Yours).size)
            servers.forEach { source -> add(ListsFilter.Server(source) to visibleOf(ListsFilter.Server(source)).size) }
        }
}

/** A server's lists that What Lists shows leaves out: someone else's, empty ones, Kavita's collections. */
fun UserList.isLeftOutBy(showing: ListsShowing): Boolean {
    val source = server?.sourceType ?: return false
    return (server.isOwned.not() && source in showing.hideOthers) ||
        (entries.isEmpty() && source in showing.hideEmpty) ||
        (kind == UserListKind.ServerCollection && showing.hideCollections)
}

/** A server's row on All: how many of its lists show, and the ones in the row. */
data class ListsServerRow(
    val filter: ListsFilter.Server,
    val count: Int,
    val lists: List<UserList>,
)

/** All, the overview: pinned lists, your lists, a row per server, and how many lists are hidden. */
data class ListsOverview(
    val pinned: List<UserList>,
    val yours: List<UserList>,
    val servers: List<ListsServerRow>,
    val hiddenCount: Int,
)

/**
 * The overview: every pinned list in the order pinned; your lists in the screen's order; then
 * each server's lists opened lately first and the rest by name, [rowLimit] at most. A pinned list
 * shows only among the pinned ones, and a finished one only while finished lists stay with the rest.
 */
fun ListsCatalog.overview(rowLimit: Int = ServerRowLimit): ListsOverview {
    val visible = visibleOf(ListsFilter.All)
    val pinned = visible.filter { preference(it).isPinned }.sortedBy { preference(it).pinnedAtEpochMillis }
    val pinnedIds = pinned.mapTo(HashSet(), UserList::id)
    val keepsFinished = showing.finished == ListsFinished.WithTheRest
    fun inRow(list: UserList) = list.id !in pinnedIds && (keepsFinished || !isFinished(list))
    val rows = servers.mapNotNull { source ->
        val filter = ListsFilter.Server(source)
        val shown = visibleOf(filter)
        if (shown.isEmpty()) return@mapNotNull null
        val candidates = shown.filter(::inRow)
        val opened = candidates.filter { openedAt(it) > 0L }.sortedByDescending(::openedAt)
        val rest = (candidates - opened.toSet()).sortedWith(byName)
        ListsServerRow(filter, count = shown.size, lists = (opened + rest).take(rowLimit))
    }
    return ListsOverview(
        pinned = pinned,
        yours = visibleOf(ListsFilter.Yours).filter(::inRow),
        servers = rows,
        hiddenCount = listsOf(ListsFilter.All).count(::isLeftOut),
    )
}

/** A run of a place's lists under one letter (null: a section that isn't by name). */
data class ListsGroup(
    val letter: Char?,
    val lists: List<UserList>,
)

/**
 * A place's page: its kinds with counts (none when it holds one kind), the sorts it offers,
 * the choice as it applies ([choice]), Recent, the lists in the sort's order under [heading]
 * ([groups] by letter when by name), then the finished and the hidden ones. [letters] are the
 * ones the letter strip offers, null when it doesn't show.
 */
data class ListsPlacePage(
    val filter: ListsFilter,
    val choice: ListsPlaceChoice,
    val kinds: List<Pair<ListsKind, Int>>,
    val sorts: List<ListsSort>,
    val recent: List<UserList>,
    val heading: String,
    val count: Int,
    val groups: List<ListsGroup>,
    val finished: List<UserList>,
    val hidden: List<UserList>,
    val letters: Set<Char>?,
)

fun ListsCatalog.placePage(filter: ListsFilter, choice: ListsPlaceChoice): ListsPlacePage {
    val all = listsOf(filter)
    val visible = all.filterNot(::isLeftOut)
    val offered = if (filter == ListsFilter.Yours) listOf(ListsKind.Smart) else ServerKinds
    val present = offered.filter { kind -> visible.any(kind::matches) }
    // Tabs while there is a choice: two kinds or more, or Your lists with smart lists among them.
    val kinds = if (present.size >= 2 || (filter == ListsFilter.Yours && present.isNotEmpty())) {
        listOf(ListsKind.All to visible.size) + present.map { kind -> kind to visible.count(kind::matches) }
    } else {
        emptyList()
    }
    val kind = choice.kind.takeIf { it == ListsKind.All || it in present } ?: ListsKind.All
    val sorts = buildList {
        add(ListsSort.Name)
        add(ListsSort.Recent)
        add(ListsSort.Size)
        if (visible.any { it.startYear != null }) add(ListsSort.Year)
    }
    val sort = choice.sort.takeIf { it in sorts } ?: ListsSort.Name
    val inKind = visible.filter(kind::matches)
    val (finished, open) = if (showing.finished == ListsFinished.WithTheRest) {
        emptyList<UserList>() to inKind
    } else {
        inKind.partition(::isFinished)
    }
    val sorted = sortLists(open, sort, filter)
    val groups = if (sort == ListsSort.Name) {
        val byLetter = sorted.groupBy { AppLetterStripDefaults.letterOf(it.name) }
        AppLetterStripDefaults.lettersFor(byLetter.keys).mapNotNull { letter -> byLetter[letter]?.let { ListsGroup(letter, it) } }
    } else {
        listOf(ListsGroup(null, sorted))
    }
    return ListsPlacePage(
        filter = filter,
        choice = ListsPlaceChoice(kind, sort, choice.view),
        kinds = kinds,
        sorts = sorts,
        recent = if (sort == ListsSort.Recent) emptyList() else open.filter { openedAt(it) > 0L }.sortedByDescending(::openedAt).take(RecentLimit),
        heading = sort.heading,
        count = sorted.size,
        groups = groups,
        finished = if (showing.finished == ListsFinished.Fold) finished else emptyList(),
        hidden = all.filter(kind::matches).filter(::isLeftOut),
        letters = if (sort == ListsSort.Name && sorted.size > AppLetterStripDefaults.minimumCount) groups.mapNotNull(ListsGroup::letter).toSet() else null,
    )
}

/**
 * [lists] in [sort]'s order. Recently opened keeps Want to read and Stalled first on Your lists,
 * then the rest by when each was opened or changed; on a server, by when each was opened.
 */
internal fun ListsCatalog.sortLists(lists: List<UserList>, sort: ListsSort, filter: ListsFilter): List<UserList> = when (sort) {
    ListsSort.Name -> lists.sortedWith(byName)
    ListsSort.Recent -> if (filter == ListsFilter.Yours) {
        val builtIn = lists.filter { it.kind == UserListKind.WantToRead || it.kind == UserListKind.Stalled }
        builtIn + (lists - builtIn.toSet()).sortedByDescending { maxOf(openedAt(it), it.updatedAtEpochMillis) }
    } else {
        lists.sortedWith(compareByDescending<UserList>(::openedAt).then(byName))
    }
    ListsSort.Size -> lists.sortedWith(compareByDescending<UserList> { it.entries.size }.then(byName))
    ListsSort.Year -> lists.sortedWith(compareBy<UserList, Int?>(nullsLast()) { it.startYear }.then(byName))
}

private val ServerKinds = listOf(ListsKind.ReadingLists, ListsKind.Collections, ListsKind.MagicShelves, ListsKind.Shelves)

private val byName: Comparator<UserList> = compareBy(String.CASE_INSENSITIVE_ORDER, UserList::name)

/** A book in your lists that a search found, with the lists holding it. */
data class ListsFoundBook(
    val entry: UserListEntry,
    val lists: List<UserList>,
)

/** A search on Lists: the lists named like it, then the books in your lists that are. */
data class ListsSearchResult(
    val lists: List<UserList>,
    val books: List<ListsFoundBook>,
)

/** Searches [filter]'s place (every place on All) for [query], leaving out the lists that don't show. */
fun ListsCatalog.search(query: String, filter: ListsFilter): ListsSearchResult {
    val needle = query.trim()
    if (needle.isEmpty()) return ListsSearchResult(emptyList(), emptyList())
    val scope = visibleOf(filter)
    val books = scope
        .flatMap { list -> list.entries.filter { it.matches(needle) }.map { entry -> entry to list } }
        .groupBy { (entry, _) -> entry.itemId }
        .map { (_, found) -> ListsFoundBook(entry = found.first().first, lists = found.map { it.second }.distinctBy(UserList::id)) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.entry.title })
        .take(FoundBookLimit)
    return ListsSearchResult(
        lists = scope.filter { it.name.contains(needle, ignoreCase = true) }.sortedWith(byName),
        books = books,
    )
}

private fun UserListEntry.matches(query: String): Boolean =
    title.contains(query, ignoreCase = true) || primaryText?.contains(query, ignoreCase = true) == true

/** Where a found book is: "In Blood Moon Rising, 2 of 18 · Night Run", a reading order saying its place. */
fun ListsFoundBook.whereLine(): String = "In " + lists.joinToString(" · ") { list ->
    if (list.isReadingOrder) {
        val number = list.sortedEntries().indexOfFirst { it.itemId == entry.itemId } + 1
        "${list.name}, $number of ${list.entries.size}"
    } else {
        list.name
    }
}

/**
 * A list row's line: what it is (unless [withKind] is false, under a kind's tab), how big, how far
 * you are and, for a Kavita reading list, its years. "Reading list · 9 of 22 read · 2019–2020",
 * "Collection · 9 series", "Shelf · 55 books · 21 read"; your own lists as their tiles say.
 */
fun UserList.rowLine(progressByItemId: Map<String, ItemProgressSummary>, withKind: Boolean = true): String {
    val count = entries.size
    val read = entries.count { entry -> progressByItemId[entry.itemId]?.isFinished == true }
    val books = bookCountLabel(count).lowercase()
    val parts = when (kind) {
        UserListKind.ServerReadingList -> listOf(
            "Reading list".takeIf { withKind },
            if (read > 0) "$read of $count read" else if (count == 1) "1 issue" else "$count issues",
            yearsLabel(),
        )
        UserListKind.ServerCollection -> listOf("Collection".takeIf { withKind }, "$count series")
        UserListKind.ServerMagicShelf -> listOf("Magic shelf".takeIf { withKind }, books)
        UserListKind.ServerShelf -> listOf("Shelf".takeIf { withKind }, books, "$read read".takeIf { read > 0 })
        else -> listOf(tileCaption(progressByItemId))
    }
    return parts.filterNotNull().joinToString(" · ").replaceFirstChar(Char::uppercaseChar)
}

/** "2019–2020", "2021", or null for a list with no years. */
fun UserList.yearsLabel(): String? {
    val start = startYear ?: return null
    val end = endYear?.takeIf { it > start } ?: return start.toString()
    return "$start–$end"
}

/** How far through a reading order you are, for its row's bar: null until something is read, and once all is. */
fun UserList.readFraction(progressByItemId: Map<String, ItemProgressSummary>): Float? {
    if (!isReadingOrder || entries.isEmpty()) return null
    val read = entries.count { entry -> progressByItemId[entry.itemId]?.isFinished == true }
    return if (read in 1 until entries.size) read.toFloat() / entries.size else null
}

/** A pinned tile's caption, naming where the list lives: "Kavita · reading list · 9 of 22 read", "This device · 9 books". */
fun UserList.placeCaption(progressByItemId: Map<String, ItemProgressSummary>): String {
    val place = server?.sourceType?.displayName() ?: "This device"
    return "$place · ${tileCaption(progressByItemId).replaceFirstChar(Char::lowercaseChar)}"
}
