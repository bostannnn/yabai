package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.kavitaCatalogSeriesKey
import com.example.unifiedreader.data.kavita.KavitaListsApi
import com.example.unifiedreader.feature.lists.ServerWantToRead
import javax.inject.Inject

/** Kavita's Want to read for the lists feature, through the saved Kavita session. */
class KavitaWantToReadAdapter @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
) : ServerWantToRead {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override val sourceType: SourceType = SourceType.KAVITA

    // An issue's series id is the last part of its catalog series key, as for its description.
    override fun seriesIdOf(item: ReadableItem): String? =
        item.kavitaCatalogSeriesKey()?.substringAfterLast("::")?.takeIf(String::isNotBlank)

    override suspend fun add(seriesIds: Set<String>) = withLists { lists, token -> lists.addToWantToRead(token, seriesIds) }

    override suspend fun remove(seriesIds: Set<String>) = withLists { lists, token -> lists.removeFromWantToRead(token, seriesIds) }

    private suspend fun withLists(block: suspend (lists: KavitaListsApi, accessToken: String) -> Unit) {
        val session = kavitaCredentialStore.sessionFlow.value ?: return
        runWithStoredSessionAddressFailover(
            sessionStore = kavitaCredentialStore,
            session = session,
            createApi = kavitaApiFactory::create,
        ) { api, activeSession ->
            sessionRequestRunner.run(api = api, session = activeSession) { accessToken, _ ->
                block(api.lists, accessToken)
            }
        }
    }
}
