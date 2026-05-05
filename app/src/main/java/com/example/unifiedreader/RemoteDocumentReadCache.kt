package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isBookReaderFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
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

    fun hasCachedDocument(
        item: ReadableItem,
    ): Boolean = supportsReadCache(item) && readCacheFileFor(item).hasReadableContent()

    suspend fun documentUriForReading(
        item: ReadableItem,
    ): Uri? {
        if (!supportsReadCache(item)) return null
        val file = readCacheFileFor(item)
        if (file.hasReadableContent()) return Uri.fromFile(file)
        if (!remoteSourceConnectivityPolicy.canAttemptReaderContent(item.sourceType)) return null
        val session = grimmoryCredentialStore.activeSessionFor(item) ?: return null
        val remoteItemId = item.remoteId ?: return null
        var downloadedFromNetwork = false
        readCacheMutex.withLock {
            if (file.hasReadableContent()) return@withLock
            try {
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
                downloadedFromNetwork = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                remoteSourceConnectivityPolicy.recordReaderContentFailure(SourceType.GRIMMORY, error)
                throw error
            }
        }
        if (downloadedFromNetwork) {
            remoteSourceConnectivityPolicy.recordReaderContentSuccess(SourceType.GRIMMORY)
        }
        return Uri.fromFile(file).takeIf { file.hasReadableContent() }
    }

    private fun supportsReadCache(
        item: ReadableItem,
    ): Boolean = item.sourceType == SourceType.GRIMMORY &&
        item.format.isBookReaderFormat()

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

private fun File.hasReadableContent(): Boolean = isFile && length() > 0L
