package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.pushDirtyGrimmoryJournalEntries
import com.example.unifiedreader.data.repository.SourceRemoteJournalPusher
import com.example.unifiedreader.journal.JournalRepository
import javax.inject.Inject

class GrimmoryRemoteJournalPusher @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val journalRepository: JournalRepository,
) : SourceRemoteJournalPusher {
    override val sourceType: SourceType = SourceType.GRIMMORY

    private val grimmorySessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override suspend fun pushPendingJournalEntries(
        items: List<ReadableItem>,
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
