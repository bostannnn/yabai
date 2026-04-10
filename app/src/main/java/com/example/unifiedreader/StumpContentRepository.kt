package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class StumpContentRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val stumpApiFactory: StumpApiFactory,
    stumpCredentialStore: StumpCredentialStore,
    private val cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
) : RemoteDocumentDownloadRepository<StumpStoredSession>(
    context = context,
    sourceType = SourceType.STUMP,
    storageDirectoryName = "stump-documents",
    supportedFormats = setOf(FormatType.CBZ, FormatType.CBR),
    sessionStore = stumpCredentialStore,
    missingSessionMessage = "Connect to Stump before downloading remote comics.",
) {
    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)

    override suspend fun download(
        session: StumpStoredSession,
        item: ReadableItem,
        remoteItemId: String,
        destination: File,
    ) = writeCachedFile(
        destination = destination,
        targetDescription = "downloaded Stump file for ${item.title}",
    ) { temporaryFile ->
        val api = stumpApiFactory.create(session.baseUrl)
        sessionRequestRunner.run(
            api = api,
            session = session,
        ) { accessToken ->
            api.downloadMediaFile(
                accessToken = accessToken,
                mediaId = remoteItemId,
                destination = temporaryFile,
            )
        }
    }

    override fun onStoredDocumentChanged(
        item: ReadableItem,
    ) {
        cacheInvalidationDispatcher.invalidate(item.id)
    }
}
