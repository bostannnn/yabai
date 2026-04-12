package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import javax.inject.Inject

interface KavitaCoverDataSource : RemoteCoverDataSource

class KavitaCoverRepository @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val coverStore: PersistentRemoteCoverStore,
) : KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override fun cachedCover(
        item: ReadableItem,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        return coverStore.cachedCoverFile(
            storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
            fileName = item.kavitaCacheFileName("cover"),
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
        val fileName = item.kavitaCacheFileName("cover")
        val cachedFile = coverStore.persistentCoverFile(
            storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
            fileName = fileName,
        )
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            return Uri.fromFile(cachedFile)
        }
        coverStore.withStorageLock(KAVITA_COVER_DIRECTORY_NAME) {
            if (cachedFile.isFile && cachedFile.length() > 0L) {
                return@withStorageLock
            }
            val session = kavitaCredentialStore.activeSessionFor(item)
                ?: error("Connect to the active Kavita server before loading remote covers.")
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
                    val coverUrl = item.coverUrl?.takeIf(String::isNotBlank)
                        ?: error("Unable to resolve Kavita cover URL.")
                    writeCachedFile(
                        destination = cachedFile,
                        targetDescription = "cached Kavita cover for ${item.title}",
                    ) { temporaryFile ->
                        temporaryFile.writeBytes(
                            api.downloadCover(
                                accessToken = accessToken,
                                coverUrl = coverUrl,
                                apiKey = apiKey,
                            ),
                        )
                    }
                }
            }
        }
        return Uri.fromFile(cachedFile)
    }

    override suspend fun clearCachedCovers() {
        coverStore.clearStorageDirectory(KAVITA_COVER_DIRECTORY_NAME)
    }
}

internal fun ReadableItem.kavitaCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileType?.toSafeFileSegment(),
).joinToString(separator = "-").plus(".$extension")

private const val KAVITA_COVER_DIRECTORY_NAME = "kavita-covers"
