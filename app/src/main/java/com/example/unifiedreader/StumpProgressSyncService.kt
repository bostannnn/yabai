package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
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

    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        if (!supports(item)) return
        val session = stumpCredentialStore.activeSessionFor(item) ?: return
        val mediaId = item.remoteId ?: return
        val api = stumpApiFactory.create(session.baseUrl)
        val progress = sessionRequestRunner.run(
            api = api,
            session = session,
        ) { accessToken ->
            api.getProgress(
                accessToken = accessToken,
                mediaId = mediaId,
                itemId = item.id,
                pageCount = item.pageCount,
            )
        }
        applyRemoteProgressSyncDecision(
            store = readerSessionStore,
            item = item,
            pageCount = item.pageCount,
            decision = decideRemoteProgressSync(
                local = readerSessionStore.getProgress(item),
                remote = progress,
                nowEpochMillis = nowEpochMillis(),
                missingRemoteClearsLocal = false,
                remoteClearOverridesLocal = false,
            ),
        )
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ) {
        val session = stumpCredentialStore.sessionFlow.value ?: return
        val stumpItems = items.forSource(SourceType.STUMP)
        if (stumpItems.isEmpty()) return
        val api = stumpApiFactory.create(session.baseUrl)
        val result = pushDirtyStumpProgress(
            api = api,
            initialAccessToken = session.accessToken,
            initialRefreshToken = session.refreshToken,
            items = stumpItems,
            readerSessionStore = readerSessionStore,
        )
        result.refreshedSession?.let { refreshedSession ->
            stumpCredentialStore.save(
                session.copy(
                    accessToken = refreshedSession.accessToken,
                    refreshToken = refreshedSession.refreshToken,
                ),
            )
        }
    }
}
