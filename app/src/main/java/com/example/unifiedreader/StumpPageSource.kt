package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StumpPageSource(
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : RemoteComicPageSource<StumpStoredSession>(
    sourceType = com.example.unifiedreader.core.model.SourceType.STUMP,
    supportedFormats = setOf(FormatType.CBZ, FormatType.CBR),
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
        val api = stumpApiFactory.create(session.baseUrl)
        return sessionRequestRunner.run(
            api = api,
            session = session,
        ) { accessToken ->
            api.downloadPageImage(
                accessToken = accessToken,
                mediaId = mediaId,
                pageIndex = pageIndex,
            )
        }
    }
}
