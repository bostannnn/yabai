package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaSeriesContainerId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.kavita.KavitaCollection
import com.example.unifiedreader.data.kavita.KavitaListsApi
import com.example.unifiedreader.feature.lists.RemoteShelf
import com.example.unifiedreader.feature.lists.RemoteShelfContents
import com.example.unifiedreader.feature.lists.ServerShelfClient
import com.example.unifiedreader.feature.lists.UserListKind
import javax.inject.Inject

/**
 * Kavita's collections for the lists feature, through the saved Kavita session: series kept
 * together, as the app's series ids. A refresh reads the collections only; their series are read
 * for one that changed or is being opened. Yours can be renamed, lose series and be deleted; one
 * someone else promoted, or one Kavita+ keeps in step with another site, is read-only.
 */
class KavitaCollectionClientAdapter @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
) : ServerShelfClient {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override val sourceType: SourceType = SourceType.KAVITA

    override val kind: UserListKind = UserListKind.ServerCollection

    override suspend fun fetchShelves(): List<RemoteShelfContents>? {
        val username = kavitaCredentialStore.sessionFlow.value?.username ?: return null
        return withLists { lists, token ->
            lists.getCollections(token).map { collection -> RemoteShelfContents(shelf = collection.toRemote(username), itemIds = null) }
        }
    }

    override suspend fun fetchItems(remoteId: String): List<String> = withLists { lists, token ->
        lists.getCollectionSeries(token, remoteId).map { series ->
            kavitaSeriesContainerId("${SourceType.KAVITA.remoteLibraryId(series.libraryId)}::${series.seriesId}")
        }
    }

    override suspend fun renameShelf(shelf: RemoteShelf, name: String): RemoteShelf = withLists { lists, token ->
        lists.renameCollection(token, current(lists, token, shelf.remoteId), name)
        shelf.copy(name = name)
    }

    override suspend fun deleteShelf(remoteId: String) = withLists { lists, token -> lists.deleteCollection(token, remoteId) }

    override suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean) {
        if (onShelf) error("Series go into a Kavita collection from Kavita.")
        // The app's ids for Kavita series are "kavita:series:<library>::<series id>".
        val seriesIds = itemIds.mapNotNull { itemId -> itemId.substringAfterLast("::", missingDelimiterValue = "").takeIf(String::isNotEmpty) }
        if (seriesIds.isEmpty()) return
        withLists { lists, token -> lists.removeSeriesFromCollection(token, current(lists, token, remoteId), seriesIds) }
    }

    override suspend fun createShelf(name: String): RemoteShelf = error("Collections are made in Kavita.")

    /** The collection as Kavita holds it now, since a change sends its summary, cover lock and promotion back. */
    private suspend fun current(lists: KavitaListsApi, token: String, remoteId: String): KavitaCollection =
        lists.getCollections(token).firstOrNull { it.id == remoteId } ?: error("Kavita has no such collection now.")

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

/** Without an owner name (an older Kavita) a promoted collection counts as someone else's. */
private fun KavitaCollection.toRemote(username: String?): RemoteShelf = RemoteShelf(
    remoteId = id,
    name = title,
    isPublic = promoted,
    isOwned = !isFromKavitaPlus && (ownerUserName?.equals(username, ignoreCase = true) ?: !promoted),
    itemCount = itemCount,
)
