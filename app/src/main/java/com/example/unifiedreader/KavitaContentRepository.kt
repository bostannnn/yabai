package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.model.AppReadableFormats
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class KavitaContentRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
) : RemoteDocumentDownloadRepository<KavitaStoredSession>(
    context = context,
    sourceType = SourceType.KAVITA,
    storageDirectoryName = "kavita-documents",
    supportedFormats = AppReadableFormats,
    sessionStore = kavitaCredentialStore,
    missingSessionMessage = "Connect to Kavita before downloading remote comics.",
) {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override suspend fun download(
        session: KavitaStoredSession,
        item: ReadableItem,
        remoteItemId: String,
        destination: File,
    ) = writeCachedFile(
        destination = destination,
        targetDescription = "downloaded Kavita file for ${item.title}",
    ) { temporaryFile ->
        runWithStoredSessionAddressFailover(
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
            ) { accessToken, apiKey ->
                api.downloadChapter(
                    accessToken = accessToken,
                    chapterId = remoteItemId,
                    apiKey = apiKey,
                    destination = temporaryFile,
                )
            }
        }
    }

    override fun onStoredDocumentChanged(
        item: ReadableItem,
    ) {
        cacheInvalidationDispatcher.invalidate(item.id)
    }
}
