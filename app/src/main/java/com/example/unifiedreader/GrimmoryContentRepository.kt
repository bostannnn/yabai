package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import java.io.File

interface GrimmoryContentDataSource : com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource

class GrimmoryContentRepository(
    context: Context,
    private val grimmoryApiFactory: GrimmoryApiFactory,
    grimmoryCredentialStore: GrimmoryCredentialStore,
    private val cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
) : RemoteDocumentDownloadRepository<GrimmoryStoredSession>(
    context = context,
    sourceType = SourceType.GRIMMORY,
    storageDirectoryName = "grimmory-documents",
    supportedFormats = setOf(FormatType.EPUB, FormatType.PDF),
    sessionStore = grimmoryCredentialStore,
    missingSessionMessage = "Connect to the active Grimmory server before opening remote books.",
), GrimmoryContentDataSource {
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override suspend fun download(
        session: GrimmoryStoredSession,
        item: ReadableItem,
        remoteItemId: String,
        destination: File,
    ) = writeGrimmoryCacheFile(
        destination = destination,
        targetDescription = "downloaded Grimmory file for ${item.title}",
    ) { temporaryFile ->
        val api = grimmoryApiFactory.create(
            baseUrl = session.baseUrl,
        )
        sessionRequestRunner.run(
            api = api,
            session = session,
        ) { accessToken ->
            api.downloadBookContent(
                accessToken = accessToken,
                bookId = remoteItemId,
                bookType = item.remoteFileType ?: item.format.name,
                destination = temporaryFile,
            )
        }
    }

    override fun cacheFileName(
        item: ReadableItem,
        extension: String,
    ): String = item.grimmoryCacheFileName(extension)

    override fun onStoredDocumentChanged(
        item: ReadableItem,
    ) {
        cacheInvalidationDispatcher.invalidate(item.id)
    }
}
