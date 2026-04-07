package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GrimmoryContentRepository(
    private val context: Context,
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) {
    private val documentDownloadMutex = Mutex()
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    suspend fun getDocumentUri(
        item: ReadableItem,
    ): Uri? {
        if (item.sourceType != SourceType.GRIMMORY) return null
        val file = cacheFileFor(item)
        if (file.isFile && file.length() > 0L) {
            return Uri.fromFile(file)
        }
        documentDownloadMutex.withLock {
            if (file.isFile && file.length() > 0L) {
                return@withLock
            }
            val session = grimmoryCredentialStore.sessionFlow.value?.takeIf { storedSession ->
                storedSession.serverId == item.serverId
            } ?: error("Connect to Grimmory before opening remote books.")
            val api = grimmoryApiFactory.create(
                baseUrl = session.baseUrl,
                serverId = session.serverId,
            )
            sessionRequestRunner.run(
                api = api,
                session = session,
            ) { accessToken ->
                download(
                    api = api,
                    accessToken = accessToken,
                    item = item,
                    destination = file,
                )
            }
        }
        return Uri.fromFile(file)
    }

    private suspend fun download(
        api: GrimmoryApi,
        accessToken: String,
        item: ReadableItem,
        destination: File,
    ) = writeGrimmoryCacheFile(
        destination = destination,
        targetDescription = "cached Grimmory file for ${item.title}",
    ) { temporaryFile ->
        api.downloadBookContent(
            accessToken = accessToken,
            bookId = item.id,
            bookType = item.remoteFileType ?: item.format.name,
            destination = temporaryFile,
        )
    }

    private fun cacheFileFor(
        item: ReadableItem,
    ): File {
        val extension = item.format.name.lowercase()
        return File(
            File(context.applicationContext.cacheDir, "grimmory-documents/${item.serverId.toGrimmoryFileSegment()}"),
            item.grimmoryCacheFileName(extension),
        )
    }
}
