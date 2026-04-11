package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.kavita.toReadingProgress
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject

class KavitaProgressSyncService @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val readerSessionStore: ReaderSessionStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : SourceProgressSync {
    override val sourceType: SourceType = SourceType.KAVITA

    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        if (!supports(item)) return
        val session = kavitaCredentialStore.activeSessionFor(item) ?: return
        val chapterId = item.remoteId ?: return
        val progressDto = runWithStoredSessionAddressFailover(
            sessionStore = kavitaCredentialStore,
            session = session,
            createApi = kavitaApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            sessionRequestRunner.run(
                api = api,
                session = activeSession,
            ) { accessToken, _ ->
                api.getProgress(
                    accessToken = accessToken,
                    chapterId = chapterId,
                )
            }
        }
        applyRemoteProgressSyncDecision(
            store = readerSessionStore,
            item = item,
            pageCount = item.pageCount,
            decision = decideRemoteProgressSync(
                local = readerSessionStore.getProgress(item),
                remote = progressDto.toReadingProgress(
                    itemId = item.id,
                    pageCount = item.pageCount,
                ),
                nowEpochMillis = nowEpochMillis(),
                missingRemoteClearsLocal = true,
                remoteClearOverridesLocal = false,
            ),
        )
    }

    override suspend fun pushPendingProgress(
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
            val result = pushDirtyKavitaProgress(
                api = api,
                initialAccessToken = activeSession.accessToken,
                initialRefreshToken = activeSession.refreshToken,
                initialApiKey = activeSession.apiKey,
                items = kavitaItems,
                readerSessionStore = readerSessionStore,
            )
            result.refreshedSession?.let { refreshedSession ->
                kavitaCredentialStore.save(
                    activeSession.copy(
                        accessToken = refreshedSession.accessToken,
                        refreshToken = refreshedSession.refreshToken,
                        apiKey = refreshedSession.apiKey ?: activeSession.apiKey,
                    ),
                )
            }
        }
    }
}
