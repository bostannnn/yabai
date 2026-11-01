package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.database.dao.ListPreferenceDao
import com.example.unifiedreader.core.database.entity.ListSettingEntity
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What this device keeps about one list: pinned (and when), hidden, and when its page was last opened. */
data class ListPreference(
    val pinnedAtEpochMillis: Long? = null,
    val hidden: Boolean = false,
    val openedAtEpochMillis: Long = 0L,
) {
    val isPinned: Boolean
        get() = pinnedAtEpochMillis != null
}

/** Which of a place's lists show: every kind, or one. */
enum class ListsKind(val label: String) {
    All("All"),
    Smart("Smart"),
    ReadingLists("Reading lists"),
    Collections("Collections"),
    MagicShelves("Magic shelves"),
    Shelves("Shelves"),
    ;

    fun matches(list: UserList): Boolean = when (this) {
        All -> true
        Smart -> list.isBuiltFromRules
        ReadingLists -> list.kind == UserListKind.ServerReadingList
        Collections -> list.kind == UserListKind.ServerCollection
        MagicShelves -> list.kind == UserListKind.ServerMagicShelf
        Shelves -> list.kind == UserListKind.ServerShelf
    }
}

/** How a place sorts its lists; [heading] names the section it makes. */
enum class ListsSort(val label: String, val heading: String) {
    Name("Name", "A to Z"),
    Recent("Recently opened", "Recently opened"),
    Size("Most books", "Most books"),
    Year("Year", "By year"),
}

/** A place's lists as rows or as covers. */
enum class ListsView { Rows, Covers }

/** What finished lists do on a place: fold away at the end, leave Lists, or stay with the rest. */
enum class ListsFinished(val label: String) {
    Fold("At the end"),
    Hide("Hide"),
    WithTheRest("With the rest"),
}

/** A place's kind, sort and Show as. */
data class ListsPlaceChoice(
    val kind: ListsKind,
    val sort: ListsSort,
    val view: ListsView,
)

/**
 * What What Lists shows leaves out: other people's lists on [hideOthers]' servers, empty lists
 * on [hideEmpty]'s, Kavita's collections, and what finished lists do.
 */
data class ListsShowing(
    val hideOthers: Set<SourceType> = emptySet(),
    val hideEmpty: Set<SourceType> = emptySet(),
    val hideCollections: Boolean = false,
    val finished: ListsFinished = ListsFinished.Fold,
)

/** The Lists screen's choices on this device: each place's (by [ListsFilter.key]) and what it shows. */
data class ListsChoices(
    val places: Map<String, ListsPlaceChoice> = emptyMap(),
    val showing: ListsShowing = ListsShowing(),
) {
    /** [filter]'s choice, or where it starts: Your lists as covers, most recently opened first; a server's as rows by name. */
    fun placeChoice(filter: ListsFilter): ListsPlaceChoice = places[filter.key] ?: when (filter) {
        ListsFilter.Yours -> ListsPlaceChoice(ListsKind.All, ListsSort.Recent, ListsView.Covers)
        else -> ListsPlaceChoice(ListsKind.All, ListsSort.Name, ListsView.Rows)
    }
}

/**
 * What this device keeps about lists, every kind alike (pins, hidden ones, when each was last
 * opened), and the Lists screen's choices. Nothing here goes to a server.
 */
interface ListPreferencesStore {
    val preferences: Flow<Map<String, ListPreference>>

    val choices: Flow<ListsChoices>

    /** Pins the lists to the top of Lists (a pinned list shows again if it was hidden), or unpins them. */
    suspend fun setPinned(listIds: List<String>, pinned: Boolean)

    /** Hides the lists from Lists (a hidden list is no longer pinned), or shows them again. */
    suspend fun setHidden(listIds: List<String>, hidden: Boolean)

    /** A list's page was opened: it comes first in Recent. */
    suspend fun markOpened(listId: String)

    suspend fun setPlaceChoice(filter: ListsFilter, choice: ListsPlaceChoice)

    suspend fun setShowing(showing: ListsShowing)
}

class RoomListPreferencesStore(
    private val dao: ListPreferenceDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : ListPreferencesStore {
    override val preferences: Flow<Map<String, ListPreference>> = dao.observePreferences()
        .map { rows ->
            rows.associate { row ->
                row.listId to ListPreference(row.pinnedAtEpochMillis, row.hidden, row.openedAtEpochMillis)
            }
        }
        .flowOn(ioDispatcher)

    override val choices: Flow<ListsChoices> = dao.observeSettings()
        .map { rows -> decodeListsChoices(rows.associate { it.key to it.value }) }
        .flowOn(ioDispatcher)

    override suspend fun setPinned(listIds: List<String>, pinned: Boolean) {
        val at = now()
        io { dao.setPinned(listIds, pinned, at) }
    }

    override suspend fun setHidden(listIds: List<String>, hidden: Boolean) = io {
        dao.setHidden(listIds, hidden)
    }

    override suspend fun markOpened(listId: String) {
        val at = now()
        io { dao.markOpened(listId, at) }
    }

    override suspend fun setPlaceChoice(filter: ListsFilter, choice: ListsPlaceChoice) = io {
        val prefix = "place:${filter.key}:"
        dao.upsertSetting(ListSettingEntity(prefix + "kind", choice.kind.name))
        dao.upsertSetting(ListSettingEntity(prefix + "sort", choice.sort.name))
        dao.upsertSetting(ListSettingEntity(prefix + "view", choice.view.name))
    }

    override suspend fun setShowing(showing: ListsShowing) = io {
        dao.upsertSetting(ListSettingEntity(HideOthersKey, showing.hideOthers.joinToString(",") { it.name }))
        dao.upsertSetting(ListSettingEntity(HideEmptyKey, showing.hideEmpty.joinToString(",") { it.name }))
        dao.upsertSetting(ListSettingEntity(HideCollectionsKey, showing.hideCollections.toString()))
        dao.upsertSetting(ListSettingEntity(FinishedKey, showing.finished.name))
    }

    private suspend fun <T> io(block: () -> T): T = withContext(ioDispatcher) { block() }
}

private const val HideOthersKey = "show:hideOthers"
private const val HideEmptyKey = "show:hideEmpty"
private const val HideCollectionsKey = "show:hideCollections"
private const val FinishedKey = "show:finished"

/** The choices as [RoomListPreferencesStore] writes them, one key each; anything unreadable falls back to where it starts. */
internal fun decodeListsChoices(settings: Map<String, String>): ListsChoices {
    val places = settings.keys
        .filter { it.startsWith("place:") }
        .map { it.removePrefix("place:").substringBeforeLast(':') }
        .distinct()
        .mapNotNull { place ->
            val kind = enumOrNull<ListsKind>(settings["place:$place:kind"]) ?: return@mapNotNull null
            val sort = enumOrNull<ListsSort>(settings["place:$place:sort"]) ?: return@mapNotNull null
            val view = enumOrNull<ListsView>(settings["place:$place:view"]) ?: return@mapNotNull null
            place to ListsPlaceChoice(kind, sort, view)
        }
        .toMap()
    fun sources(key: String): Set<SourceType> = settings[key].orEmpty().split(',').mapNotNull { name -> enumOrNull<SourceType>(name) }.toSet()
    return ListsChoices(
        places = places,
        showing = ListsShowing(
            hideOthers = sources(HideOthersKey),
            hideEmpty = sources(HideEmptyKey),
            hideCollections = settings[HideCollectionsKey] == "true",
            finished = enumOrNull<ListsFinished>(settings[FinishedKey]) ?: ListsFinished.Fold,
        ),
    )
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? = enumValues<T>().firstOrNull { it.name == name }
