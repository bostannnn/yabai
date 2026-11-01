package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.progress.ProgressSyncStore
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
    private val progressSyncStore: ProgressSyncStore = ReaderSessionProgressStoreAdapter(readerSessionStore)

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
            store = progressSyncStore,
            item = item,
            pageCount = item.pageCount,
            decision = decideRemoteProgressSync(
                local = progressSyncStore.getProgress(item),
                remote = progressDto.toReadingProgress(
                    itemId = item.id,
                    pageCount = item.pageCount,
                ),
                nowEpochMillis = nowEpochMillis(),
                missingRemoteClearsLocal = true,
                remoteClearOverridesLocal = false,
                localRefinesRemote = ::kavitaLocalRefinesRemote,
            ),
        )
    }

    override suspend fun pushPendingProgress(
        items: List<ReadableItem>,
    ): List<SourceProgressSyncFailure> {
        val session = kavitaCredentialStore.sessionFlow.value ?: return emptyList()
        val kavitaItems = items.forSource(SourceType.KAVITA)
        if (kavitaItems.isEmpty()) return emptyList()
        return runWithStoredSessionAddressFailover(
            sessionStore = kavitaCredentialStore,
            session = session,
            createApi = kavitaApiFactory::create,
            withActiveBaseUrl = { currentSession, activeBaseUrl ->
                currentSession.copy(activeBaseUrl = activeBaseUrl)
            },
        ) { api, activeSession ->
            val tokens = KavitaRunTokens(
                api = api,
                initial = activeSession.toKavitaSession(),
                onRenewed = { renewed -> kavitaCredentialStore.saveRenewedTokens(renewed, activeSession) },
            )
            pushDirtyKavitaProgress(
                api = api,
                tokens = tokens,
                items = kavitaItems,
                readerSessionStore = readerSessionStore,
                itemsAreWholeCatalog = false,
            ).map { throwable ->
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
    isRemoteServerAddressRetryableFailure() || this is com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
