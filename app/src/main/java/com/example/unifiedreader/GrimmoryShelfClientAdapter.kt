package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.data.grimmory.GrimmoryShelf
import com.example.unifiedreader.data.grimmory.GrimmoryShelvesApi
import com.example.unifiedreader.feature.lists.RemoteShelf
import com.example.unifiedreader.feature.lists.RemoteShelfContents
import com.example.unifiedreader.feature.lists.ServerShelfClient
import javax.inject.Inject

/** Grimmory's plain shelves for the lists feature, through the saved Grimmory session. */
class GrimmoryShelfClientAdapter @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) : ServerShelfClient {
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override val sourceType: SourceType = SourceType.GRIMMORY

    override suspend fun fetchShelves(): List<RemoteShelfContents>? {
        if (grimmoryCredentialStore.sessionFlow.value == null) return null
        return withShelves { shelves, token ->
            val all = shelves.getShelves(token)
            // Only a public shelf can be someone else's; ask who this is only when there is one.
            val userId = if (all.any(GrimmoryShelf::isPublic)) shelves.getCurrentUserId(token) else null
            all.map { shelf ->
                RemoteShelfContents(
                    shelf = shelf.toRemote(isOwned = !shelf.isPublic || (userId != null && shelf.ownerUserId == userId)),
                    itemIds = shelves.getShelfBookIds(token, shelf.id).map { bookId -> sourceType.remoteItemId(bookId) },
                )
            }
        }
    }

    override suspend fun createShelf(name: String): RemoteShelf = withShelves { shelves, token ->
        shelves.createShelf(token, name).toRemote(isOwned = true)
    }

    override suspend fun renameShelf(shelf: RemoteShelf, name: String): RemoteShelf = withShelves { shelves, token ->
        shelves.renameShelf(token, shelf.toGrimmory(), name).toRemote(isOwned = shelf.isOwned)
    }

    override suspend fun deleteShelf(remoteId: String) = withShelves { shelves, token ->
        shelves.deleteShelf(token, remoteId)
    }

    override suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean) {
        // The app's ids for Grimmory books are "grimmory:item:<book id>", as fetchShelves makes them.
        val itemPrefix = sourceType.remoteItemId("")
        val bookIds = itemIds.mapNotNull { itemId -> itemId.removePrefix(itemPrefix).takeIf { itemId.startsWith(itemPrefix) } }
        if (bookIds.isEmpty()) return
        withShelves { shelves, token ->
            shelves.setBookShelves(
                accessToken = token,
                bookIds = bookIds,
                assignShelfIds = if (onShelf) listOf(remoteId) else emptyList(),
                unassignShelfIds = if (onShelf) emptyList() else listOf(remoteId),
            )
        }
    }

    private suspend fun <T> withShelves(block: suspend (shelves: GrimmoryShelvesApi, accessToken: String) -> T): T {
        val session = grimmoryCredentialStore.sessionFlow.value ?: error("Grimmory is not connected.")
        return runWithStoredSessionAddressFailover(
            sessionStore = grimmoryCredentialStore,
            session = session,
            createApi = grimmoryApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            sessionRequestRunner.run(api = api, session = activeSession) { accessToken ->
                block(api.shelves, accessToken)
            }
        }
    }
}

private fun GrimmoryShelf.toRemote(isOwned: Boolean): RemoteShelf = RemoteShelf(
    remoteId = id,
    name = name,
    icon = icon,
    iconType = iconType,
    isPublic = isPublic,
    isOwned = isOwned,
    sortField = sortField,
    itemCount = bookCount,
)

private fun RemoteShelf.toGrimmory(): GrimmoryShelf = GrimmoryShelf(
    id = remoteId,
    name = name,
    icon = icon,
    iconType = iconType,
    sortField = sortField,
    isPublic = isPublic,
    bookCount = itemCount,
)
