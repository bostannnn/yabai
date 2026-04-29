package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject

class StumpProgressSyncService @Inject constructor(
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
    private val readerSessionStore: ReaderSessionStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : SourceProgressSync {
    override val sourceType: SourceType = SourceType.STUMP

    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)
    private val progressSyncStore: ProgressSyncStore = ReaderSessionProgressStoreAdapter(readerSessionStore)

    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        if (!supports(item)) return
        val session = stumpCredentialStore.activeSessionFor(item) ?: return
        val mediaId = item.remoteId ?: return
        val progress = runWithStoredSessionAddressFailover(
            sessionStore = stumpCredentialStore,
            session = session,
            createApi = stumpApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            sessionRequestRunner.run(
                api = api,
                session = activeSession,
            ) { accessToken ->
                api.getProgress(
                    accessToken = accessToken,
                    mediaId = mediaId,
                    itemId = item.id,
                    pageCount = item.pageCount,
                )
            }
        }
        applyRemoteProgressSyncDecision(
            store = progressSyncStore,
            item = item,
            pageCount = item.pageCount,
            decision = decideRemoteProgressSync(
                local = progressSyncStore.getProgress(item),
                remote = progress,
                nowEpochMillis = nowEpochMillis(),
                missingRemoteClearsLocal = true,
                remoteClearOverridesLocal = false,
            ),
        )
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        val session = stumpCredentialStore.sessionFlow.value ?: return emptyList()
        val stumpItems = items.forSource(SourceType.STUMP)
        if (stumpItems.isEmpty()) return emptyList()
        return runWithStoredSessionAddressFailover(
            sessionStore = stumpCredentialStore,
            session = session,
            createApi = stumpApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            val result = pushDirtyStumpProgress(
                api = api,
                initialAccessToken = activeSession.accessToken,
                initialRefreshToken = activeSession.refreshToken,
                items = stumpItems,
                readerSessionStore = readerSessionStore,
                diagnostics = LogcatStumpProgressSyncDiagnostics,
            )
            result.refreshedSession?.let { refreshedSession ->
                stumpCredentialStore.save(
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
                    blocksDependentArtifacts = throwable.blocksDependentRemoteArtifacts(),
                )
            }
        }
    }
}

private fun Throwable.blocksDependentRemoteArtifacts(): Boolean =
    isRemoteServerAddressRetryableFailure() || this is com.example.unifiedreader.data.stump.StumpUnauthorizedException
