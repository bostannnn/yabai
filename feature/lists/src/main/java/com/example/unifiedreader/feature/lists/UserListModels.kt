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
}

/**
 * How a list sorts its books. Only [Custom] numbers them, and only a list in [Custom] order is a
 * reading order that Read next follows.
 */
enum class UserListOrder(val label: String) {
    Custom("Your order"),
    DateAdded("Date added"),
    Title("Title"),
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
) {
    val isReadingOrder: Boolean
        get() = order == UserListOrder.Custom

    /**
     * Stalled is worked out from your reading, and someone else's public shelf is theirs: nothing
     * is added to either or changed in them.
     */
    val isEditable: Boolean
        get() = kind != UserListKind.Stalled && server?.isOwned != false

    /** The orders the list can take. A server's shelf keeps no order of its own, so it has no Your order. */
    val orders: List<UserListOrder>
        get() = if (kind == UserListKind.ServerShelf) ShelfOrders else UserListOrder.entries

    fun contains(itemId: String): Boolean = entries.any { entry -> entry.itemId == itemId }
}

/** A shelf's place on its server: which source, its id there, and whether this user made it. */
data class ServerListRef(
    val sourceType: SourceType,
    val remoteId: String,
    val isOwned: Boolean,
)

private val ShelfOrders = listOf(UserListOrder.DateAdded, UserListOrder.Title)

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
    /** A server's shelf: Done saves its name and removals on the server, and it has no description or order of its own. */
    val isServerShelf: Boolean = false,
)

/** The editor's starting point: the books in the list's own order, or, for a shelf, which has none, as the page shows them. */
fun UserList.toDraft(): UserListDraft = UserListDraft(
    listId = id,
    name = name,
    description = description.orEmpty(),
    order = order,
    itemIds = (if (kind == UserListKind.ServerShelf) sortedEntries() else entries.sortedBy(UserListEntry::position))
        .map(UserListEntry::itemId),
    isServerShelf = kind == UserListKind.ServerShelf,
)
