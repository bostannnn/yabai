package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import java.io.File
import javax.inject.Inject

interface GrimmoryCoverDataSource : RemoteCoverDataSource

class GrimmoryCoverRepository @Inject constructor(
    private val grimmoryApiFactory: GrimmoryApiFactory,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val coverStore: PersistentRemoteCoverStore,
) : GrimmoryCoverDataSource {
    override val sourceType: SourceType = SourceType.GRIMMORY
    private val sessionRequestRunner = GrimmoryStoredSessionRequestRunner(grimmoryCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        return coverStore.cachedCoverFile(
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
            fileName = item.grimmoryCacheFileName("cover"),
            legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
        )?.let { cachedCover ->
            CachedRemoteCover(
                uri = Uri.fromFile(cachedCover.file),
                isPersistent = cachedCover.isPersistent,
            )
        }
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri? {
        if (item.sourceType != sourceType) return null
        val fileName = item.grimmoryCacheFileName("cover")
        val cachedFile = coverStore.persistentCoverFile(
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
            fileName = fileName,
        )
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            return Uri.fromFile(cachedFile)
        }
        var resolvedUri: Uri? = null
        coverStore.withStorageLock(GRIMMORY_COVER_DIRECTORY_NAME) {
            coverStore.moveLegacyCoverIfPresent(
                storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
                fileName = fileName,
                legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
            )?.let { migratedFile ->
                if (migratedFile.isFile && migratedFile.length() > 0L) {
                    resolvedUri = Uri.fromFile(migratedFile)
                    return@withStorageLock
                }
            }
            if (cachedFile.isFile && cachedFile.length() > 0L) {
                return@withStorageLock
            }
            val session = grimmoryCredentialStore.activeSessionFor(item)
                ?: error("Connect to the active Grimmory server before loading remote covers.")
            runWithStoredSessionAddressFailover(
                sessionStore = grimmoryCredentialStore,
                session = session,
                createApi = grimmoryApiFactory::create,
                withActiveBaseUrl = { currentSession, activeBaseUrl ->
                    currentSession.copy(activeBaseUrl = activeBaseUrl)
                },
            ) { api, activeSession ->
                downloadWithTokenRefresh(
                    api = api,
                    session = activeSession,
                    item = item,
                    destination = cachedFile,
                )
            }
            resolvedUri = Uri.fromFile(cachedFile)
        }
        return resolvedUri ?: Uri.fromFile(cachedFile)
    }

    override suspend fun clearCachedCovers() {
        coverStore.clearStorageDirectory(
            storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
            legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
        )
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

}

private const val GRIMMORY_COVER_DIRECTORY_NAME = "grimmory-covers"
private const val GRIMMORY_LEGACY_COVER_DIRECTORY_NAME = "grimmory-covers"
