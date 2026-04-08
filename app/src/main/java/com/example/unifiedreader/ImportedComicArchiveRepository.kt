package com.example.unifiedreader

import android.content.Intent
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.entity.ImportedComicArchiveEntity
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

interface LocalDocumentDataSource {
    val isHydratedFlow: StateFlow<Boolean>

    val importedItemsFlow: StateFlow<List<ReadableItem>>

    fun getImportedItems(): List<ReadableItem>

    suspend fun import(uri: Uri): ReadableItem?

    suspend fun removeById(itemId: String): Boolean

    fun getPageCount(item: ReadableItem): Int

    fun getDocumentUri(item: ReadableItem): Uri?
}

class LocalDocumentRepository(
    private val context: Context,
    private val importedComicArchiveDao: ImportedComicArchiveDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : LocalDocumentDataSource {
    private val coverWarmupSemaphore = Semaphore(permits = 3)
    private val coverMutationMutex = Mutex()
    private val comicArchiveCodecs: List<ComicArchiveCodec> = listOf(
        ZipComicArchiveCodec,
        RarComicArchiveCodec(context),
    )
    private val recordsById = MutableStateFlow<Map<String, LocalDocumentRecord>>(emptyMap())
    private val importedItemsState = MutableStateFlow<List<ReadableItem>>(emptyList())
    private val hydrationState = MutableStateFlow(false)
    private val pageBitmapCache = LruCache<String, ComicPageLoadResult.Success>(12)
    private val prefetchInFlight = mutableSetOf<String>()
    private val prefetchLock = Any()
    override val isHydratedFlow: StateFlow<Boolean> = hydrationState.asStateFlow()
    override val importedItemsFlow: StateFlow<List<ReadableItem>> = importedItemsState.asStateFlow()

    init {
        scope.launch {
            val persistedRecords = importedComicArchiveDao.getAll()
                .map(::entityToRecord)
                .filter(LocalDocumentRecord::isSupportedLocalFormat)
            recordsById.update { existingRecords ->
                buildMap {
                    persistedRecords.forEach { put(it.id, it) }
                    existingRecords.values.forEach { put(it.id, it) }
                }
            }
            publishRecords(recordsById.value.values)
            hydrationState.value = true
            warmImportedCovers(persistedRecords.map(LocalDocumentRecord::id))
        }
    }

    override fun getImportedItems(): List<ReadableItem> = importedItemsFlow.value

    override suspend fun import(uri: Uri): ReadableItem? {
        val persistedUri = uri.toString()
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Imported Document"
        val contentType = queryContentType(uri)
        val record = resolveImportedRecord(
            displayName = displayName,
            contentType = contentType,
            uri = uri,
            persistedUri = persistedUri,
        ) ?: return null
        val existingRecord = recordsById.value[record.id]
        val recordWithAddedAt = record.copy(
            addedAtEpochMillis = existingRecord?.addedAtEpochMillis ?: record.addedAtEpochMillis,
        )
        val recordWithCover = coverMutationMutex.withLock {
            val refreshedRecord = attachLocalCover(recordWithAddedAt, forceRefresh = true)
            withContext(Dispatchers.IO) {
                importedComicArchiveDao.insertOrReplace(refreshedRecord.toEntity())
            }
            refreshedRecord
        }
        publishRecords(recordsById.value.values.plus(recordWithCover).distinctBy(LocalDocumentRecord::id))
        if (recordWithCover.coverUri == null) {
            warmImportedCovers(listOf(recordWithCover.id))
        }
        return recordToReadableItem(recordWithCover)
    }

    override suspend fun removeById(itemId: String): Boolean {
        val record = recordsById.value[itemId] ?: return false
        withContext(Dispatchers.IO) {
            importedComicArchiveDao.deleteByItemId(itemId)
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(record.uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        clearCachedPages(itemId)
        clearCachedCover(itemId)
        publishRecords(recordsById.value.values.filterNot { it.id == itemId })
        return true
    }

    internal fun findRecord(item: ReadableItem): LocalDocumentRecord? = recordsById.value[item.id]

    override fun getPageCount(item: ReadableItem): Int = findRecord(item)?.pageCount ?: 0

    override fun getDocumentUri(item: ReadableItem): Uri? = findRecord(item)?.uri?.let(Uri::parse)

    suspend fun loadPageImage(
        item: ReadableItem,
        entryName: String,
    ): ComicPageLoadResult {
        val record = findRecord(item)
            ?: return ComicPageLoadResult.Failure("Unable to find imported archive.")
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format }
            ?: return ComicPageLoadResult.Failure("Unsupported archive format.")
        pageBitmapCache[pageCacheKey(item.id, entryName)]?.let { return it }
        val loadedResult = decodePageImage(
            item = item,
            record = record,
            codec = codec,
            entryName = entryName,
        )
        if (loadedResult is ComicPageLoadResult.Success) {
            pageBitmapCache.put(pageCacheKey(item.id, entryName), loadedResult)
        }
        return loadedResult
    }

    fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        val record = findRecord(item) ?: return
        if (pageCount <= 1) return
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format } ?: return
        val candidateIndexes = buildList {
            for (offset in 1..4) {
                add(settledPageIndex + offset)
            }
            for (offset in 1..4) {
                add(settledPageIndex - offset)
            }
        }.filter { it in 0 until minOf(pageCount, record.pageEntryNames.size) }

        candidateIndexes.forEach { pageIndex ->
            val entryName = record.pageEntryNames[pageIndex]
            val cacheKey = pageCacheKey(item.id, entryName)
            if (pageBitmapCache.get(cacheKey) != null) {
                return@forEach
            }
            val shouldLaunch = synchronized(prefetchLock) {
                prefetchInFlight.add(cacheKey)
            }
            if (!shouldLaunch) {
                return@forEach
            }
            scope.launch {
                try {
                    val result = decodePageImage(
                        item = item,
                        record = record,
                        codec = codec,
                        entryName = entryName,
                    )
                    if (result is ComicPageLoadResult.Success) {
                        pageBitmapCache.put(cacheKey, result)
                    }
                } finally {
                    synchronized(prefetchLock) {
                        prefetchInFlight.remove(cacheKey)
                    }
                }
            }
        }
    }

    private suspend fun decodePageImage(
        item: ReadableItem,
        record: LocalDocumentRecord,
        codec: ComicArchiveCodec,
        entryName: String,
    ): ComicPageLoadResult {
        return runCatching {
            val image = codec.decodePageImage(
                context = context,
                uri = Uri.parse(record.uri),
                entryName = entryName,
            )
            if (image != null) {
                ComicPageLoadResult.Success(image)
            } else {
                ComicPageLoadResult.Failure("Unable to decode this page.")
            }
        }.getOrElse { throwable ->
            Log.w(LOG_TAG, "Failed to decode page $entryName for ${record.displayName}", throwable)
            ComicPageLoadResult.Failure("Unable to decode this page.")
        }
    }

    private fun pageCacheKey(
        itemId: String,
        entryName: String,
    ): String = "$itemId::$entryName"

    private fun clearCachedPages(
        itemId: String,
    ) {
        pageBitmapCache.snapshot().keys
            .filter { it.startsWith("$itemId::") }
            .forEach(pageBitmapCache::remove)
        synchronized(prefetchLock) {
            prefetchInFlight.removeAll { it.startsWith("$itemId::") }
        }
    }

    private suspend fun resolveCodec(
        displayName: String,
        uri: Uri,
    ): ResolvedComicArchive? {
        val prioritizedCodecs = comicArchiveCodecs.sortedByDescending { codec ->
            if (codec.matchesFileName(displayName)) 1 else 0
        }
        for (codec in prioritizedCodecs) {
            val resolvedArchive = runCatching {
                val entryNames = codec.listEntries(context, uri)
                val pageEntryNames = entryNames.filter(String::isImageEntryName)
                    .sortedWith(NaturalPageOrderComparator)
                if (pageEntryNames.isNotEmpty()) {
                    ResolvedComicArchive(
                        codec = codec,
                        pageEntryNames = pageEntryNames,
                        comicMetadata = entryNames
                            .firstOrNull(::isComicInfoEntryName)
                            ?.let { metadataEntryName ->
                                codec.readEntryBytes(
                                    context = context,
                                    uri = uri,
                                    entryName = metadataEntryName,
                                )
                            }
                            ?.let(::parseComicInfoMetadata),
                    )
                } else {
                    null
                }
            }.onFailure { throwable ->
                Log.w(LOG_TAG, "Failed to probe ${codec.format.name} archive for $displayName", throwable)
            }.getOrNull()
            if (resolvedArchive != null) {
                return resolvedArchive
            }
        }
        return null
    }

    private data class ResolvedComicArchive(
        val codec: ComicArchiveCodec,
        val pageEntryNames: List<String>,
        val comicMetadata: ImportedComicMetadata? = null,
    )

    private suspend fun resolveImportedRecord(
        displayName: String,
        contentType: String?,
        uri: Uri,
        persistedUri: String,
    ): LocalDocumentRecord? {
        if (isEpubDocument(displayName, contentType) && isValidEpubDocument(uri)) {
            val metadata = extractEpubMetadata(
                context = context,
                uri = uri,
            )
            return LocalDocumentRecord(
                id = buildItemId(uri, FormatType.EPUB),
                uri = persistedUri,
                displayName = displayName,
                title = metadata?.title?.takeIf(String::isNotBlank)
                    ?: displayName.removeSuffixIgnoreCase(".epub"),
                format = FormatType.EPUB,
                pageCount = 0,
                pageEntryNames = emptyList(),
                author = metadata?.author,
                epubCoverEntryName = metadata?.coverEntryName,
            )
        }
        val archive = resolveCodec(displayName, uri)
        if (archive != null) {
            val pageEntryNames = archive.pageEntryNames
            val pageCount = pageEntryNames.size
            if (pageCount <= 0) return null
            return LocalDocumentRecord(
                id = buildItemId(uri, archive.codec.format),
                uri = persistedUri,
                displayName = displayName,
                title = displayName.comicArchiveTitle(),
                format = archive.codec.format,
                pageCount = pageCount,
                pageEntryNames = pageEntryNames,
                series = archive.comicMetadata?.series,
                issueTitle = archive.comicMetadata?.issueTitle,
                volume = archive.comicMetadata?.volume,
                issueNumber = archive.comicMetadata?.issueNumber,
            )
        }
        return null
    }

    private fun isValidEpubDocument(
        uri: Uri,
    ): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var hasContainerXml = false
                var hasOpf = false
                var mimetypeMatches = false
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        "mimetype" -> {
                            val value = zip.readBytes().decodeToString().trim()
                            mimetypeMatches = value == IMPORTED_EPUB_MIMETYPE
                        }

                        "META-INF/container.xml" -> hasContainerXml = true
                        else -> if (entry.name.endsWith(".opf", ignoreCase = true)) {
                            hasOpf = true
                        }
                    }
                    zip.closeEntry()
                }
                mimetypeMatches && hasContainerXml && hasOpf
            }
        } ?: false
    }.getOrElse { throwable ->
        Log.w(LOG_TAG, "Failed to validate EPUB import for $uri", throwable)
        false
    }

    private fun recordToReadableItem(
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
            description = formatSpec.buildDescription(record),
            coverUrl = record.coverUri,
            addedAtEpochMillis = record.addedAtEpochMillis,
            bookMetadata = record.author?.let(::BookMetadata),
            comicMetadata = if (
                record.series != null ||
                record.issueTitle != null ||
                record.volume != null ||
                record.issueNumber != null
            ) {
                ComicMetadata(
                    series = record.series,
                    issueTitle = record.issueTitle,
                    volume = record.volume,
                    issueNumber = record.issueNumber,
                )
            } else {
                null
            },
        )
    }

    private fun entityToRecord(
        entity: ImportedComicArchiveEntity,
    ): LocalDocumentRecord {
        val format = entity.format.toFormatTypeOrNull() ?: FormatType.CBZ
        val coverUri = entity.coverFileName
            ?.takeIf { coverFile(it).isValidCoverFileFor(entity.itemId) }
            ?.let(::coverUriForFileName)
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
        )
    }

    private suspend fun attachLocalCover(
        record: LocalDocumentRecord,
        forceRefresh: Boolean = false,
    ): LocalDocumentRecord {
        val existingCoverFileName = existingCoverFileName(record)
        val coverFileName = runCatching {
            when (record.format) {
                FormatType.CBZ,
                FormatType.CBR -> cacheComicCover(record, forceRefresh)
                FormatType.EPUB -> cacheEpubCover(record, forceRefresh)
                FormatType.PDF -> null
            }
        }.getOrNull() ?: existingCoverFileName
        return record.copy(
            coverFileName = coverFileName,
            coverUri = coverFileName?.let(::coverUriForFileName),
        )
    }

    private suspend fun cacheComicCover(
        record: LocalDocumentRecord,
        forceRefresh: Boolean,
    ): String? {
        if (!forceRefresh) {
            existingCoverFileName(record)?.let { return it }
        }
        val coverEntryName = record.pageEntryNames.firstOrNull() ?: return null
        val codec = comicArchiveCodecs.firstOrNull { it.format == record.format } ?: return null
        val bytes = codec.readEntryBytes(
            context = context,
            uri = Uri.parse(record.uri),
            entryName = coverEntryName,
        ) ?: return null
        val extension = coverEntryName.substringAfterLast('.', "jpg")
        return cacheCoverBytes(record.id, extension, bytes)
    }

    private suspend fun cacheEpubCover(
        record: LocalDocumentRecord,
        forceRefresh: Boolean,
    ): String? {
        if (!forceRefresh) {
            existingCoverFileName(record)?.let { return it }
        }
        val coverEntryName = record.epubCoverEntryName ?: extractEpubMetadata(
            context = context,
            uri = Uri.parse(record.uri),
        )?.coverEntryName ?: return null
        val bytes = readZipEntryBytes(
            context = context,
            uri = Uri.parse(record.uri),
            entryName = coverEntryName,
        ) ?: return null
        val extension = coverEntryName.substringAfterLast('.', "jpg")
        return cacheCoverBytes(record.id, extension, bytes)
    }

    private fun existingCoverFileName(
        record: LocalDocumentRecord,
    ): String? = record.coverFileName
        ?.takeIf { coverFile(it).isValidCoverFileFor(record.id) }
        ?: coverStorageDirectory()
            .listFiles()
            ?.firstOrNull { it.isValidCoverFileFor(record.id) }
            ?.name

    private fun cacheCoverBytes(
        itemId: String,
        extension: String,
        bytes: ByteArray,
    ): String? = runCatching {
        val normalizedExtension = extension.lowercase().ifBlank { "jpg" }
        val coverDirectory = coverStorageDirectory()
        val coverFile = File(coverDirectory, "$itemId.${System.nanoTime()}.$normalizedExtension")
        val tempFile = File(coverDirectory, "${coverFile.name}.tmp")
        tempFile.writeBytes(bytes)
        if (tempFile.length() <= 0L) {
            tempFile.delete()
            return@runCatching null
        }
        val moved = tempFile.renameTo(coverFile) || runCatching {
            tempFile.copyTo(coverFile, overwrite = false)
            tempFile.delete()
            true
        }.getOrDefault(false)
        if (!moved || coverFile.length() <= 0L) {
            tempFile.delete()
            return@runCatching null
        }
        coverDirectory.listFiles()
            ?.filter { it.isValidCoverFileFor(itemId) && it.name != coverFile.name }
            ?.forEach(File::delete)
        coverFile.name
    }.getOrNull()

    private fun coverStorageDirectory(): File = File(context.filesDir, "imported-cover-cache").apply {
        mkdirs()
    }

    private fun coverFile(
        coverFileName: String,
    ): File = File(coverStorageDirectory(), coverFileName)

    private fun coverUriForFileName(
        coverFileName: String,
    ): String = Uri.fromFile(coverFile(coverFileName)).toString()

    private fun clearCachedCover(
        itemId: String,
    ) {
        coverStorageDirectory()
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("$itemId.") }
            ?.forEach(File::delete)
    }

    private fun warmImportedCovers(
        itemIds: List<String>,
    ) {
        itemIds.distinct().forEach { itemId ->
            scope.launch {
                coverWarmupSemaphore.withPermit {
                    coverMutationMutex.withLock {
                        val currentRecord = recordsById.value[itemId] ?: return@launch
                        if (currentRecord.coverUri != null) return@launch
                        val updatedRecord = attachLocalCover(currentRecord)
                        if (updatedRecord.coverUri == currentRecord.coverUri) return@launch
                        if (recordsById.value[itemId] != currentRecord) return@launch
                        persistRecord(updatedRecord)
                        recordsById.update { existingRecords ->
                            existingRecords + (itemId to updatedRecord)
                        }
                        publishRecords(recordsById.value.values)
                    }
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = context.contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME)
            }
    }

    private fun queryContentType(uri: Uri): String? = context.contentResolver.getType(uri)

    private fun buildItemId(
        uri: Uri,
        format: FormatType,
    ): String = "${format.localImportFormatSpec().idPrefix}-${uri.toString().hashCode().toUInt().toString(16)}"

    private fun publishRecords(
        records: Collection<LocalDocumentRecord>,
    ) {
        val sortedRecords = records.sortedBy { record ->
            record.title.ifBlank { record.displayName }.lowercase()
        }
        recordsById.value = sortedRecords.associateBy(LocalDocumentRecord::id)
        importedItemsState.value = sortedRecords.map(::recordToReadableItem)
    }

    private suspend fun persistRecord(
        record: LocalDocumentRecord,
    ) {
        withContext(Dispatchers.IO) {
            importedComicArchiveDao.insertOrReplace(record.toEntity())
        }
    }

    private companion object {
        const val LOG_TAG = "ImportedComicArchive"
    }
}

private fun File.isValidCoverFileFor(
    itemId: String,
): Boolean = isFile &&
    name.startsWith("$itemId.") &&
    !name.endsWith(".tmp") &&
    length() > 0L

internal const val LOCAL_LIBRARY_ID = "local-imports"
internal const val IMPORTED_EPUB_MIMETYPE = "application/epub+zip"

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
)

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
)

private fun LocalDocumentRecord.isSupportedLocalFormat(): Boolean = when (format) {
    FormatType.CBZ,
    FormatType.CBR,
    FormatType.EPUB -> true
    FormatType.PDF -> false
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

internal fun String.isImageEntryName(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".png") ||
        lower.endsWith(".webp")
}

internal fun String.comicArchiveTitle(): String = removeSuffixIgnoreCase(".cbz")
    .removeSuffixIgnoreCase(".cbr")

private data class LocalImportFormatSpec(
    val idPrefix: String,
    val libraryId: String,
    val sourceType: SourceType,
    val buildDescription: (LocalDocumentRecord) -> String,
)

private val ComicLocalImportFormatSpec = LocalImportFormatSpec(
    idPrefix = "comic",
    libraryId = LOCAL_LIBRARY_ID,
    sourceType = SourceType.LOCAL,
    buildDescription = { record -> "Imported local ${record.format.name} with ${record.pageCount} pages." },
)

private val EpubLocalImportFormatSpec = LocalImportFormatSpec(
    idPrefix = "document",
    libraryId = LOCAL_LIBRARY_ID,
    sourceType = SourceType.LOCAL,
    buildDescription = { "Imported local EPUB book." },
)

private fun FormatType.localImportFormatSpec(): LocalImportFormatSpec = when (this) {
    FormatType.CBZ,
    FormatType.CBR -> ComicLocalImportFormatSpec
    FormatType.EPUB -> EpubLocalImportFormatSpec
    FormatType.PDF -> error("PDF is not a supported local import format.")
}

internal fun FormatType.localImportTitleFallback(
    displayName: String,
): String = when (this) {
    FormatType.CBZ,
    FormatType.CBR -> displayName.comicArchiveTitle()
    FormatType.EPUB -> displayName.removeSuffixIgnoreCase(".epub")
    FormatType.PDF -> displayName
}

private fun isEpubDocument(
    displayName: String,
    contentType: String?,
): Boolean = displayName.endsWith(".epub", ignoreCase = true) ||
    (contentType?.equals(IMPORTED_EPUB_MIMETYPE, ignoreCase = true) == true)

private fun String.removeSuffixIgnoreCase(
    suffix: String,
): String = if (endsWith(suffix, ignoreCase = true)) {
    dropLast(suffix.length)
} else {
    this
}

internal fun String.toFormatTypeOrNull(): FormatType? = runCatching(FormatType::valueOf).getOrNull()

private data class ImportedComicMetadata(
    val series: String? = null,
    val issueTitle: String? = null,
    val volume: String? = null,
    val issueNumber: String? = null,
)

private data class ImportedEpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val coverEntryName: String? = null,
)

private suspend fun extractEpubMetadata(
    context: Context,
    uri: Uri,
): ImportedEpubMetadata? {
    val containerBytes = readZipEntryBytes(
        context = context,
        uri = uri,
        entryName = "META-INF/container.xml",
    ) ?: return null
    val containerDocument = parseXmlDocument(containerBytes) ?: return null
    val rootFilePath = containerDocument.firstElementByLocalName("rootfile")
        ?.getAttribute("full-path")
        ?.trim()
        .orEmpty()
    if (rootFilePath.isEmpty()) return null
    val packageBytes = readZipEntryBytes(
        context = context,
        uri = uri,
        entryName = rootFilePath,
    ) ?: return null
    val packageDocument = parseXmlDocument(packageBytes) ?: return null
    return ImportedEpubMetadata(
        title = packageDocument.firstTextByLocalName("title"),
        author = packageDocument.firstTextByLocalName("creator"),
        coverEntryName = packageDocument.resolveEpubCoverEntryPath(rootFilePath),
    )
}

private suspend fun readZipEntryBytes(
    context: Context,
    uri: Uri,
    entryName: String,
): ByteArray? = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == entryName) {
                    return@withContext zip.readBytes()
                }
                zip.closeEntry()
            }
            null
        }
    }
}

private fun isComicInfoEntryName(
    entryName: String,
): Boolean = entryName.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)

private fun parseComicInfoMetadata(
    bytes: ByteArray,
): ImportedComicMetadata? {
    val document = parseXmlDocument(bytes) ?: return null
    return ImportedComicMetadata(
        series = document.firstTextByLocalName("Series"),
        issueTitle = document.firstTextByLocalName("Title"),
        volume = document.firstTextByLocalName("Volume"),
        issueNumber = document.firstTextByLocalName("Number"),
    )
}

private fun parseXmlDocument(
    bytes: ByteArray,
): Document? = runCatching {
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).also {
        it.documentElement?.normalize()
    }
}.getOrNull()

private fun Document.firstElementByLocalName(
    localName: String,
): Element? {
    val nodes = getElementsByTagNameNS("*", localName)
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        return element
    }
    return null
}

private fun Document.firstTextByLocalName(
    localName: String,
): String? {
    val nodes = getElementsByTagNameNS("*", localName)
    for (index in 0 until nodes.length) {
        val text = nodes.item(index)?.textContent?.trim().orEmpty()
        if (text.isNotEmpty()) return text
    }
    return null
}

private fun Document.resolveEpubCoverEntryPath(
    packagePath: String,
): String? {
    val manifestItems = manifestItemElements()
    val coverId = metaCoverId()
    val href = when {
        !coverId.isNullOrBlank() -> manifestItems.firstOrNull { it.getAttribute("id") == coverId }
            ?.getAttribute("href")
        else -> manifestItems.firstOrNull { item ->
            item.getAttribute("properties")
                .split(Regex("\\s+"))
                .any { it == "cover-image" }
        }?.getAttribute("href")
    }?.trim().orEmpty()
    return href.takeIf(String::isNotEmpty)?.resolveRelativeToPackagePath(packagePath)
}

private fun Document.metaCoverId(): String? {
    val nodes = getElementsByTagNameNS("*", "meta")
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        if (element.getAttribute("name").equals("cover", ignoreCase = true)) {
            return element.getAttribute("content").trim().takeIf(String::isNotEmpty)
        }
    }
    return null
}

private fun Document.manifestItemElements(): List<Element> {
    val manifest = firstElementByLocalName("manifest") ?: return emptyList()
    val children = manifest.childNodes
    return buildList {
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (element.localName == "item") {
                add(element)
            }
        }
    }
}

private fun String.resolveRelativeToPackagePath(
    packagePath: String,
): String = java.net.URI(packagePath).resolve(this).path

private fun Cursor.getStringOrNull(
    columnName: String,
): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0) getString(index) else null
}
