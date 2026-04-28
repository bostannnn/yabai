package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.pushDirtyKavitaJournalEntries
import com.example.unifiedreader.data.repository.SourceRemoteJournalPusher
import com.example.unifiedreader.journal.JournalRepository
import javax.inject.Inject

class KavitaRemoteJournalPusher @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val journalRepository: JournalRepository,
) : SourceRemoteJournalPusher {
    override val sourceType: SourceType = SourceType.KAVITA

    private val kavitaSessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override suspend fun pushPendingJournalEntries(
        items: List<ReadableItem>,
    ) {
        val session = kavitaCredentialStore.sessionFlow.value ?: return
        val kavitaItems = items.forSource(SourceType.KAVITA)
        if (kavitaItems.isEmpty()) return
        runWithStoredSessionAddressFailover(
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
                    entries = kavitaItems,
                    journalRepository = journalRepository,
                )
            }
        }
    }
}
