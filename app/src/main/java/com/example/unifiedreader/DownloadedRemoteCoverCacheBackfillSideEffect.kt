package com.example.unifiedreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.downloads.DownloadCompletionSideEffect
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.downloads.downloadDataSourceFor
import com.example.unifiedreader.feature.library.covers.GRIMMORY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.GRIMMORY_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.KAVITA_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.KAVITA_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.PersistentRemoteCoverStore
import com.example.unifiedreader.feature.library.covers.RemoteCoverStorageDirectory
import com.example.unifiedreader.feature.library.covers.STUMP_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.STUMP_LEGACY_COVER_DIRECTORY_NAME
import com.example.unifiedreader.feature.library.covers.writeCoverCacheFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Singleton
class DownloadedRemoteCoverCacheBackfiller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    private val coverStore: PersistentRemoteCoverStore,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val stumpCredentialStore: StumpCredentialStore,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val pdfCoverRenderer: DownloadedPdfCoverRenderer,
) : DownloadedRemoteCoverBackfiller {
    private val comicArchiveCodecs = listOf(
        ZipComicArchiveCodec,
        RarComicArchiveCodec(context),
    )

    override fun hasStoredDocument(
        item: ReadableItem,
    ): Boolean = item.sourceType != SourceType.LOCAL &&
        item.format in BackfillableCoverFormats &&
        remoteDownloadDataSources.downloadDataSourceFor(item)?.storedDocumentUri(item) != null

    fun supportsDownloadCompletion(
        item: ReadableItem,
    ): Boolean = item.sourceType != SourceType.LOCAL &&
        item.format in BackfillableCoverFormats &&
        remoteDownloadDataSources.downloadDataSourceFor(item) != null

    override suspend fun backfillOriginalCoverFromStoredDocument(
        item: ReadableItem,
    ): Boolean {
        val storedUri = remoteDownloadDataSources.downloadDataSourceFor(item)
            ?.storedDocumentUri(item)
            ?: return false
        val target = item.coverBackfillTarget() ?: return false
        if (target.hasCachedCover()) return true
        val coverBytes = coverBytesFor(
            item = item,
            uri = storedUri,
        )?.takeIf(ByteArray::isNotEmpty) ?: return false
        return coverStore.withStorageLock(target.storage.storageDirectoryName) {
            if (target.hasCachedCover()) return@withStorageLock true
            val destination = coverStore.persistentCoverFile(
                storageDirectoryName = target.storage.storageDirectoryName,
                fileName = target.baseFileName,
            )
            writeCoverCacheFile(
                destination = destination,
                targetDescription = "downloaded cover for ${item.title}",
            ) { temporaryFile ->
                temporaryFile.writeBytes(coverBytes)
            }
            coverStore.recordPersistentCoverFile(
                storageDirectoryName = target.storage.storageDirectoryName,
                fileName = target.baseFileName,
                file = destination,
            )
            true
        }
    }

    private fun DownloadedRemoteCoverBackfillTarget.hasCachedCover(): Boolean =
        sequenceOf(baseFileName)
            .plus(legacyBaseFileNames.asSequence())
            .any { fileName ->
                coverStore.cachedCoverFile(
                    storageDirectoryName = storage.storageDirectoryName,
                    fileName = fileName,
                    legacyDirectoryName = storage.legacyDirectoryName,
                ) != null
            }

    private suspend fun coverBytesFor(
        item: ReadableItem,
        uri: Uri,
    ): ByteArray? = when (item.format) {
        FormatType.CBZ,
        FormatType.CBR -> comicCoverBytesFor(item, uri)
        FormatType.EPUB -> epubCoverBytesFor(uri)
        FormatType.PDF -> pdfCoverRenderer.coverBytes(uri)
    }

    private suspend fun comicCoverBytesFor(
        item: ReadableItem,
        uri: Uri,
    ): ByteArray? {
        val preferredCodec = comicArchiveCodecs.firstOrNull { codec -> codec.format == item.format }
        val codecs = listOfNotNull(preferredCodec) + comicArchiveCodecs.filterNot { codec -> codec === preferredCodec }
        codecs.forEach { codec ->
            val coverEntryName = codec.listImageEntries(
                context = context,
                uri = uri,
            ).firstOrNull() ?: return@forEach
            codec.readEntryBytes(
                context = context,
                uri = uri,
                entryName = coverEntryName,
            )?.let { bytes -> return bytes }
        }
        return null
    }

    private suspend fun epubCoverBytesFor(
        uri: Uri,
    ): ByteArray? {
        val coverEntryName = extractEpubMetadata(
            context = context,
            uri = uri,
        )?.coverEntryName ?: return null
        return readZipEntryBytes(
            context = context,
            uri = uri,
            entryName = coverEntryName,
        )
    }

    private fun ReadableItem.coverBackfillTarget(): DownloadedRemoteCoverBackfillTarget? = when (sourceType) {
        SourceType.KAVITA -> DownloadedRemoteCoverBackfillTarget(
            storage = RemoteCoverStorageDirectory(
                storageDirectoryName = KAVITA_COVER_DIRECTORY_NAME,
                legacyDirectoryName = KAVITA_LEGACY_COVER_DIRECTORY_NAME,
            ),
            baseFileName = kavitaCacheFileName("cover"),
            legacyBaseFileNames = kavitaLegacyCacheFileNames(
                extension = "cover",
                session = kavitaCredentialStore.activeSessionFor(this),
            ),
        )
        SourceType.STUMP -> DownloadedRemoteCoverBackfillTarget(
            storage = RemoteCoverStorageDirectory(
                storageDirectoryName = STUMP_COVER_DIRECTORY_NAME,
                legacyDirectoryName = STUMP_LEGACY_COVER_DIRECTORY_NAME,
            ),
            baseFileName = stumpCacheFileName("cover"),
            legacyBaseFileNames = stumpLegacyCacheFileNames(
                extension = "cover",
                session = stumpCredentialStore.activeSessionFor(this),
            ),
        )
        SourceType.GRIMMORY -> DownloadedRemoteCoverBackfillTarget(
            storage = RemoteCoverStorageDirectory(
                storageDirectoryName = GRIMMORY_COVER_DIRECTORY_NAME,
                legacyDirectoryName = GRIMMORY_LEGACY_COVER_DIRECTORY_NAME,
            ),
            baseFileName = grimmoryCacheFileName("cover"),
            legacyBaseFileNames = grimmoryLegacyCacheFileNames(
                extension = "cover",
                session = grimmoryCredentialStore.activeSessionFor(this),
            ),
        )
        SourceType.LOCAL -> null
    }
}

interface DownloadedPdfCoverRenderer {
    suspend fun coverBytes(
        uri: Uri,
    ): ByteArray?
}

@Singleton
class AndroidDownloadedPdfCoverRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DownloadedPdfCoverRenderer {
    override suspend fun coverBytes(
        uri: Uri,
    ): ByteArray? = withContext(Dispatchers.IO) {
        var renderedBitmap: Bitmap? = null
        try {
            val bytes = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount <= 0) return@withContext null
                    renderer.openPage(0).use { page ->
                        val scale = PDF_COVER_RENDER_LONG_EDGE_PX.toFloat() /
                            maxOf(page.width, page.height).coerceAtLeast(1)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        renderedBitmap = bitmap
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        currentCoroutineContext().ensureActive()
                        ByteArrayOutputStream().use { output ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                return@withContext null
                            }
                            output.toByteArray()
                        }
                    }
                }
            }
            bytes?.takeIf(ByteArray::isNotEmpty)
        } finally {
            renderedBitmap?.recycle()
        }
    }
}

private data class DownloadedRemoteCoverBackfillTarget(
    val storage: RemoteCoverStorageDirectory,
    val baseFileName: String,
    val legacyBaseFileNames: List<String>,
)

interface DownloadedRemoteCoverBackfiller {
    fun hasStoredDocument(
        item: ReadableItem,
    ): Boolean

    suspend fun backfillOriginalCoverFromStoredDocument(
        item: ReadableItem,
    ): Boolean
}

class DownloadedRemoteCoverCacheBackfillSideEffect @Inject constructor(
    private val backfiller: DownloadedRemoteCoverCacheBackfiller,
) : DownloadCompletionSideEffect {
    override fun supports(
        item: ReadableItem,
    ): Boolean = backfiller.supportsDownloadCompletion(item)

    override suspend fun onDownloadCompleted(
        item: ReadableItem,
    ) {
        backfiller.backfillOriginalCoverFromStoredDocument(item)
    }
}

internal object NoOpDownloadedRemoteCoverBackfiller : DownloadedRemoteCoverBackfiller {
    override fun hasStoredDocument(
        item: ReadableItem,
    ): Boolean = false

    override suspend fun backfillOriginalCoverFromStoredDocument(
        item: ReadableItem,
    ): Boolean = false
}

private val BackfillableCoverFormats = setOf(
    FormatType.CBZ,
    FormatType.CBR,
    FormatType.EPUB,
    FormatType.PDF,
)

private const val PDF_COVER_RENDER_LONG_EDGE_PX = 1200
