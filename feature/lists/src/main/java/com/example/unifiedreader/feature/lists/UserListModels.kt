package com.example.unifiedreader.feature.lists

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import java.util.UUID

/**
 * What a list is: one the user made, the built-in Want to read, Stalled, which works itself out,
 * or a shelf kept on a server (a Grimmory shelf), which holds only that server's books.
 */
enum class UserListKind {
    User,
    WantToRead,
    Stalled,
    ServerShelf,
    ServerReadingList,

    /** Built from rules on this device: its books are whatever its rules pick out now, from every source. */
    Smart,

    /** A Grimmory magic shelf: rules kept on the server, which picks its books; Grimmory books only. */
    ServerMagicShelf,

    /** A Kavita collection: series kept together on the server, in no order. */
    ServerCollection,
}

/**
 * How a list sorts its books. Only [Custom] numbers them, and only a list in [Custom] order is a
 * reading order that Read next follows.
 */
enum class UserListOrder(val label: String) {
    Custom("Your order"),
    DateAdded("Date added"),
    Title("Title"),

    /** Oldest first; for lists built from rules. */
    Published("Published"),
}

/**
 * A book in a list, with the copy of its display fields taken when it was added, so its row still
 * shows when the book has left the library.
 */
data class UserListEntry(
    val itemId: String,
    val position: Int,
    val addedAtEpochMillis: Long,
    val sourceType: SourceType,
    val libraryId: String,
    val title: String,
    val primaryText: String?,
    val secondaryText: String?,
    val coverUrl: String?,
    val kind: ReadableItemKind,
    val format: FormatType,
    /** For sorting lists built from rules by Published; not kept for a list's own entries. */
    val publishedYear: Int? = null,
)

/** A list and its books, [entries] in the list's own order (position). */
data class UserList(
    val id: String,
    val name: String,
    val description: String?,
    val kind: UserListKind,
    val order: UserListOrder,
    val hideFinished: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long,
    val entries: List<UserListEntry>,
    /** Where a server's shelf lives; null for a list on this device. */
    val server: ServerListRef? = null,
    /** Its next three unread server books are kept downloaded. */
    val keepNextDownloaded: Boolean = false,
    /** A smart list's or magic shelf's rules; null for every other list, and for a magic shelf whose rules the app can't read. */
    val smart: SmartListSpec? = null,
    /** A Kavita reading list's first and last year, from its server; null for every other list. */
    val startYear: Int? = null,
    val endYear: Int? = null,
) {
    val isReadingOrder: Boolean
        get() = order == UserListOrder.Custom

    /**
     * Stalled is worked out from your reading, and someone else's public shelf is theirs: nothing
     * is added to either or changed in them.
     */
    val isEditable: Boolean
        get() = kind != UserListKind.Stalled && server?.isOwned != false

    /**
     * The orders the list can take. A server's shelf keeps no order of its own, so it has no Your
     * order; a server's reading list has only its own.
     */
    val orders: List<UserListOrder>
        get() = when (kind) {
            UserListKind.ServerShelf, UserListKind.ServerCollection -> ShelfOrders
            UserListKind.Smart, UserListKind.ServerMagicShelf, UserListKind.Stalled -> RuleOrders
            UserListKind.ServerReadingList -> listOf(UserListOrder.Custom)
            else -> OwnOrders
        }

    /** Whether its books come from rules (a smart list, a magic shelf, Stalled), so nothing is added to it by hand. */
    val isBuiltFromRules: Boolean
        get() = kind == UserListKind.Smart || kind == UserListKind.ServerMagicShelf || kind == UserListKind.Stalled

    fun contains(itemId: String): Boolean = entries.any { entry -> entry.itemId == itemId }
}

/** A shelf's place on its server: which source, its id there, and whether this user made it. */
data class ServerListRef(
    val sourceType: SourceType,
    val remoteId: String,
    val isOwned: Boolean,
)

private val ShelfOrders = listOf(UserListOrder.DateAdded, UserListOrder.Title)
private val RuleOrders = listOf(UserListOrder.DateAdded, UserListOrder.Title, UserListOrder.Published)
private val OwnOrders = listOf(UserListOrder.Custom, UserListOrder.DateAdded, UserListOrder.Title)

object UserListIds {
    const val WantToRead = "list:want-to-read"
    const val Stalled = "list:stalled"

    fun newId(): String = "list:${UUID.randomUUID()}"
}

/** The copy of [this] a list keeps. */
fun ReadableItem.toUserListEntry(
    position: Int,
    addedAtEpochMillis: Long,
): UserListEntry = UserListEntry(
    itemId = id,
    position = position,
    addedAtEpochMillis = addedAtEpochMillis,
    sourceType = sourceType,
    libraryId = libraryId,
    title = title,
    primaryText = libraryPrimaryText(),
    secondaryText = librarySecondaryText(),
    coverUrl = coverUrl,
    kind = kind,
    format = format,
    publishedYear = publishedYear,
)

/** The book as far as the list remembers it, for its cover and title once it has left the library. */
fun UserListEntry.asReadableItem(): ReadableItem = ReadableItem(
    id = itemId,
    sourceType = sourceType,
    libraryId = libraryId,
    title = title,
    format = format,
    kind = kind,
    coverUrl = coverUrl,
)

/** What the list page's editor changes, applied together with Done. */
data class UserListDraft(
    val listId: String,
    val name: String,
    val description: String,
    val order: UserListOrder,
    /** The books in the list's own order, without the removed ones. */
    val itemIds: List<String>,
    val removedItemIds: Set<String> = emptySet(),
    /** What the list is: a server's shelf or reading list saves its name, removals and order on the server with Done. */
    val kind: UserListKind = UserListKind.User,
) {
    /** A shelf or collection keeps no order on its server: the one chosen is kept here, not saved there. */
    val isServerShelf: Boolean get() = kind == UserListKind.ServerShelf || kind == UserListKind.ServerCollection

    val isOnServer: Boolean
        get() = kind == UserListKind.ServerShelf || kind == UserListKind.ServerReadingList || kind == UserListKind.ServerCollection
}

/** The editor's starting point: the books in the list's own order, or, for a shelf, which has none, as the page shows them. */
fun UserList.toDraft(): UserListDraft = UserListDraft(
    listId = id,
    name = name,
    description = description.orEmpty(),
    order = order,
    itemIds = (if (kind == UserListKind.ServerShelf || kind == UserListKind.ServerCollection) sortedEntries() else entries.sortedBy(UserListEntry::position))
        .map(UserListEntry::itemId),
    kind = kind,
)
