package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject

class GrimmoryProgressSyncService @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val readerSessionStore: ReaderSessionStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : SourceProgressSync {
    override val sourceType: SourceType = SourceType.GRIMMORY

    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)
    private val progressSyncStore: ProgressSyncStore = ReaderSessionProgressStoreAdapter(readerSessionStore)

    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        if (!supports(item)) return
        val session = grimmoryCredentialStore.activeSessionFor(item) ?: return
        val remoteItemId = item.remoteId ?: return
        val entry = runWithStoredSessionAddressFailover(
            sessionStore = grimmoryCredentialStore,
            session = session,
            createApi = grimmoryApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            sessionRequestRunner.run(
                api = api,
                session = activeSession,
            ) { accessToken ->
                api.getBookEntry(
                    accessToken = accessToken,
                    bookId = remoteItemId,
                )
            }
        } ?: return
        applyRemoteProgressSyncDecision(
            store = progressSyncStore,
            item = entry.item,
            pageCount = entry.item.pageCount,
            decision = decideRemoteProgressSync(
                local = progressSyncStore.getProgress(entry.item),
                remote = entry.progress,
                nowEpochMillis = nowEpochMillis(),
                missingRemoteClearsLocal = entry.isExplicitUnreadReset,
                remoteClearOverridesLocal = false,
            ),
        )
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        val session = grimmoryCredentialStore.sessionFlow.value ?: return emptyList()
        val grimmoryItems = items.forActiveGrimmoryServer(session)
        if (grimmoryItems.isEmpty()) return emptyList()
        return runWithStoredSessionAddressFailover(
            sessionStore = grimmoryCredentialStore,
            session = session,
            createApi = grimmoryApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            val result = pushDirtyGrimmoryProgress(
                api = api,
                initialAccessToken = activeSession.accessToken,
                initialRefreshToken = activeSession.refreshToken,
                items = grimmoryItems,
                readerSessionStore = readerSessionStore,
            )
            result.refreshedSession?.let { refreshedSession ->
                grimmoryCredentialStore.save(
                    activeSession.copy(
                        accessToken = refreshedSession.accessToken,
                        refreshToken = refreshedSession.refreshToken,
                    ),
                )
            }
            result.value.map { throwable ->
                SourceProgressSyncFailure(
                    sourceType = sourceType,
                    throwable = throwable,
                )
            }
        }
    }
}
