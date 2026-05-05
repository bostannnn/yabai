package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.toReadableFormatTypeOrNull
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    val created: String? = null,
    val lastChapterAddedUtc: String? = null,
    val coverImage: String? = null,
    val writers: List<KavitaPersonDto>? = null,
    val pencillers: List<KavitaPersonDto>? = null,
    val publishers: List<KavitaPersonDto>? = null,
    val genres: List<KavitaGenreTagDto>? = null,
    val tags: List<KavitaTagDto>? = null,
    val language: String? = null,
)

@Serializable
internal data class KavitaGroupedSeriesDto(
    val seriesId: Int? = null,
    val libraryId: Int? = null,
    val created: String? = null,
    val chapterId: Int? = null,
    val volumeId: Int? = null,
    val count: Int? = null,
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
    val writers: List<KavitaPersonDto>? = null,
    val pencillers: List<KavitaPersonDto>? = null,
    val publishers: List<KavitaPersonDto>? = null,
    val genres: List<KavitaGenreTagDto>? = null,
    val tags: List<KavitaTagDto>? = null,
    val language: String? = null,
)

@Serializable
internal data class KavitaPersonDto(
    val name: String? = null,
)

@Serializable
internal data class KavitaGenreTagDto(
    val title: String? = null,
)

@Serializable
internal data class KavitaTagDto(
    val title: String? = null,
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
data class KavitaChapterDetailPlusDto(
    val rating: Float? = null,
    val hasBeenRated: Boolean? = null,
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
internal data class KavitaUpdateRatingDto(
    val seriesId: Int,
    val chapterId: Int,
    val userRating: Float,
)

@Serializable
internal data class KavitaUpdateUserReviewDto(
    val seriesId: Int,
    val chapterId: Int,
    val body: String?,
)

@Serializable
data class KavitaUserReviewDto(
    val id: Int? = null,
    val body: String? = null,
    val bodyJustText: String? = null,
    val seriesId: Int? = null,
    val chapterId: Int? = null,
    val libraryId: Int? = null,
    val username: String? = null,
    val userId: Int? = null,
    val rating: Float? = null,
    val rawBody: String? = null,
    val createdUtc: String? = null,
)

@Serializable
data class KavitaReadingHistoryItemDto(
    val sessionDataIds: List<Int>? = null,
    val sessionId: Int? = null,
    val startTimeUtc: String? = null,
    val endTimeUtc: String? = null,
    val seriesId: Int? = null,
    val seriesName: String? = null,
    val chapters: List<KavitaReadingHistoryChapterItemDto>? = null,
    val libraryId: Int? = null,
    val libraryName: String? = null,
    val pagesRead: Int? = null,
    val durationSeconds: Int? = null,
    val totalPages: Int? = null,
)

@Serializable
data class KavitaReadingHistoryChapterItemDto(
    val chapterId: Int? = null,
    val label: String? = null,
    val startTimeUtc: String? = null,
    val endTimeUtc: String? = null,
    val pagesRead: Int? = null,
    val durationSeconds: Int? = null,
    val startPage: Int? = null,
    val endPage: Int? = null,
    val totalPages: Int? = null,
    val completed: Boolean? = null,
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
        contentType = type.toLibraryContentType(),
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
        pages = pages?.coerceAtLeast(0),
        addedAtEpochMillis = created.toEpochMillisOrNull(),
        lastChapterAddedAtEpochMillis = lastChapterAddedUtc.toEpochMillisOrNull(),
        coverUrl = buildKavitaSeriesCoverUrl(
            baseUrl = baseUrl,
            seriesId = seriesId,
            apiKey = apiKey,
        ),
        writers = writers.toPersonNames(),
        pencillers = pencillers.toPersonNames(),
        publishers = publishers.toPublisherNames(),
        genres = genres.toFacetNames(),
        tags = tags.toTagNames(),
        languages = language.toFacetNames(),
    )
}

internal fun KavitaGroupedSeriesDto.toUpdateEntry(): KavitaSeriesUpdateEntry? {
    val remoteSeriesId = seriesId?.toString() ?: return null
    return KavitaSeriesUpdateEntry(
        seriesId = remoteSeriesId,
        libraryId = libraryId?.toString(),
        createdAtEpochMillis = created.toEpochMillisOrNull(),
        chapterId = chapterId?.toString(),
        volumeId = volumeId?.toString(),
        count = count?.coerceAtLeast(0),
    )
}

internal fun KavitaSeriesDetailDto.toCatalogEntries(
    series: KavitaSeriesEntry,
    issueTitleOverridesByChapterId: Map<String, String> = emptyMap(),
    supplementalChapterInfoByChapterId: Map<String, KavitaChapterInfoDto> = emptyMap(),
): List<KavitaChapterCatalogEntry> {
    val baseUrl = series.coverUrl
        ?.substringBefore("/api/Image/", missingDelimiterValue = "")
        ?.takeIf(String::isNotBlank)
    val apiKey = series.coverUrl
        ?.substringAfter("apiKey=", missingDelimiterValue = "")
        ?.substringBefore('&')
        ?.takeIf(String::isNotBlank)
    return resolvedChapters()
        .mapNotNull { resolved ->
            val chapterId = resolved.chapter.id?.toString() ?: return@mapNotNull null
            val supplementalChapterInfo = supplementalChapterInfoByChapterId[chapterId]
            resolved.chapter.toCatalogEntry(
                series = series,
                volumeName = resolved.volumeName,
                baseUrl = baseUrl,
                apiKey = apiKey,
                issueTitleOverride = issueTitleOverridesByChapterId[chapterId]
                    ?: supplementalChapterInfo?.preferredKavitaIssueTitleFallback(resolved.volumeName),
                formatOverride = supplementalChapterInfo?.resolvedFormat(fallback = series.format),
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
    issueTitleOverride: String? = null,
    formatOverride: FormatType? = null,
): KavitaChapterCatalogEntry? {
    val chapterId = id?.toString() ?: return null
    val pageCount = pages?.coerceAtLeast(0) ?: 0
    val issueTitle = issueTitleOverride ?: preferredIssueTitle(volumeName = volumeName)
    val item = ReadableItem(
        id = SourceType.KAVITA.remoteItemId(chapterId),
        sourceType = SourceType.KAVITA,
        libraryId = SourceType.KAVITA.remoteLibraryId(series.libraryId),
        remoteId = chapterId,
        remoteFileId = volumeId?.toString(),
        remoteFileType = series.id,
        title = issueTitle ?: series.title,
        format = formatOverride ?: series.format,
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
            writers = chapterWriters(series),
            pencillers = chapterPencillers(series),
            publishers = chapterPublishers(series),
            genres = chapterGenres(series),
            tags = chapterTags(series),
            languages = chapterLanguages(series),
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

internal fun KavitaChapterDto.preferredIssueTitle(
    volumeName: String?,
): String? = preferredKavitaIssueTitle(
    explicitTitle = title,
    volumeName = volumeName,
)

private fun Int?.toFormatType(): FormatType? = when (this) {
    KAVITA_FORMAT_IMAGE -> FormatType.CBZ
    KAVITA_FORMAT_ARCHIVE -> FormatType.CBZ
    KAVITA_FORMAT_EPUB -> FormatType.EPUB
    KAVITA_FORMAT_PDF -> FormatType.PDF
    else -> null
}

fun KavitaChapterInfoDto.resolvedFormat(
    fallback: FormatType? = null,
): FormatType? = pageDimensions.orEmpty()
    .asSequence()
    .mapNotNull { dimension ->
        dimension.fileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            .toReadableFormatTypeOrNull()
    }
    .firstOrNull()
    ?: seriesFormat.toFormatType()
    ?: fallback

private fun Int?.toLibraryContentType(): LibraryContentType = when (this) {
    KAVITA_LIBRARY_TYPE_BOOK,
    KAVITA_LIBRARY_TYPE_LIGHT_NOVEL -> LibraryContentType.BOOKS
    KAVITA_LIBRARY_TYPE_COMIC,
    KAVITA_LIBRARY_TYPE_COMIC_FLEXIBLE,
    KAVITA_LIBRARY_TYPE_IMAGE,
    KAVITA_LIBRARY_TYPE_MANGA -> LibraryContentType.COMICS
    else -> LibraryContentType.UNKNOWN
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

private fun String?.toEpochMillisOrNull(): Long? {
    val value = this?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        Instant.parse(value).toEpochMilli()
    }.recoverCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.recoverCatching {
        LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
}

private fun KavitaChapterDto.chapterPublishers(
    series: KavitaSeriesEntry,
): List<String> = publishers.toPublisherNames().ifEmpty { series.publishers }

private fun KavitaChapterDto.chapterWriters(
    series: KavitaSeriesEntry,
): List<String> = writers.toPersonNames().ifEmpty { series.writers }

private fun KavitaChapterDto.chapterPencillers(
    series: KavitaSeriesEntry,
): List<String> = pencillers.toPersonNames().ifEmpty { series.pencillers }

private fun KavitaChapterDto.chapterGenres(
    series: KavitaSeriesEntry,
): List<String> = genres.toFacetNames().ifEmpty { series.genres }

private fun KavitaChapterDto.chapterTags(
    series: KavitaSeriesEntry,
): List<String> = tags.toTagNames().ifEmpty { series.tags }

private fun KavitaChapterDto.chapterLanguages(
    series: KavitaSeriesEntry,
): List<String> = language.toFacetNames().ifEmpty { series.languages }

private fun String?.toFacetNames(): List<String> = listOfNotNull(
    this?.trim()?.takeIf(String::isNotBlank),
)

private fun List<KavitaPersonDto>?.toPublisherNames(): List<String> = orEmpty()
    .mapNotNull { publisher -> publisher.name?.trim()?.takeIf(String::isNotBlank) }
    .distinctBy(String::lowercase)

private fun List<KavitaPersonDto>?.toPersonNames(): List<String> = orEmpty()
    .mapNotNull { person -> person.name?.trim()?.takeIf(String::isNotBlank) }
    .distinctBy(String::lowercase)

private fun List<KavitaGenreTagDto>?.toFacetNames(): List<String> = orEmpty()
    .mapNotNull { facet -> facet.title?.trim()?.takeIf(String::isNotBlank) }
    .distinctBy(String::lowercase)

private fun List<KavitaTagDto>?.toTagNames(): List<String> = orEmpty()
    .mapNotNull { tag -> tag.title?.trim()?.takeIf(String::isNotBlank) }
    .distinctBy(String::lowercase)

private const val KAVITA_FORMAT_IMAGE = 0
private const val KAVITA_FORMAT_ARCHIVE = 1
private const val KAVITA_FORMAT_EPUB = 3
private const val KAVITA_FORMAT_PDF = 4
private const val KAVITA_LIBRARY_TYPE_BOOK = 0
private const val KAVITA_LIBRARY_TYPE_COMIC = 1
private const val KAVITA_LIBRARY_TYPE_COMIC_FLEXIBLE = 2
private const val KAVITA_LIBRARY_TYPE_IMAGE = 3
private const val KAVITA_LIBRARY_TYPE_LIGHT_NOVEL = 4
private const val KAVITA_LIBRARY_TYPE_MANGA = 5
