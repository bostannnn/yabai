package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RemoteDocumentReadCache @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    private val readCacheMutex = Mutex()
    private val grimmoryRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    suspend fun documentUriForReading(
        item: ReadableItem,
    ): Uri? {
        if (!supportsReadCache(item)) return null
        if (!remoteSourceConnectivityPolicy.canOpenRemoteItem(item.sourceType)) return null
        val session = grimmoryCredentialStore.activeSessionFor(item) ?: return null
        val remoteItemId = item.remoteId ?: return null
        val file = readCacheFileFor(item)
        readCacheMutex.withLock {
            if (file.isFile && file.length() > 0L) return@withLock
            writeGrimmoryCacheFile(
                destination = file,
                targetDescription = "reader cache for ${item.title}",
            ) { temporaryFile ->
                runWithStoredSessionAddressFailover(
                    sessionStore = grimmoryCredentialStore,
                    session = session,
                    createApi = grimmoryApiFactory::create,
                    withActiveBaseUrl = { currentSession, activeBaseUrl ->
                        currentSession.copy(activeBaseUrl = activeBaseUrl)
                    },
                ) { api, activeSession ->
                    grimmoryRequestRunner.run(
                        api = api,
                        session = activeSession,
                    ) { accessToken ->
                        api.downloadBookContent(
                            accessToken = accessToken,
                            bookId = remoteItemId,
                            bookType = item.remoteFileType ?: item.format.name,
                            destination = temporaryFile,
                        )
                    }
                }
            }
        }
        return Uri.fromFile(file).takeIf { file.isFile && file.length() > 0L }
    }

    private fun supportsReadCache(
        item: ReadableItem,
    ): Boolean = item.sourceType == SourceType.GRIMMORY &&
        (item.format == FormatType.EPUB || item.format == FormatType.PDF)

    private fun readCacheFileFor(
        item: ReadableItem,
    ): File {
        val extension = item.format.name.lowercase()
        return File(
            File(context.applicationContext.cacheDir, "grimmory-reader-documents"),
            item.grimmoryCacheFileName(extension),
        )
    }
}
