package com.example.unifiedreader

import com.example.unifiedreader.core.model.ListRuleField
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.encode
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryMagicShelfDto
import com.example.unifiedreader.data.grimmory.grimmoryFilterJsonToListRules
import com.example.unifiedreader.data.grimmory.toGrimmoryFilterJson
import com.example.unifiedreader.feature.lists.RemoteShelf
import com.example.unifiedreader.feature.lists.RemoteShelfContents
import com.example.unifiedreader.feature.lists.ServerShelfClient
import com.example.unifiedreader.feature.lists.UserListKind
import javax.inject.Inject

/**
 * Grimmory's magic shelves for the lists feature, through the saved Grimmory session: the rules
 * are Grimmory's, read into the app's and written back, and Grimmory picks the books. A shelf rule
 * names a plain shelf, which Grimmory knows by id, so the shelves are read when a rule needs them.
 * Grimmory says nothing of who made a public magic shelf, so every one counts as the user's; a
 * change to someone else's is refused by the server and says so in a toast.
 */
class GrimmoryMagicShelfClientAdapter @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) : ServerShelfClient {
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override val sourceType: SourceType = SourceType.GRIMMORY

    override val kind: UserListKind = UserListKind.ServerMagicShelf

    override suspend fun fetchShelves(): List<RemoteShelfContents>? {
        if (grimmoryCredentialStore.sessionFlow.value == null) return null
        return withGrimmory { api, token ->
            val all = api.magicShelves.getMagicShelves(token)
            val shelfNames = if (all.any { ShelfFieldMarker in it.filterJson }) shelfNamesById(api, token) else emptyMap()
            all.mapNotNull { shelf ->
                val id = shelf.id ?: return@mapNotNull null
                RemoteShelfContents(
                    shelf = shelf.toRemote(rules = grimmoryFilterJsonToListRules(shelf.filterJson, shelfNames::get)),
                    itemIds = api.magicShelves.getMagicShelfBookIds(token, id).map { bookId -> sourceType.remoteItemId(bookId.toString()) },
                )
            }
        }
    }

    override suspend fun fetchItems(remoteId: String): List<String> = withGrimmory { api, token ->
        api.magicShelves.getMagicShelfBookIds(token, remoteId.toLong()).map { bookId -> sourceType.remoteItemId(bookId.toString()) }
    }

    override suspend fun createRuled(name: String, rules: ListRules): RemoteShelf = withGrimmory { api, token ->
        val saved = api.magicShelves.saveMagicShelf(token, GrimmoryMagicShelfDto(name = name, filterJson = filterJson(api, token, rules)))
        saved.toRemote(rules)
    }

    override suspend fun updateRuled(shelf: RemoteShelf, name: String, rules: ListRules): RemoteShelf = withGrimmory { api, token ->
        // An update replaces the whole shelf, so its icon and whether it is public go back as they were.
        val saved = api.magicShelves.saveMagicShelf(token, shelf.toDto(name = name, filterJson = filterJson(api, token, rules)))
        saved.toRemote(rules)
    }

    override suspend fun renameShelf(shelf: RemoteShelf, name: String): RemoteShelf = withGrimmory { api, token ->
        val current = api.magicShelves.getMagicShelves(token).firstOrNull { it.id?.toString() == shelf.remoteId }
            ?: error("Grimmory has no magic shelf “${shelf.name}” now.")
        api.magicShelves.saveMagicShelf(token, current.copy(name = name)).toRemote(rules = null).copy(rules = shelf.rules)
    }

    override suspend fun deleteShelf(remoteId: String) = withGrimmory { api, token ->
        api.magicShelves.deleteMagicShelf(token, remoteId.toLong())
    }

    override suspend fun createShelf(name: String): RemoteShelf = error("A magic shelf is made from rules.")

    override suspend fun setOnShelf(remoteId: String, itemIds: List<String>, onShelf: Boolean) =
        error("A magic shelf picks its own books.")

    /** The rules as Grimmory's JSON; one it can't hold is refused here, with what the toast should say. */
    private suspend fun filterJson(api: GrimmoryApi, token: String, rules: ListRules): String {
        val ids = if (rules.allRules.any { it.field == ListRuleField.Shelf }) {
            shelfNamesById(api, token).entries.associate { (id, name) -> name.lowercase() to id }
        } else {
            emptyMap()
        }
        return try {
            rules.toGrimmoryFilterJson { name -> ids[name.lowercase()] }
        } catch (refused: IllegalArgumentException) {
            throw IllegalStateException(refused.message, refused)
        }
    }

    private suspend fun shelfNamesById(api: GrimmoryApi, token: String): Map<String, String> =
        api.shelves.getShelves(token).associate { shelf -> shelf.id to shelf.name }

    private suspend fun <T> withGrimmory(block: suspend (api: GrimmoryApi, accessToken: String) -> T): T {
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
                block(api, accessToken)
            }
        }
    }
}

private const val ShelfFieldMarker = "\"shelf\""

/** [rules] null where the app can't read Grimmory's: the shelf still shows, its rules changed only in Grimmory's web app. */
private fun GrimmoryMagicShelfDto.toRemote(rules: ListRules?): RemoteShelf = RemoteShelf(
    remoteId = id?.toString().orEmpty(),
    name = name,
    icon = icon,
    iconType = iconType,
    isPublic = isPublic,
    isOwned = true,
    rules = rules?.encode(),
)

private fun RemoteShelf.toDto(name: String, filterJson: String): GrimmoryMagicShelfDto = GrimmoryMagicShelfDto(
    id = remoteId.toLong(),
    name = name,
    icon = icon,
    iconType = iconType,
    filterJson = filterJson,
    isPublic = isPublic,
)
