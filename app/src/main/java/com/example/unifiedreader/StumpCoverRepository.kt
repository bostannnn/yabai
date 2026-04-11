package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface StumpCoverDataSource {
    suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri?

    suspend fun clearCachedCovers()
}

class StumpCoverRepository(
    private val context: Context,
    private val stumpApiFactory: StumpApiFactory,
    private val stumpCredentialStore: StumpCredentialStore,
) : StumpCoverDataSource {
    private val coverDownloadMutex = Mutex()
    private val sessionRequestRunner = StumpStoredSessionRequestRunner(stumpCredentialStore)

    override suspend fun getCoverUri(
        item: ReadableItem,
    ): Uri? {
        if (item.sourceType != SourceType.STUMP) return null
        val cachedFile = cacheFileFor(item)
        if (cachedFile.isFile && cachedFile.length() > 0L) {
            return Uri.fromFile(cachedFile)
        }
        coverDownloadMutex.withLock {
            if (cachedFile.isFile && cachedFile.length() > 0L) {
                return@withLock
            }
            val session = stumpCredentialStore.activeSessionFor(item)
                ?: error("Connect to the active Stump server before loading remote covers.")
            runWithStoredSessionAddressFailover(
                sessionStore = stumpCredentialStore,
                session = session,
                createApi = stumpApiFactory::create,
                withActiveBaseUrl = { currentSession, activeBaseUrl ->
                    currentSession.copy(activeBaseUrl = activeBaseUrl)
                },
            ) { api, activeSession ->
                sessionRequestRunner.run(
                    api = api,
                    session = activeSession,
                ) { accessToken ->
                    writeCachedFile(
                        destination = cachedFile,
                        targetDescription = "cached Stump cover for ${item.title}",
                    ) { temporaryFile ->
                        val coverBytes = when {
                            item.isStumpSeriesContainer() -> {
                                val seriesId = item.remoteFileId
                                    ?: error("Unable to resolve Stump series id.")
                                api.downloadSeriesThumbnail(
                                    accessToken = accessToken,
                                    seriesId = seriesId,
                                )
                            }
                            !item.coverUrl.isNullOrBlank() -> {
                                runCatching {
                                    api.downloadUrl(
                                        accessToken = accessToken,
                                        url = requireNotNull(item.coverUrl),
                                    )
                                }.getOrElse {
                                    val mediaId = item.remoteId
                                        ?: error("Unable to resolve Stump media id.")
                                    api.downloadThumbnail(
                                        accessToken = accessToken,
                                        mediaId = mediaId,
                                    )
                                }
                            }
                            else -> {
                                val mediaId = item.remoteId
                                    ?: error("Unable to resolve Stump media id.")
                                api.downloadThumbnail(
                                    accessToken = accessToken,
                                    mediaId = mediaId,
                                )
                            }
                        }
                        temporaryFile.writeBytes(coverBytes)
                    }
                }
            }
        }
        return Uri.fromFile(cachedFile)
    }

    override suspend fun clearCachedCovers() {
        withContext(Dispatchers.IO) {
            coverStorageDirectory().deleteRecursively()
        }
    }

    private fun cacheFileFor(
        item: ReadableItem,
    ): File = File(
        coverStorageDirectory(),
        item.stumpCacheFileName("cover"),
    )

    private fun coverStorageDirectory(): File = File(
        context.applicationContext.cacheDir,
        "stump-covers",
    ).apply {
        mkdirs()
    }
}

internal fun ReadableItem.stumpCacheFileName(
    extension: String,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    remoteFileType?.toSafeFileSegment(),
).joinToString(separator = "-").plus(".$extension")
