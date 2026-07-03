package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface RemoteItemLocalStateCache {
    fun load(
        items: List<ReadableItem>,
    ): RemoteItemLocalState?

    fun store(
        items: List<ReadableItem>,
        state: RemoteItemLocalState,
    )
}

object EmptyRemoteItemLocalStateCache : RemoteItemLocalStateCache {
    override fun load(
        items: List<ReadableItem>,
    ): RemoteItemLocalState? = null

    override fun store(
        items: List<ReadableItem>,
        state: RemoteItemLocalState,
    ) = Unit
}

class InMemoryRemoteItemLocalStateCache : RemoteItemLocalStateCache {
    private val entriesByScopeKey = mutableMapOf<String, RemoteItemLocalStateCacheEntry>()

    override fun load(
        items: List<ReadableItem>,
    ): RemoteItemLocalState? {
        return items.remoteItemLocalStateCacheScopes()
            .mapNotNull { scope ->
                entriesByScopeKey[scope.key()]
                    ?.takeIf { entry -> entry.scope.matchesSourceLibraryScope(scope) }
                    ?.state
                    ?.scopedTo(scope)
            }
            .mergeOrNull()
    }

    override fun store(
        items: List<ReadableItem>,
        state: RemoteItemLocalState,
    ) {
        items.remoteItemLocalStateCacheScopes().forEach { scope ->
            entriesByScopeKey[scope.key()] = RemoteItemLocalStateCacheEntry(
                scope = scope,
                state = state.scopedTo(scope),
            )
        }
    }
}

class SharedPreferencesRemoteItemLocalStateCache @Inject constructor(
    @ApplicationContext context: Context,
) : RemoteItemLocalStateCache {
    private val preferences = context.applicationContext.getSharedPreferences(
        REMOTE_ITEM_LOCAL_STATE_CACHE_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(
        items: List<ReadableItem>,
    ): RemoteItemLocalState? {
        return items.remoteItemLocalStateCacheScopes()
            .mapNotNull { scope ->
                val encoded = preferences.getString(scope.preferenceKey(), null) ?: return@mapNotNull null
                try {
                    val entry = JSONObject(encoded).toRemoteItemLocalStateCacheEntry()
                    entry.takeIf { it.scope.matchesSourceLibraryScope(scope) }
                        ?.state
                        ?.scopedTo(scope)
                } catch (_: JSONException) {
                    null
                }
            }
            .mergeOrNull()
    }

    override fun store(
        items: List<ReadableItem>,
        state: RemoteItemLocalState,
    ) {
        val scopes = items.remoteItemLocalStateCacheScopes()
        if (scopes.isEmpty()) return
        val editor = preferences.edit()
        scopes.forEach { scope ->
            val entry = RemoteItemLocalStateCacheEntry(
                scope = scope,
                state = state.scopedTo(scope),
            )
            editor.putString(scope.preferenceKey(), entry.toJson().toString())
        }
        editor.commit()
    }
}

private data class RemoteItemLocalStateCacheEntry(
    val scope: RemoteItemLocalStateCacheScope,
    val state: RemoteItemLocalState,
)

internal data class RemoteItemLocalStateCacheScope(
    val sourceLibraryScopeId: String,
    val itemIds: Set<String>,
    val availabilityScopeIds: Set<String>,
)

internal fun List<ReadableItem>.remoteItemLocalStateCacheScopes(): List<RemoteItemLocalStateCacheScope> =
    filter { item -> item.sourceType != SourceType.LOCAL }
        .groupBy(ReadableItem::remoteItemLocalStateSourceLibraryScopeId)
        .entries
        .sortedBy(Map.Entry<String, List<ReadableItem>>::key)
        .map { (sourceLibraryScopeId, items) ->
            RemoteItemLocalStateCacheScope(
                sourceLibraryScopeId = sourceLibraryScopeId,
                itemIds = items.remoteItemLocalStateScopeItemIds(),
                availabilityScopeIds = items.remoteItemLocalAvailabilityScopeIds(),
            )
        }

private fun RemoteItemLocalState.scopedTo(
    scope: RemoteItemLocalStateCacheScope,
): RemoteItemLocalState = RemoteItemLocalState(
    downloadedItemIds = downloadedItemIds.filterTo(linkedSetOf()) { itemId ->
        itemId in scope.itemIds
    },
    locallyAvailableItemIds = locallyAvailableItemIds.filterTo(linkedSetOf()) { itemId ->
        itemId in scope.availabilityScopeIds
    },
    knownSourceLibraryScopeIds = setOf(scope.sourceLibraryScopeId),
)

private fun RemoteItemLocalStateCacheScope.matchesSourceLibraryScope(
    other: RemoteItemLocalStateCacheScope,
): Boolean = sourceLibraryScopeId == other.sourceLibraryScopeId

private fun RemoteItemLocalStateCacheScope.preferenceKey(): String =
    "$REMOTE_ITEM_LOCAL_STATE_CACHE_ENTRY_PREFIX${key()}"

private fun RemoteItemLocalStateCacheScope.key(): String = sha256(
    sourceLibraryScopeId,
)

private fun RemoteItemLocalStateCacheEntry.toJson(): JSONObject = JSONObject()
    .put("version", REMOTE_ITEM_LOCAL_STATE_CACHE_VERSION)
    .put("sourceLibraryScopeId", scope.sourceLibraryScopeId)
    .put("itemIds", scope.itemIds.toJsonArray())
    .put("availabilityScopeIds", scope.availabilityScopeIds.toJsonArray())
    .put("downloadedItemIds", state.downloadedItemIds.toJsonArray())
    .put("locallyAvailableItemIds", state.locallyAvailableItemIds.toJsonArray())

private fun JSONObject.toRemoteItemLocalStateCacheEntry(): RemoteItemLocalStateCacheEntry {
    val version = optInt("version")
    if (version != REMOTE_ITEM_LOCAL_STATE_CACHE_VERSION && version != 1) {
        throw JSONException("Unsupported remote item local state cache version.")
    }
    val scope = RemoteItemLocalStateCacheScope(
        sourceLibraryScopeId = when (version) {
            1 -> getJSONArray("sourceLibraryScopeIds").toStringSet().singleSourceLibraryScopeId()
            else -> getString("sourceLibraryScopeId")
        },
        itemIds = getJSONArray("itemIds").toStringSet(),
        availabilityScopeIds = getJSONArray("availabilityScopeIds").toStringSet(),
    )
    return RemoteItemLocalStateCacheEntry(
        scope = scope,
        state = RemoteItemLocalState(
            downloadedItemIds = getJSONArray("downloadedItemIds").toStringSet(),
            locallyAvailableItemIds = getJSONArray("locallyAvailableItemIds").toStringSet(),
            knownSourceLibraryScopeIds = setOf(scope.sourceLibraryScopeId),
        ),
    )
}

private fun Set<String>.singleSourceLibraryScopeId(): String =
    singleOrNull() ?: throw JSONException("Expected exactly one source library scope.")

private fun List<RemoteItemLocalState>.mergeOrNull(): RemoteItemLocalState? {
    if (isEmpty()) return null
    return RemoteItemLocalState(
        downloadedItemIds = flatMapTo(linkedSetOf()) { state -> state.downloadedItemIds },
        locallyAvailableItemIds = flatMapTo(linkedSetOf()) { state -> state.locallyAvailableItemIds },
        knownSourceLibraryScopeIds = flatMapTo(linkedSetOf()) { state -> state.knownSourceLibraryScopeIds },
    )
}

private fun Set<String>.toJsonArray(): JSONArray = JSONArray().also { array ->
    sorted().forEach(array::put)
}

private fun JSONArray.toStringSet(): Set<String> = buildSet {
    for (index in 0 until length()) {
        add(getString(index))
    }
}

private fun sha256(
    value: String,
): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

private const val REMOTE_ITEM_LOCAL_STATE_CACHE_PREFERENCES_NAME =
    "remote_item_local_state_cache"
private const val REMOTE_ITEM_LOCAL_STATE_CACHE_ENTRY_PREFIX = "scope:"
private const val REMOTE_ITEM_LOCAL_STATE_CACHE_VERSION = 2
