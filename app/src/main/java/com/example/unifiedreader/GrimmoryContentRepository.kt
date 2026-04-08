package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface GrimmoryContentDataSource {
    suspend fun getDocumentUri(item: ReadableItem): Uri?

    suspend fun downloadToLocalStorage(item: ReadableItem): Uri?

    suspend fun storedDocumentItemIds(items: List<ReadableItem>): Set<String>

    suspend fun deleteStoredDocument(item: ReadableItem): Boolean
}

class GrimmoryContentRepository(
    private val context: Context,
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) : GrimmoryContentDataSource {
    private val documentDownloadMutex = Mutex()
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override suspend fun getDocumentUri(
        item: ReadableItem,
    ): Uri? = downloadToLocalStorage(item)

    override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? {
        if (item.sourceType != SourceType.GRIMMORY) return null
        val file = localFileFor(item)
        if (file.isFile && file.length() > 0L) {
            return Uri.fromFile(file)
        }
        documentDownloadMutex.withLock {
            if (file.isFile && file.length() > 0L) {
                return@withLock
            }
            val session = grimmoryCredentialStore.activeSessionFor(item)
                ?: error("Connect to the active Grimmory server before opening remote books.")
            val remoteItemId = item.remoteId ?: error("Unable to resolve Grimmory book id.")
            val api = grimmoryApiFactory.create(
                baseUrl = session.baseUrl,
            )
            sessionRequestRunner.run(
                api = api,
                session = session,
            ) { accessToken ->
                download(
                    api = api,
                    accessToken = accessToken,
                    remoteItemId = remoteItemId,
                    item = item,
                    destination = file,
                )
            }
        }
        return Uri.fromFile(file)
    }

    override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = withContext(Dispatchers.IO) {
        items
            .filter(::isDocumentStored)
            .mapTo(mutableSetOf(), ReadableItem::id)
    }

    override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = withContext(Dispatchers.IO) {
        if (item.sourceType != SourceType.GRIMMORY) return@withContext false
        val file = localFileFor(item)
        file.exists() && file.delete()
    }

    private fun isDocumentStored(
        item: ReadableItem,
    ): Boolean {
        if (item.sourceType != SourceType.GRIMMORY) return false
        val file = localFileFor(item)
        return file.isFile && file.length() > 0L
    }

    private suspend fun download(
        api: GrimmoryApi,
        accessToken: String,
        remoteItemId: String,
        item: ReadableItem,
        destination: File,
    ) = writeGrimmoryCacheFile(
        destination = destination,
        targetDescription = "downloaded Grimmory file for ${item.title}",
    ) { temporaryFile ->
        api.downloadBookContent(
            accessToken = accessToken,
            bookId = remoteItemId,
            bookType = item.remoteFileType ?: item.format.name,
            destination = temporaryFile,
        )
    }

    private fun localFileFor(
        item: ReadableItem,
    ): File {
        val extension = item.format.name.lowercase()
        return File(
            File(context.applicationContext.filesDir, "grimmory-documents"),
            item.grimmoryCacheFileName(extension),
        )
    }
}
