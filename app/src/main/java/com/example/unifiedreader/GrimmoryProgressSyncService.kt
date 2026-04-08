package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
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

    override suspend fun syncItem(
        item: ReadableItem,
    ) {
        if (!supports(item)) return
        val session = grimmoryCredentialStore.activeSessionFor(item) ?: return
        val remoteItemId = item.remoteId ?: return
        val api = grimmoryApiFactory.create(
            baseUrl = session.baseUrl,
        )
        val entry = sessionRequestRunner.run(
            api = api,
            session = session,
        ) { accessToken ->
            api.getBookEntry(
                accessToken = accessToken,
                bookId = remoteItemId,
            )
        } ?: return
        applyRemoteProgressSyncDecision(
            store = readerSessionStore,
            item = entry.item,
            pageCount = entry.item.pageCount,
            decision = decideRemoteProgressSync(
                local = readerSessionStore.getProgress(entry.item),
                remote = entry.progress,
                nowEpochMillis = nowEpochMillis(),
                missingRemoteClearsLocal = entry.isExplicitUnreadReset,
            ),
        )
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ) {
        val session = grimmoryCredentialStore.sessionFlow.value ?: return
        val grimmoryItems = items.forActiveGrimmoryServer(session)
        if (grimmoryItems.isEmpty()) return
        val api = grimmoryApiFactory.create(
            baseUrl = session.baseUrl,
        )
        val result = pushDirtyGrimmoryProgress(
            api = api,
            initialAccessToken = session.accessToken,
            initialRefreshToken = session.refreshToken,
            items = grimmoryItems,
            readerSessionStore = readerSessionStore,
        )
        result.refreshedSession?.let { refreshedSession ->
            grimmoryCredentialStore.save(
                session.copy(
                    accessToken = refreshedSession.accessToken,
                    refreshToken = refreshedSession.refreshToken,
                ),
            )
        }
    }
}
