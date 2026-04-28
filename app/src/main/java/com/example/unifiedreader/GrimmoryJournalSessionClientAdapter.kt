package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.data.grimmory.GrimmoryJournalSessionClient
import com.example.unifiedreader.data.grimmory.pushDirtyGrimmoryJournalEntries
import com.example.unifiedreader.journal.JournalRepository
import javax.inject.Inject

class GrimmoryJournalSessionClientAdapter @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) : GrimmoryJournalSessionClient {
    private val grimmorySessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override suspend fun pushDirtyJournalEntries(
        items: List<ReadableItem>,
        journalRepository: JournalRepository,
    ) {
        val session = grimmoryCredentialStore.sessionFlow.value ?: return
        val grimmoryItems = items.forActiveGrimmoryServer(session)
        if (grimmoryItems.isEmpty()) return
        runWithStoredSessionAddressFailover(
            sessionStore = grimmoryCredentialStore,
            session = session,
            createApi = grimmoryApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            grimmorySessionRequestRunner.run(
                api = api,
                session = activeSession,
            ) { accessToken ->
                pushDirtyGrimmoryJournalEntries(
                    api = api,
                    accessToken = accessToken,
                    entries = grimmoryItems,
                    journalRepository = journalRepository,
                )
            }
        }
    }
}
