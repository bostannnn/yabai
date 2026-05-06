package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.isComicReaderFormat
import com.example.unifiedreader.feature.downloads.downloadDataSourceFor
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagesUnavailableDiagnostic
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewOptions
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.jvm.JvmSuppressWildcards

class DownloadedRemoteComicPageSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val remoteDownloadDataSources: List<@JvmSuppressWildcards RemoteItemDownloadDataSource>,
    private val archiveEntryCache: DownloadedRemoteComicArchiveEntryCache,
    cacheInvalidationDispatcher: DownloadedRemoteComicCacheInvalidationDispatcher,
    previewStore: PersistentComicPagePreviewStore? = null,
) : ComicPageSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val comicArchiveCodecs: List<ComicArchiveCodec> = defaultDownloadedRemoteComicArchiveCodecs(context)
    private val comicPageLoader = LocalComicPageLoader(
        context = context,
        comicArchiveCodecs = comicArchiveCodecs,
        scope = scope,
        previewStore = previewStore,
    )
    private val entryCacheCoordinator = DownloadedRemoteComicEntryCacheCoordinator(archiveEntryCache)
    private val archiveDiagnosticsByItemId = ConcurrentHashMap<String, DownloadedComicArchiveDiagnostic>()
    private val archiveFormatByItemId = ConcurrentHashMap<String, FormatType>()

    init {
        cacheInvalidationDispatcher.register(::invalidateItemCaches)
    }

    fun hasStoredDocument(
        item: ReadableItem,
    ): Boolean = storedDocumentUriFor(item) != null && item.format.isComicReaderFormat()

    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> {
        if (!hasStoredDocument(item)) return emptyList()
        val cachedEntryNames = archiveEntryCache.get(item.id).orEmpty()
        return if (cachedEntryNames.isNotEmpty()) {
            cachedEntryNames.toComicPageSpecs(item)
        } else {
            List(item.pageCount?.coerceAtLeast(0) ?: 0) { index ->
                ComicPageSpec(
                    id = index.toString(),
                    pageIndex = index,
                    contentDescription = "${item.title} page ${index + 1}",
                )
            }
        }
    }

    override fun pagesUnavailableDiagnostic(
        item: ReadableItem,
    ): ComicPagesUnavailableDiagnostic {
        val unsupportedFormatMessage = if (!item.format.isComicReaderFormat()) {
            "Downloaded comic reader supports CBZ/CBR only."
        } else {
            null
        }
        val dataSource = remoteDownloadDataSources.downloadDataSourceFor(item)
        val storedUri = dataSource?.storedDocumentUri(item)
        val cachedEntryNames = archiveEntryCache.get(item.id)
        val archiveDiagnostic = archiveDiagnosticsByItemId[item.id]
        val title = when {
            unsupportedFormatMessage != null -> "Unsupported downloaded comic format"
            dataSource == null -> "No download data source for this comic"
            storedUri == null -> "Downloaded comic file was not found"
            archiveDiagnostic?.allEntryCount == 0 -> "Downloaded comic archive has no entries"
            cachedEntryNames != null && cachedEntryNames.isEmpty() -> "Downloaded comic archive has no readable pages"
            item.pageCount == null -> "Downloaded comic pages are still being inspected"
            else -> "Downloaded comic pages are not ready"
        }
        val detail = buildString {
            append("itemId=${item.id}, source=${item.sourceType}, format=${item.format}")
            append(", remoteId=${item.remoteId.orEmpty()}, remoteFileId=${item.remoteFileId.orEmpty()}")
            append(", pageCount=${item.pageCount ?: "unknown"}")
            append(", storedUri=${storedUri?.toString().orEmpty()}")
            append(", cachedEntries=${cachedEntryNames?.size?.toString() ?: "not loaded"}")
            archiveDiagnostic?.let { diagnostic ->
                append(", fileBytes=${diagnostic.fileBytes ?: "unknown"}")
                append(", firstBytes=${diagnostic.firstBytesHex.orEmpty()}")
                append(", archiveEntries=${diagnostic.allEntryCount}")
                append(", imageEntries=${diagnostic.imageEntryCount}")
                if (diagnostic.firstEntryNames.isNotEmpty()) {
                    append(", firstEntries=${diagnostic.firstEntryNames.joinToString(separator = " / ")}")
                }
            }
            unsupportedFormatMessage?.let { message ->
                append(", reason=$message")
            }
        }
        return ComicPagesUnavailableDiagnostic(
            title = title,
            detail = detail,
        )
    }

    override fun observePages(
        item: ReadableItem,
    ): Flow<List<ComicPageSpec>> = flow {
        val initialPages = getPages(item)
        emit(initialPages)
        val resolvedPages = resolvedArchivePagesFor(item) ?: return@flow
        if (resolvedPages != initialPages) {
            emit(resolvedPages)
        }
    }.flowOn(Dispatchers.IO)

    override fun observePagesUnavailableDiagnostic(
        item: ReadableItem,
    ): Flow<ComicPagesUnavailableDiagnostic> = flow {
        emit(pagesUnavailableDiagnostic(item))
        inspectArchiveForDiagnostics(item)
        archiveEntryNamesFor(item)
        emit(pagesUnavailableDiagnostic(item))
    }.flowOn(Dispatchers.IO)

    override suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? {
        val record = localRecordFor(item) ?: return null
        val entryName = record.pageEntryNames.getOrNull(page.pageIndex) ?: return null
        return comicPageLoader.loadPageMetadata(
            item = item,
            record = record,
            entryName = entryName,
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult {
        val record = localRecordFor(item)
            ?: return ComicPageLoadResult.Failure("Unable to find downloaded archive.")
        val entryName = record.pageEntryNames.getOrNull(page.pageIndex)
            ?: return ComicPageLoadResult.Failure("Unable to find downloaded page.")
        return comicPageLoader.loadPageImage(
            item = item,
            record = record,
            entryName = entryName,
            priority = priority,
        )
    }

    override suspend fun loadPagePreview(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult {
        val record = localRecordFor(item)
            ?: return ComicPagePreviewResult.Unavailable
        val entryName = record.pageEntryNames.getOrNull(page.pageIndex)
            ?: return ComicPagePreviewResult.Unavailable
        return comicPageLoader.loadPagePreview(
            item = item,
            record = record,
            entryName = entryName,
            options = options,
        )
    }

    override suspend fun loadPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? {
        val record = localRecordFor(item) ?: return null
        val entryName = record.pageEntryNames.getOrNull(page.pageIndex) ?: return null
        return comicPageLoader.loadPageMetadata(
            item = item,
            record = record,
            entryName = entryName,
        )
    }

    override suspend fun loadCachedPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? {
        val entryNames = archiveEntryCache.get(item.id).orEmpty()
        if (entryNames.isEmpty()) return null
        val record = localRecordForCachedEntries(item = item, entryNames = entryNames) ?: return null
        val entryName = record.pageEntryNames.getOrNull(page.pageIndex) ?: return null
        return comicPageLoader.cachedPageMetadata(
            item = item,
            record = record,
            entryName = entryName,
        )
    }

    override fun observePagePreviewVersion(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
    ): kotlinx.coroutines.flow.Flow<Long> {
        val entryNames = archiveEntryCache.get(item.id).orEmpty()
        if (entryNames.isEmpty()) return kotlinx.coroutines.flow.flowOf(0L)
        val record = localRecordForCachedEntries(item = item, entryNames = entryNames)
            ?: return kotlinx.coroutines.flow.flowOf(0L)
        val spreadEntryNames = pages.mapNotNull { page -> entryNames.getOrNull(page.pageIndex) }
        return comicPageLoader.observePagePreviewVersion(
            comicPageLoader.pagePreviewCacheKeys(
                item = item,
                record = record,
                entryNames = spreadEntryNames,
                options = options,
            ),
        )
    }

    override fun prefetchPagePreviews(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
        priorityPageIndex: Int?,
    ) {
        if (!hasStoredDocument(item) || pages.isEmpty()) return
        val entryNames = archiveEntryCache.get(item.id).orEmpty()
        if (entryNames.isEmpty()) return
        val record = localRecordForCachedEntries(item = item, entryNames = entryNames) ?: return
        comicPageLoader.prefetchPagePreviews(
            item = item,
            record = record,
            entryNames = entryNames,
            options = options,
            priorityPageIndex = priorityPageIndex,
        )
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        if (!hasStoredDocument(item)) return
        val entryNames = archiveEntryCache.get(item.id).orEmpty()
        if (entryNames.isEmpty()) return
        val record = localRecordForCachedEntries(item = item, entryNames = entryNames) ?: return
        comicPageLoader.prefetchAround(
            item = item,
            record = record,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
        )
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
        window: ComicPagePrefetchWindow,
    ) {
        if (!hasStoredDocument(item)) return
        val entryNames = archiveEntryCache.get(item.id).orEmpty()
        if (entryNames.isEmpty()) return
        val record = localRecordForCachedEntries(item = item, entryNames = entryNames) ?: return
        comicPageLoader.prefetchAround(
            item = item,
            record = record,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
            window = window,
        )
    }

    private suspend fun localRecordFor(
        item: ReadableItem,
    ): LocalDocumentRecord? {
        val entryNames = archiveEntryNamesFor(item) ?: return null
        return localRecordForCachedEntries(item = item, entryNames = entryNames)
    }

    private suspend fun resolvedArchivePagesFor(
        item: ReadableItem,
    ): List<ComicPageSpec>? = archiveEntryNamesFor(item)
        ?.toComicPageSpecs(item)

    private suspend fun archiveEntryNamesFor(
        item: ReadableItem,
    ): List<String>? {
        val uri = storedDocumentUriFor(item) ?: return null
        return entryCacheCoordinator.entryNamesFor(item.id) {
            val resolvedArchive = resolveArchiveCodecAndEntries(item = item, uri = uri)
                ?: return@entryNamesFor emptyList()
            archiveFormatByItemId[item.id] = resolvedArchive.codec.format
            resolvedArchive.imageEntries
        }
    }

    private suspend fun inspectArchiveForDiagnostics(
        item: ReadableItem,
    ) {
        val uri = storedDocumentUriFor(item) ?: return
        val firstBytes = readFirstBytes(uri, byteCount = 12)
        val resolvedArchive = resolveArchiveCodecAndEntries(item = item, uri = uri) ?: return
        archiveFormatByItemId[item.id] = resolvedArchive.codec.format
        archiveDiagnosticsByItemId[item.id] = DownloadedComicArchiveDiagnostic(
            fileBytes = uri.fileLengthOrNull(),
            firstBytesHex = firstBytes?.toHexString(),
            allEntryCount = resolvedArchive.allEntries.size,
            imageEntryCount = resolvedArchive.imageEntries.size,
            firstEntryNames = resolvedArchive.allEntries.take(5),
        )
    }

    private suspend fun resolveArchiveCodecAndEntries(
        item: ReadableItem,
        uri: Uri,
    ): ResolvedComicArchive? {
        val codecs = prioritizedCodecs(item)
        var firstResolvedArchive: ResolvedComicArchive? = null
        codecs.forEach { codec ->
            val allEntries = codec.listEntries(
                context = context,
                uri = uri,
            )
            val imageEntries = allEntries.filter(String::isImageEntryName).sortedWith(NaturalPageOrderComparator)
            val resolvedArchive = ResolvedComicArchive(
                codec = codec,
                allEntries = allEntries,
                imageEntries = imageEntries,
            )
            if (firstResolvedArchive == null) {
                firstResolvedArchive = resolvedArchive
            }
            if (imageEntries.isNotEmpty()) {
                return resolvedArchive
            }
        }
        return firstResolvedArchive
    }

    private fun prioritizedCodecs(
        item: ReadableItem,
    ): List<ComicArchiveCodec> {
        val preferredFormat = archiveFormatByItemId[item.id] ?: item.format
        val preferredCodec = comicArchiveCodecs.firstOrNull { codec -> codec.format == preferredFormat }
        return listOfNotNull(preferredCodec) + comicArchiveCodecs.filterNot { codec -> codec === preferredCodec }
    }

    private suspend fun readFirstBytes(
        uri: Uri,
        byteCount: Int,
    ): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(byteCount)
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) {
                    ByteArray(0)
                } else {
                    buffer.copyOf(bytesRead)
                }
            }
        }.getOrNull()
    }

    private fun Uri.fileLengthOrNull(): Long? {
        if (scheme != "file") return null
        return path?.let(::File)?.takeIf(File::isFile)?.length()
    }

    private fun localRecordForCachedEntries(
        item: ReadableItem,
        entryNames: List<String>,
    ): LocalDocumentRecord? {
        val uri = storedDocumentUriFor(item) ?: return null
        if (entryNames.isEmpty()) return null
        return LocalDocumentRecord(
            id = item.id,
            uri = uri.toString(),
            displayName = item.title,
            title = item.title,
            format = archiveFormatByItemId[item.id] ?: item.format,
            pageCount = entryNames.size,
            pageEntryNames = entryNames,
            coverUri = item.coverUrl,
            author = item.bookMetadata?.author,
            series = item.comicMetadata?.series,
            issueTitle = item.comicMetadata?.issueTitle,
            volume = item.comicMetadata?.volume,
            issueNumber = item.comicMetadata?.issueNumber,
            addedAtEpochMillis = item.addedAtEpochMillis ?: System.currentTimeMillis(),
        )
    }

    private fun storedDocumentUriFor(
        item: ReadableItem,
    ): Uri? = remoteDownloadDataSources.downloadDataSourceFor(item)
        ?.storedDocumentUri(item)

    private fun invalidateItemCaches(
        itemId: String,
    ) {
        RarComicArchiveCodec.clearLocalArchiveCache(context)
        entryCacheCoordinator.invalidate(itemId)
        archiveDiagnosticsByItemId.remove(itemId)
        archiveFormatByItemId.remove(itemId)
        comicPageLoader.clear(itemId)
    }
}

@androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
internal fun downloadedRemoteComicArchiveCodecFormats(
    context: Context,
): List<FormatType> = defaultDownloadedRemoteComicArchiveCodecs(context).map(ComicArchiveCodec::format)

private fun defaultDownloadedRemoteComicArchiveCodecs(
    context: Context,
): List<ComicArchiveCodec> = listOf(
    ZipComicArchiveCodec,
    RarComicArchiveCodec(context),
)

private data class ResolvedComicArchive(
    val codec: ComicArchiveCodec,
    val allEntries: List<String>,
    val imageEntries: List<String>,
)

private data class DownloadedComicArchiveDiagnostic(
    val fileBytes: Long?,
    val firstBytesHex: String?,
    val allEntryCount: Int,
    val imageEntryCount: Int,
    val firstEntryNames: List<String>,
)

private fun ByteArray.toHexString(): String = joinToString(separator = " ") { byte ->
    (byte.toInt() and 0xFF).toString(radix = 16).padStart(2, '0')
}

private fun List<String>.toComicPageSpecs(
    item: ReadableItem,
): List<ComicPageSpec> = mapIndexed { index, entryName ->
    ComicPageSpec(
        id = entryName,
        pageIndex = index,
        contentDescription = "${item.title} page ${index + 1}",
    )
}
