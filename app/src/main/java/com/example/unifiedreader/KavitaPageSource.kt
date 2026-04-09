package com.example.unifiedreader

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KavitaPageSource(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : RemoteComicPageSource<KavitaStoredSession>(
    sourceType = com.example.unifiedreader.core.model.SourceType.KAVITA,
    supportedFormats = setOf(FormatType.CBZ),
    missingSessionMessage = "Connect to Kavita before reading remote comics.",
    ioDispatcher = ioDispatcher,
    scope = scope,
) {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override fun activeSessionFor(
        item: ReadableItem,
    ): KavitaStoredSession? = kavitaCredentialStore.activeSessionFor(item)

    override suspend fun downloadPageBytes(
        session: KavitaStoredSession,
        item: ReadableItem,
        page: ComicPageSpec,
    ): ByteArray? {
        val chapterId = item.remoteId ?: return null
        val pageIndex = page.id.toIntOrNull() ?: return null
        val api = kavitaApiFactory.create(session.baseUrl)
        return sessionRequestRunner.run(
            api = api,
            session = session,
        ) { accessToken, apiKey ->
            api.downloadPageImage(
                accessToken = accessToken,
                chapterId = chapterId,
                pageIndex = pageIndex,
                apiKey = apiKey,
            )
        }
    }
}
