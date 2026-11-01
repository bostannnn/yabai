package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.data.kavita.KavitaListsApi
import com.example.unifiedreader.data.kavita.KavitaReadingList
import com.example.unifiedreader.feature.lists.RemoteShelf
import com.example.unifiedreader.feature.lists.RemoteShelfContents
import com.example.unifiedreader.feature.lists.ServerShelfClient
import javax.inject.Inject

/**
 * Kavita's reading lists for the lists feature, through the saved Kavita session. A refresh reads
 * the lists only; their issues are read for a list that changed or is being opened.
 */
class KavitaReadingListClientAdapter @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
) : ServerShelfClient {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override val sourceType: SourceType = SourceType.KAVITA

    override val isOrdered: Boolean = true

    override suspend fun fetchShelves(): List<RemoteShelfContents>? {
        val username = kavitaCredentialStore.sessionFlow.value?.username ?: return null
        return withLists { lists, token ->
            lists.getReadingLists(token).map { list -> RemoteShelfContents(shelf = list.toRemote(username), itemIds = null) }
        }
    }

    override suspend fun fetchItems(remoteId: String): List<String> = withLists { lists, token ->
        lists.getReadingListEntries(token, remoteId).map { entry -> sourceType.remoteItemId(entry.chapterId) }
    }

    override suspend fun createShelf(name: String): RemoteShelf = withLists { lists, token ->
        lists.createReadingList(token, name).toRemote(username = null).copy(isOwned = true)
    }

    override suspend fun renameShelf(shelf: RemoteShelf, name: String): RemoteShelf = withLists { lists, token ->
        lists.renameReadingList(token, shelf.remoteId, name)
        shelf.copy(name = name)
    }

    override suspend fun deleteShelf(remoteId: String) = withLists { lists, token -> lists.deleteReadingList(token, remoteId) }

    override suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean) {
        val chapterIds = chapterIdsOf(itemIds)
        if (chapterIds.isEmpty()) return
        withLists { lists, token ->
            if (onShelf) {
                chapterIds.forEach { chapterId -> lists.addChapterToReadingList(token, remoteId, chapterId) }
            } else {
                // Kavita removes by its own id for the issue in the list.
                val entries = lists.getReadingListEntries(token, remoteId)
                chapterIds.forEach { chapterId ->
                    entries.firstOrNull { it.chapterId == chapterId }?.let { entry -> lists.removeReadingListEntry(token, remoteId, entry.id) }
                }
            }
        }
    }

    override suspend fun move(remoteId: String, itemId: String, fromPosition: Int, toPosition: Int) {
        val chapterId = chapterIdsOf(listOf(itemId)).singleOrNull() ?: return
        withLists { lists, token ->
            val entries = lists.getReadingListEntries(token, remoteId)
            val from = entries.indexOfFirst { it.chapterId == chapterId }.takeIf { it >= 0 } ?: return@withLists
            lists.moveReadingListEntry(token, remoteId, entries[from].id, fromPosition = from, toPosition = toPosition)
        }
    }

    /** The chapter ids behind the app's ids for Kavita issues ("kavita:item:<chapter id>"). */
    private fun chapterIdsOf(itemIds: List<String>): List<String> {
        val prefix = sourceType.remoteItemId("")
        return itemIds.mapNotNull { itemId -> itemId.removePrefix(prefix).takeIf { itemId.startsWith(prefix) } }
    }

    private suspend fun <T> withLists(block: suspend (lists: KavitaListsApi, accessToken: String) -> T): T {
        val session = kavitaCredentialStore.sessionFlow.value ?: error("Kavita is not connected.")
        return runWithStoredSessionAddressFailover(
            sessionStore = kavitaCredentialStore,
            session = session,
            createApi = kavitaApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            sessionRequestRunner.run(api = api, session = activeSession) { accessToken, _ ->
                block(api.lists, accessToken)
            }
        }
    }
}

/** Someone else's promoted list is read-only; without an owner name (an older Kavita) a promoted list counts as theirs. */
private fun KavitaReadingList.toRemote(username: String?): RemoteShelf = RemoteShelf(
    remoteId = id,
    name = title,
    isPublic = promoted,
    isOwned = ownerUserName?.equals(username, ignoreCase = true) ?: !promoted,
    itemCount = itemCount,
    startYear = startingYear,
    endYear = endingYear,
)
