package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.writeFileAtomically
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.RemoteCoverCacheResolver
import com.example.unifiedreader.feature.library.covers.RemoteCoverStorageDirectory

/**
 * A server's book covers, cached on the device: the cache lookup, the download into the cache through the saved
 * sign-in (trying the backup address when the main one is unreachable) and clearing are the same for every server.
 * A subclass names its cache files and downloads one cover's bytes.
 */
abstract class SourceCoverRepository<Session : AddressedSession<Session>, Api> internal constructor(
    final override val sourceType: SourceType,
    private val storage: RemoteCoverStorageDirectory,
    private val credentialStore: SourceSessionStore<Session>,
    private val createApi: (baseUrl: String) -> Api,
    private val coverCacheResolver: RemoteCoverCacheResolver,
) : RemoteCoverDataSource {
    private val sourceName = sourceType.name.lowercase().replaceFirstChar(Char::uppercase)

    /** The cover's cache file name, without the size suffix the resolver adds. */
    internal abstract fun ReadableItem.coverCacheFileName(): String

    /** Older names the same cover may be cached under. */
    internal abstract fun ReadableItem.legacyCoverCacheFileNames(session: Session?): List<String>

    /** Downloads [item]'s cover, renewing the sign-in if it has expired. */
    internal abstract suspend fun downloadCover(api: Api, session: Session, item: ReadableItem): ByteArray

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? {
        if (item.sourceType != sourceType) return null
        return coverCacheResolver.cachedCover(
            storage = storage,
            baseFileName = item.coverCacheFileName(),
            variant = variant.toCoverAssetVariant(),
            legacyBaseFileNames = item.legacyCoverCacheFileNames(credentialStore.activeSessionFor(item)),
        )?.let { asset ->
            CachedRemoteCover(
                uri = Uri.fromFile(asset.file),
                isPersistent = asset.isPersistent,
                matchesRequestedVariant = asset.matchesRequestedVariant,
            )
        }
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        if (item.sourceType != sourceType) return null
        val resolvedFile = coverCacheResolver.resolveCoverFile(
            storage = storage,
            baseFileName = item.coverCacheFileName(),
            variant = variant.toCoverAssetVariant(),
            legacyBaseFileNames = item.legacyCoverCacheFileNames(credentialStore.activeSessionFor(item)),
        ) { destination ->
            val session = credentialStore.activeSessionFor(item)
                ?: error("Connect to the active $sourceName server before loading remote covers.")
            runWithStoredSessionAddressFailover(
                sessionStore = credentialStore,
                session = session,
                createApi = createApi,
            ) { api, activeSession ->
                val bytes = downloadCover(api, activeSession, item)
                writeFileAtomically(
                    destination = destination,
                    targetDescription = "cached $sourceName cover for ${item.title}",
                ) { temporaryFile ->
                    temporaryFile.writeBytes(bytes)
                }
            }
        }
        return Uri.fromFile(resolvedFile)
    }

    override suspend fun warmCacheIndexes() {
        coverCacheResolver.warmCacheIndexes(storage)
    }

    override suspend fun clearCachedCovers() {
        coverCacheResolver.clearCachedCovers(storage)
    }
}

/**
 * The cache file name of a server item's [extension] file: the item's id, its [fileKey] (Kavita and Stump use the
 * file type, Grimmory the file id) and the cover's identity, so a changed cover gets a new file.
 */
internal fun ReadableItem.remoteCacheFileName(
    extension: String,
    coverUrl: String? = this.coverUrl,
    fileKey: String? = remoteFileType,
): String = listOfNotNull(
    id.toSafeFileSegment(),
    fileKey?.toSafeFileSegment(),
    coverIdentityFileSegment(coverUrl),
).joinToString(separator = "-").plus(".$extension")

/** Older names of [remoteCacheFileName]: the same file saved from the other address, or before covers had an identity. */
internal fun ReadableItem.remoteLegacyCacheFileNames(
    extension: String,
    session: StoredSourceSession? = null,
    fileKey: String? = remoteFileType,
): List<String> {
    val alternateAddressFileNames = alternateAddressCoverCacheFileNames(
        extension = extension,
        session = session,
        cacheFileName = { fileExtension, coverUrl -> remoteCacheFileName(fileExtension, coverUrl, fileKey) },
    )
    if (coverIdentityFileSegment() != null) return alternateAddressFileNames
    return alternateAddressFileNames + listOf(
        listOfNotNull(id.toSafeFileSegment(), fileKey?.toSafeFileSegment()).joinToString(separator = "-").plus(".$extension"),
    )
}
