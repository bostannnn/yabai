package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class KavitaLoginRequestDto(
    val username: String? = null,
    val password: String? = null,
    val apiKey: String? = null,
)

@Serializable
internal data class KavitaTokenRequestDto(
    val token: String? = null,
    val refreshToken: String? = null,
)

@Serializable
internal data class KavitaUserDto(
    val id: Int? = null,
    val username: String? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val apiKey: String? = null,
)

@Serializable
internal data class KavitaLibraryDto(
    val id: Int? = null,
    val name: String? = null,
    val type: Int? = null,
    val coverImage: String? = null,
)

@Serializable
internal data class KavitaSeriesDto(
    val id: Int? = null,
    val name: String? = null,
    val pages: Int? = null,
    val format: Int? = null,
    val libraryId: Int? = null,
    val libraryName: String? = null,
    val lastChapterAddedUtc: String? = null,
    val coverImage: String? = null,
)

@Serializable
internal data class KavitaSeriesDetailDto(
    val specials: List<KavitaChapterDto>? = null,
    val chapters: List<KavitaChapterDto>? = null,
    val volumes: List<KavitaVolumeDto>? = null,
    val storylineChapters: List<KavitaChapterDto>? = null,
)

@Serializable
internal data class KavitaVolumeDto(
    val id: Int? = null,
    val name: String? = null,
    val seriesId: Int? = null,
    val chapters: List<KavitaChapterDto>? = null,
)

@Serializable
internal data class KavitaChapterDto(
    val id: Int? = null,
    val range: String? = null,
    val number: String? = null,
    val minNumber: Float? = null,
    val maxNumber: Float? = null,
    val pages: Int? = null,
    val isSpecial: Boolean? = null,
    val title: String? = null,
    val pagesRead: Int? = null,
    val lastReadingProgressUtc: String? = null,
    val volumeId: Int? = null,
    val summary: String? = null,
    val volumeTitle: String? = null,
)

@Serializable
data class KavitaProgressDto(
    val volumeId: Int? = null,
    val chapterId: Int? = null,
    val pageNum: Int? = null,
    val seriesId: Int? = null,
    val libraryId: Int? = null,
    val bookScrollId: String? = null,
    val lastModifiedUtc: String? = null,
)

@Serializable
data class KavitaChapterInfoDto(
    val chapterNumber: String? = null,
    val volumeNumber: String? = null,
    val volumeId: Int? = null,
    val seriesName: String? = null,
    val seriesFormat: Int? = null,
    val seriesId: Int? = null,
    val libraryId: Int? = null,
    val pages: Int? = null,
    val title: String? = null,
    val chapterTitle: String? = null,
    val pageDimensions: List<KavitaFileDimensionDto>? = null,
)

@Serializable
data class KavitaFileDimensionDto(
    val width: Int? = null,
    val height: Int? = null,
    val pageNumber: Int? = null,
    val fileName: String? = null,
    val isWide: Boolean? = null,
)

@Serializable
internal data class KavitaFilterV2Dto(
    val id: Int? = null,
    val name: String? = null,
    val statements: List<KavitaFilterStatementDto>? = null,
    val combination: Int? = null,
    val sortOptions: KavitaSortOptionsDto? = null,
    val limitTo: Int? = null,
)

@Serializable
internal data class KavitaFilterStatementDto(
    val comparison: Int? = null,
    val field: Int? = null,
    val value: String? = null,
)

@Serializable
internal data class KavitaSortOptionsDto(
    val sortField: Int? = null,
    val isAscending: Boolean? = null,
)

internal fun KavitaUserDto.toSession(): KavitaSession = KavitaSession(
    accessToken = token?.takeIf(String::isNotBlank)
        ?: error("Kavita login response did not include an access token."),
    refreshToken = refreshToken?.takeIf(String::isNotBlank),
    apiKey = apiKey?.takeIf(String::isNotBlank),
)

internal fun KavitaTokenRequestDto.toSession(
    apiKey: String?,
): KavitaSession = KavitaSession(
    accessToken = token?.takeIf(String::isNotBlank)
        ?: error("Kavita refresh response did not include an access token."),
    refreshToken = refreshToken?.takeIf(String::isNotBlank),
    apiKey = apiKey?.takeIf(String::isNotBlank),
)

internal fun KavitaLibraryDto.toDomain(): Library? {
    val remoteId = id?.toString() ?: return null
    return Library(
        id = SourceType.KAVITA.remoteLibraryId(remoteId),
        sourceType = SourceType.KAVITA,
        title = name?.takeIf(String::isNotBlank) ?: "Library $remoteId",
        remoteId = remoteId,
    )
}

internal fun KavitaSeriesDto.toEntry(
    baseUrl: String,
    apiKey: String?,
): KavitaSeriesEntry? {
    val seriesId = id?.toString() ?: return null
    val remoteLibraryId = libraryId?.toString() ?: return null
    val format = format.toFormatType() ?: return null
    return KavitaSeriesEntry(
        id = seriesId,
        libraryId = remoteLibraryId,
        title = name?.takeIf(String::isNotBlank) ?: "Series $seriesId",
        format = format,
        addedAtEpochMillis = lastChapterAddedUtc.toEpochMillisOrNull(),
        coverUrl = buildKavitaSeriesCoverUrl(
            baseUrl = baseUrl,
            seriesId = seriesId,
            apiKey = apiKey,
        ),
    )
}

internal fun KavitaSeriesDetailDto.toCatalogEntries(
    series: KavitaSeriesEntry,
): List<KavitaChapterCatalogEntry> {
    val baseUrl = series.coverUrl
        ?.substringBefore("/api/Image/", missingDelimiterValue = "")
        ?.takeIf(String::isNotBlank)
    val apiKey = series.coverUrl
        ?.substringAfter("apiKey=", missingDelimiterValue = "")
        ?.substringBefore('&')
        ?.takeIf(String::isNotBlank)
    val volumeNamesById = volumes.orEmpty()
        .associate { volume -> volume.id?.toString() to volume.name }
        .filterKeys { it != null }
        .mapKeys { (key, _) -> key!! }
    return sequenceOf(
        chapters.orEmpty(),
        specials.orEmpty(),
        storylineChapters.orEmpty(),
        volumes.orEmpty().flatMap { it.chapters.orEmpty() },
    ).flatten()
        .mapNotNull { chapter ->
            chapter.toCatalogEntry(
                series = series,
                volumeName = chapter.volumeTitle?.takeIf(String::isNotBlank)
                    ?: chapter.volumeId?.toString()?.let(volumeNamesById::get),
                baseUrl = baseUrl,
                apiKey = apiKey,
            )
        }
        .distinctBy { entry -> entry.item.id }
        .toList()
}

internal fun KavitaChapterDto.toCatalogEntry(
    series: KavitaSeriesEntry,
    volumeName: String?,
    baseUrl: String?,
    apiKey: String?,
): KavitaChapterCatalogEntry? {
    val chapterId = id?.toString() ?: return null
    val pageCount = pages?.coerceAtLeast(0) ?: 0
    val issueTitle = preferredIssueTitle(volumeName = volumeName)
    val item = ReadableItem(
        id = SourceType.KAVITA.remoteItemId(chapterId),
        sourceType = SourceType.KAVITA,
        libraryId = SourceType.KAVITA.remoteLibraryId(series.libraryId),
        remoteId = chapterId,
        remoteFileId = volumeId?.toString(),
        remoteFileType = series.id,
        title = issueTitle ?: series.title,
        format = series.format,
        pageCount = pageCount.takeIf { it > 0 },
        coverUrl = baseUrl?.let { resolvedBaseUrl ->
            buildKavitaChapterCoverUrl(
                baseUrl = resolvedBaseUrl,
                chapterId = chapterId,
                apiKey = apiKey,
            )
        } ?: series.coverUrl,
        description = summary,
        addedAtEpochMillis = series.addedAtEpochMillis,
        comicMetadata = ComicMetadata(
            series = series.title,
            issueTitle = issueTitle,
            volume = volumeName,
            issueNumber = range?.takeIf(String::isNotBlank)
                ?: number?.takeIf(String::isNotBlank),
        ),
    )
    val progress = toReadingProgress(
        itemId = item.id,
        pageCount = pageCount,
    )
    return KavitaChapterCatalogEntry(
        item = item,
        progress = progress,
        isExplicitUnreadReset = progress == null,
    )
}

fun KavitaProgressDto.toReadingProgress(
    itemId: String,
    pageCount: Int?,
): ReadingProgress? {
    val remotePage = pageNum ?: return null
    if (remotePage <= 0) return null
    val safePageCount = pageCount?.coerceAtLeast(0) ?: 0
    val isFinished = safePageCount > 0 && remotePage >= safePageCount
    val localPage = when {
        safePageCount <= 0 -> remotePage.coerceAtLeast(0)
        isFinished -> safePageCount - 1
        else -> remotePage.coerceIn(0, safePageCount - 1)
    }
    return ReadingProgress(
        itemId = itemId,
        locatorType = LocatorType.PAGE,
        page = localPage,
        percent = when {
            safePageCount <= 0 -> null
            isFinished -> 1.0
            else -> (localPage.toDouble() / safePageCount.toDouble()).coerceIn(0.0, 1.0)
        },
        isFinished = isFinished,
        updatedAtEpochMillis = lastModifiedUtc.toEpochMillisOrNull(),
    )
}

fun KavitaChapterInfoDto.toProgressUpdate(
    item: ReadableItem,
    page: Int,
    isFinished: Boolean,
    updatedAtEpochMillis: Long,
): KavitaProgressUpdate? {
    val chapterId = item.remoteId?.toIntOrNull() ?: return null
    val volumeId = volumeId ?: item.remoteFileId?.toIntOrNull() ?: return null
    val seriesId = seriesId ?: item.remoteFileType?.toIntOrNull() ?: return null
    val libraryId = libraryId
        ?: item.libraryId.substringAfterLast(':', missingDelimiterValue = "")
            .toIntOrNull()
        ?: return null
    val safePageCount = (pages ?: item.pageCount ?: 0).coerceAtLeast(0)
    val remotePage = when {
        isFinished && safePageCount > 0 -> safePageCount
        else -> page.coerceAtLeast(0)
    }
    return KavitaProgressUpdate(
        libraryId = libraryId,
        seriesId = seriesId,
        volumeId = volumeId,
        chapterId = chapterId,
        pageNum = remotePage,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun KavitaChapterDto.toReadingProgress(
    itemId: String,
    pageCount: Int,
): ReadingProgress? = KavitaProgressDto(
    volumeId = volumeId,
    chapterId = id,
    pageNum = pagesRead,
    lastModifiedUtc = lastReadingProgressUtc,
).toReadingProgress(
    itemId = itemId,
    pageCount = pageCount,
)

private fun KavitaChapterDto.preferredIssueTitle(
    volumeName: String?,
): String? = listOfNotNull(
    title?.takeIf(String::isNotBlank),
    range?.takeIf(String::isNotBlank)?.let { value ->
        if (volumeName.isNullOrBlank()) {
            "Chapter $value"
        } else {
            "$volumeName • Chapter $value"
        }
    },
    number?.takeIf(String::isNotBlank)?.let { value ->
        if (volumeName.isNullOrBlank()) {
            "Chapter $value"
        } else {
            "$volumeName • Chapter $value"
        }
    },
    volumeName?.takeIf(String::isNotBlank),
).firstOrNull()

private fun Int?.toFormatType(): FormatType? = when (this) {
    KAVITA_FORMAT_IMAGE,
    KAVITA_FORMAT_ARCHIVE -> FormatType.CBZ
    else -> null
}

private fun buildKavitaSeriesCoverUrl(
    baseUrl: String,
    seriesId: String,
    apiKey: String?,
): String = buildString {
    append(baseUrl.trimEnd('/'))
    append("/api/Image/series-cover?seriesId=")
    append(seriesId)
    apiKey?.takeIf(String::isNotBlank)?.let { key ->
        append("&apiKey=")
        append(key)
    }
}

private fun buildKavitaChapterCoverUrl(
    baseUrl: String,
    chapterId: String,
    apiKey: String?,
): String = buildString {
    append(baseUrl.trimEnd('/'))
    append("/api/Image/chapter-cover?chapterId=")
    append(chapterId)
    apiKey?.takeIf(String::isNotBlank)?.let { key ->
        append("&apiKey=")
        append(key)
    }
}

private fun String?.toEpochMillisOrNull(): Long? = runCatching {
    this?.let(Instant::parse)?.toEpochMilli()
}.getOrNull()

private const val KAVITA_FORMAT_IMAGE = 0
private const val KAVITA_FORMAT_ARCHIVE = 1
