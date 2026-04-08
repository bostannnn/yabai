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

interface GrimmoryCoverDataSource {
    suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri?

    suspend fun clearCachedCovers()
}

class GrimmoryCoverRepository(
    private val context: Context,
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) : GrimmoryCoverDataSource {
    private val coverDownloadMutex = Mutex()
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri? {
        if (item.sourceType != SourceType.GRIMMORY) return null
        val cachedFile = cacheFileFor(item)
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            return Uri.fromFile(cachedFile)
        }
        coverDownloadMutex.withLock {
            if (cachedFile.isFile && cachedFile.length() > 0L) {
                return@withLock
            }
            val session = grimmoryCredentialStore.activeSessionFor(item)
                ?: error("Connect to the active Grimmory server before loading remote covers.")
            val api = grimmoryApiFactory.create(
                baseUrl = session.baseUrl,
            )
            downloadWithTokenRefresh(
                api = api,
                session = session,
                item = item,
                destination = cachedFile,
            )
        }
        return Uri.fromFile(cachedFile)
    }

    override suspend fun clearCachedCovers() {
        withContext(Dispatchers.IO) {
            coverStorageDirectory().deleteRecursively()
        }
    }

    private suspend fun downloadWithTokenRefresh(
        api: GrimmoryApi,
        session: GrimmoryStoredSession,
        item: ReadableItem,
        destination: File,
    ) {
        sessionRequestRunner.run(
            api = api,
            session = session,
        ) { accessToken ->
            download(
                api = api,
                accessToken = accessToken,
                item = item,
                destination = destination,
            )
        }
    }

    private suspend fun download(
        api: GrimmoryApi,
        accessToken: String,
        item: ReadableItem,
        destination: File,
    ) = writeGrimmoryCacheFile(
        destination = destination,
        targetDescription = "cached Grimmory cover for ${item.title}",
    ) { temporaryFile ->
        val remoteItemId = item.remoteId ?: error("Unable to resolve Grimmory book id.")
        val bytes = api.downloadBookCover(
            accessToken = accessToken,
            bookId = remoteItemId,
            coverUrl = item.coverUrl,
        )
        temporaryFile.writeBytes(bytes)
    }

    private fun cacheFileFor(
        item: ReadableItem,
    ): File = File(
        coverStorageDirectory(),
        item.grimmoryCacheFileName("cover"),
    )

    private fun coverStorageDirectory(): File = File(
        context.applicationContext.cacheDir,
        "grimmory-covers",
    ).apply {
        mkdirs()
    }
}
