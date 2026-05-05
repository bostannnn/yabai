package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicReaderFormats
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StumpPageSource(
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : RemoteComicPageSource<StumpStoredSession>(
    sourceType = com.example.unifiedreader.core.model.SourceType.STUMP,
    supportedFormats = ComicReaderFormats,
    missingSessionMessage = "Connect to Stump before reading remote comics.",
    ioDispatcher = ioDispatcher,
    scope = scope,
) {
    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)

    override fun activeSessionFor(
        item: ReadableItem,
    ): StumpStoredSession? = stumpCredentialStore.activeSessionFor(item)

    override suspend fun downloadPageBytes(
        session: StumpStoredSession,
        item: ReadableItem,
        page: ComicPageSpec,
    ): ByteArray? {
        val mediaId = item.remoteId ?: return null
        val pageIndex = page.id.toIntOrNull() ?: return null
        return runWithStoredSessionAddressFailover(
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
                api.downloadPageImage(
                    accessToken = accessToken,
                    mediaId = mediaId,
                    pageIndex = pageIndex,
                )
            }
        }.also {
            remoteSourceConnectivityPolicy.recordReaderContentSuccess(com.example.unifiedreader.core.model.SourceType.STUMP)
        }
    }

    override fun onReaderContentFailure(
        item: ReadableItem,
        throwable: Throwable,
    ) {
        remoteSourceConnectivityPolicy.recordReaderContentFailure(
            sourceType = com.example.unifiedreader.core.model.SourceType.STUMP,
            throwable = throwable,
        )
    }
}
