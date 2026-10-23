package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.ImportedComicArchiveEntity
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageMetadata
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewOptions
import com.example.unifiedreader.feature.reader.comics.ComicPagePreviewResult
import com.example.unifiedreader.feature.reader.comics.ComicPagePrefetchWindow
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray

interface LocalDocumentDataSource {
    val isHydratedFlow: StateFlow<Boolean>

    val importedItemsFlow: StateFlow<List<ReadableItem>>

    fun getImportedItems(): List<ReadableItem>

    suspend fun import(uri: android.net.Uri): ReadableItem?

    suspend fun removeById(itemId: String): Boolean

    fun getPageCount(item: ReadableItem): Int

    fun getDocumentUri(item: ReadableItem): android.net.Uri?
}

interface LocalDocumentHydrator {
    suspend fun hydrate()
}

interface LocalComicDocumentDataSource {
    fun getPages(item: ReadableItem): List<ComicPageSpec>

    suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata?

    suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult

    suspend fun loadPagePreview(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPagePreviewResult = ComicPagePreviewResult.Unavailable

    suspend fun loadPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? = null

    suspend fun loadCachedPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions,
    ): ComicPageMetadata? = null

    fun observePagePreviewVersion(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
    ): Flow<Long> = flowOf(0L)

    fun prefetchPagePreviews(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions = ComicPagePreviewOptions(),
        priorityPageIndex: Int? = null,
    ) = Unit

    fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    )

    fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
        window: ComicPagePrefetchWindow,
    ) {
        prefetchAround(
            item = item,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
        )
    }

    fun clearReaderMemoryCaches() = Unit

    fun trimReaderMemoryCaches(
        request: ReaderMemoryTrimRequest,
    ) {
        if (request.mode == ReaderMemoryTrimMode.ClearAll) {
            clearReaderMemoryCaches()
        }
    }
}

internal const val LOCAL_LIBRARY_ID = "local-imports"
internal const val IMPORTED_EPUB_MIMETYPE = "application/epub+zip"
internal const val IMPORTED_PDF_MIMETYPE = "application/pdf"
internal val PDF_SIGNATURE = "%PDF-".encodeToByteArray()

internal data class LocalDocumentRecord(
    val id: String,
    val uri: String,
    val displayName: String,
    val title: String,
    val format: FormatType,
    val pageCount: Int,
    val pageEntryNames: List<String>,
    val coverFileName: String? = null,
    val coverUri: String? = null,
    val author: String? = null,
    val series: String? = null,
    val issueTitle: String? = null,
    val volume: String? = null,
    val issueNumber: String? = null,
    val epubCoverEntryName: String? = null,
    val addedAtEpochMillis: Long = System.currentTimeMillis(),
    // The file's own description (EPUB `dc:description`, ComicInfo `Summary`), as written.
    val summary: String? = null,
)

internal fun LocalDocumentRecord.toEntity(): ImportedComicArchiveEntity = ImportedComicArchiveEntity(
    itemId = id,
    documentUri = uri,
    displayName = displayName,
    title = title,
    coverFileName = coverFileName,
    format = format.name,
    pageCount = pageCount,
    pageEntryNamesJson = JSONArray(pageEntryNames).toString(),
    author = author,
    series = series,
    issueTitle = issueTitle,
    volume = volume,
    issueNumber = issueNumber,
    updatedAtEpochMillis = addedAtEpochMillis,
    summary = summary,
)

internal fun recordToReadableItem(
    record: LocalDocumentRecord,
): ReadableItem {
    val formatSpec = record.format.localImportFormatSpec()
    return ReadableItem(
        id = record.id,
        sourceType = formatSpec.sourceType,
        libraryId = formatSpec.libraryId,
        isLocalImport = true,
        title = record.title,
        format = record.format,
        pageCount = record.pageCount.takeIf { it > 0 },
        description = record.summary?.takeIf(String::isNotBlank),
        coverUrl = record.coverUri,
        addedAtEpochMillis = record.addedAtEpochMillis,
        // For an EPUB `author` is its creator; for a comic archive it is ComicInfo's Writer list.
        bookMetadata = record.author?.takeUnless { record.format.isComicArchive }?.let(::BookMetadata),
        comicMetadata = record.comicWriters().let { writers ->
            if (
                record.series != null ||
                record.issueTitle != null ||
                record.volume != null ||
                record.issueNumber != null ||
                writers.isNotEmpty()
            ) {
                ComicMetadata(
                    series = record.series,
                    issueTitle = record.issueTitle,
                    volume = record.volume,
                    issueNumber = record.issueNumber,
                    writers = writers,
                )
            } else {
                null
            }
        },
    )
}

private val FormatType.isComicArchive: Boolean
    get() = this == FormatType.CBZ || this == FormatType.CBR

private fun LocalDocumentRecord.comicWriters(): List<String> =
    if (format.isComicArchive) {
        author.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
    } else {
        emptyList()
    }

internal fun entityToRecord(
    entity: ImportedComicArchiveEntity,
    coverUri: String? = null,
): LocalDocumentRecord {
    val format = entity.format.toFormatTypeOrNull() ?: FormatType.CBZ
    return LocalDocumentRecord(
        id = entity.itemId,
        uri = entity.documentUri,
        displayName = entity.displayName,
        title = entity.title.ifBlank {
            format.localImportTitleFallback(entity.displayName)
        },
        format = format,
        pageCount = entity.pageCount,
        pageEntryNames = JSONArray(entity.pageEntryNamesJson).let { array ->
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    add(array.optString(index))
                }
            }.filter(String::isNotEmpty)
        },
        coverFileName = entity.coverFileName,
        coverUri = coverUri,
        author = entity.author,
        series = entity.series,
        issueTitle = entity.issueTitle,
        volume = entity.volume,
        issueNumber = entity.issueNumber,
        addedAtEpochMillis = entity.updatedAtEpochMillis,
        summary = entity.summary,
    )
}

internal fun LocalDocumentRecord.isSupportedLocalFormat(): Boolean = when (format) {
    FormatType.CBZ,
    FormatType.CBR,
    FormatType.EPUB,
    FormatType.PDF -> true
}

internal object NaturalPageOrderComparator : Comparator<String> {
    override fun compare(left: String, right: String): Int {
        val leftTokens = left.naturalSortTokens()
        val rightTokens = right.naturalSortTokens()
        val sharedSize = minOf(leftTokens.size, rightTokens.size)
        for (index in 0 until sharedSize) {
            val result = leftTokens[index].compareTo(rightTokens[index])
            if (result != 0) return result
        }
        return leftTokens.size.compareTo(rightTokens.size)
    }
}

private sealed interface NaturalSortToken : Comparable<NaturalSortToken> {
    data class Text(val value: String) : NaturalSortToken {
        override fun compareTo(other: NaturalSortToken): Int = when (other) {
            is Text -> value.compareTo(other.value)
            is Number -> 1
        }
    }

    data class Number(val value: Long) : NaturalSortToken {
        override fun compareTo(other: NaturalSortToken): Int = when (other) {
            is Text -> -1
            is Number -> value.compareTo(other.value)
        }
    }
}

private fun String.naturalSortTokens(): List<NaturalSortToken> {
    val tokens = mutableListOf<NaturalSortToken>()
    val pattern = Regex("\\d+|\\D+")
    pattern.findAll(lowercase()).forEach { match ->
        val token = match.value
        tokens += token.toLongOrNull()?.let(NaturalSortToken::Number)
            ?: NaturalSortToken.Text(token)
    }
    return tokens
}

// The union of what Kavita (png, jpeg, jpg, webp, gif, avif) and Grimmory (adds bmp, heic) count as
// pages; BitmapFactory decodes all of them (AVIF from Android 12).
private val comicPageImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif")

/**
 * Whether an archive entry is a comic page. Like Kavita and Grimmory, it skips `__MACOSX/` folders and
 * dot-files, such as the `._` AppleDouble files macOS adds next to each image.
 */
internal fun String.isImageEntryName(): Boolean {
    val segments = replace('\\', '/').split('/').filter(String::isNotEmpty)
    val fileName = segments.lastOrNull() ?: return false
    if (fileName.startsWith('.')) return false
    if (segments.any { segment -> segment.equals("__MACOSX", ignoreCase = true) }) return false
    return fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase() in comicPageImageExtensions
}

internal fun String.comicArchiveTitle(): String = removeSuffixIgnoreCase(".cbz")
    .removeSuffixIgnoreCase(".cbr")

internal data class LocalImportFormatSpec(
    val idPrefix: String,
    val libraryId: String,
    val sourceType: SourceType,
)

private val comicLocalImportFormatSpec = LocalImportFormatSpec(
    idPrefix = "comic",
    libraryId = LOCAL_LIBRARY_ID,
    sourceType = SourceType.LOCAL,
)

private val epubLocalImportFormatSpec = LocalImportFormatSpec(
    idPrefix = "document",
    libraryId = LOCAL_LIBRARY_ID,
    sourceType = SourceType.LOCAL,
)

private val pdfLocalImportFormatSpec = LocalImportFormatSpec(
    idPrefix = "document",
    libraryId = LOCAL_LIBRARY_ID,
    sourceType = SourceType.LOCAL,
)

internal fun FormatType.localImportFormatSpec(): LocalImportFormatSpec = when (this) {
    FormatType.CBZ,
    FormatType.CBR -> comicLocalImportFormatSpec
    FormatType.EPUB -> epubLocalImportFormatSpec
    FormatType.PDF -> pdfLocalImportFormatSpec
}

internal fun FormatType.localImportTitleFallback(
    displayName: String,
): String = when (this) {
    FormatType.CBZ,
    FormatType.CBR -> displayName.comicArchiveTitle()
    FormatType.EPUB -> displayName.removeSuffixIgnoreCase(".epub")
    FormatType.PDF -> displayName.removeSuffixIgnoreCase(".pdf")
}

internal fun isEpubDocument(
    displayName: String,
    contentType: String?,
): Boolean = displayName.endsWith(".epub", ignoreCase = true) ||
    (contentType?.equals(IMPORTED_EPUB_MIMETYPE, ignoreCase = true) == true)

internal fun isPdfDocument(
    displayName: String,
    contentType: String?,
): Boolean = displayName.endsWith(".pdf", ignoreCase = true) ||
    (contentType?.equals(IMPORTED_PDF_MIMETYPE, ignoreCase = true) == true)

internal fun String.removeSuffixIgnoreCase(
    suffix: String,
): String = if (endsWith(suffix, ignoreCase = true)) {
    dropLast(suffix.length)
} else {
    this
}

internal fun String.toFormatTypeOrNull(): FormatType? = runCatching(FormatType::valueOf).getOrNull()
