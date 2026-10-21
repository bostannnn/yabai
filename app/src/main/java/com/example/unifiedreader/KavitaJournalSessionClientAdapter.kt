package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.data.kavita.KavitaJournalSessionClient
import com.example.unifiedreader.data.kavita.pushDirtyKavitaJournalEntries
import com.example.unifiedreader.journal.JournalRepository
import javax.inject.Inject

class KavitaJournalSessionClientAdapter @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
) : KavitaJournalSessionClient {
    private val kavitaSessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override suspend fun pushDirtyJournalEntries(
        items: List<ReadableItem>,
        journalRepository: JournalRepository,
        pushJournalEntries: Boolean,
    ): List<Throwable> {
        val session = kavitaCredentialStore.sessionFlow.value ?: return emptyList()
        return runWithStoredSessionAddressFailover(
            sessionStore = kavitaCredentialStore,
            session = session,
            createApi = kavitaApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            kavitaSessionRequestRunner.run(
                api = api,
                session = activeSession,
            ) { accessToken, _ ->
                pushDirtyKavitaJournalEntries(
                    api = api,
                    accessToken = accessToken,
                    entries = items,
                    journalRepository = journalRepository,
                    itemsAreWholeCatalog = false,
                    pushJournalEntries = pushJournalEntries,
                )
            }
        }
    }
}
