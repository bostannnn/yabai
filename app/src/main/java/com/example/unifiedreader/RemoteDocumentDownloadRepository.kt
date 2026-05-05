package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

abstract class RemoteDocumentDownloadRepository<Session : StoredSourceSession>(
    private val context: Context,
    final override val sourceType: SourceType,
    private val storageDirectoryName: String,
    private val supportedFormats: Set<FormatType>,
    private val sessionStore: SourceSessionStore<Session>,
    private val missingSessionMessage: String,
) : RemoteItemDownloadDataSource {
    private val documentDownloadMutex = Mutex()

    final override fun supports(
        item: ReadableItem,
    ): Boolean = item.sourceType == sourceType && item.format in supportedFormats

    final override fun storedDocumentUri(
        item: ReadableItem,
    ): Uri? {
        if (!supports(item)) return null
        val file = localFileFor(item)
        return if (file.isFile && file.length() > 0L) {
            Uri.fromFile(file)
        } else {
            null
        }
    }

    final override suspend fun downloadToLocalStorage(
        item: ReadableItem,
    ): Uri? = prepareDocumentForReading(item)?.uri

    final override suspend fun prepareDocumentForReading(
        item: ReadableItem,
    ): RemoteItemDownloadResult? {
        if (!supports(item)) return null
        storedDocumentUri(item)?.let { uri ->
            return RemoteItemDownloadResult(
                uri = uri,
                downloadedFromNetwork = false,
            )
        }
        val file = localFileFor(item)
        var downloadedFromNetwork = false
        documentDownloadMutex.withLock {
            storedDocumentUri(item)?.let { return@withLock }
            val session = sessionStore.activeSessionFor(item) ?: error(missingSessionMessage)
            val remoteItemId = item.remoteId ?: error("Unable to resolve ${sourceType.displayName()} item id.")
            download(
                session = session,
                item = item,
                remoteItemId = remoteItemId,
                destination = file,
            )
            onStoredDocumentChanged(item)
            downloadedFromNetwork = true
        }
        return storedDocumentUri(item)?.let { uri ->
            RemoteItemDownloadResult(
                uri = uri,
                downloadedFromNetwork = downloadedFromNetwork,
            )
        }
    }

    final override suspend fun storedDocumentItemIds(
        items: List<ReadableItem>,
    ): Set<String> = withContext(Dispatchers.IO) {
        items
            .filter { item -> storedDocumentUri(item) != null }
            .mapTo(mutableSetOf(), ReadableItem::id)
    }

    final override suspend fun deleteStoredDocument(
        item: ReadableItem,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!supports(item)) return@withContext false
        val file = localFileFor(item)
        val deleted = file.exists() && file.delete()
        if (deleted) {
            onStoredDocumentChanged(item)
        }
        deleted
    }

    protected abstract suspend fun download(
        session: Session,
        item: ReadableItem,
        remoteItemId: String,
        destination: File,
    )

    protected open fun onStoredDocumentChanged(
        item: ReadableItem,
    ) = Unit

    protected open fun cacheFileName(
        item: ReadableItem,
        extension: String,
    ): String = item.remoteDocumentCacheFileName(extension)

    private fun localFileFor(
        item: ReadableItem,
    ): File {
        val extension = item.format.name.lowercase()
        return File(
            File(context.applicationContext.filesDir, storageDirectoryName),
            cacheFileName(item = item, extension = extension),
        )
    }
}

internal fun ReadableItem.remoteDocumentCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileId?.toSafeFileSegment(),
).joinToString(separator = "-").plus(".$extension")

private fun SourceType.displayName(): String = name.lowercase()
