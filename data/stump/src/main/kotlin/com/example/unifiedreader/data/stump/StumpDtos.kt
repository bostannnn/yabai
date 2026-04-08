package com.example.unifiedreader.data.stump

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class StumpLoginRequestDto(
    val username: String,
    val password: String,
)

@Serializable
internal data class StumpLoginResponseDto(
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

@Serializable
internal data class StumpRefreshTokenResponseDto(
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

@Serializable
internal data class StumpGraphQlRequestDto(
    val query: String,
    val variables: JsonObject,
)

@Serializable
internal data class StumpGraphQlResponseDto(
    val data: JsonObject? = null,
    val errors: List<StumpGraphQlErrorDto>? = null,
)

@Serializable
internal data class StumpGraphQlErrorDto(
    val message: String? = null,
)

@Serializable
internal data class StumpLibrariesQueryData(
    val libraries: StumpLibraryConnectionDto? = null,
)

@Serializable
internal data class StumpLibraryConnectionDto(
    val nodes: List<StumpLibraryNodeDto>? = null,
)

@Serializable
internal data class StumpLibraryNodeDto(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
internal data class StumpSeriesQueryData(
    val series: StumpSeriesConnectionDto? = null,
)

@Serializable
internal data class StumpSeriesConnectionDto(
    val nodes: List<StumpSeriesNodeDto>? = null,
)

@Serializable
internal data class StumpSeriesNodeDto(
    val id: String? = null,
    val resolvedName: String? = null,
    val thumbnail: StumpImageRefDto? = null,
)

@Serializable
internal data class StumpSeriesDetailQueryData(
    val seriesById: StumpSeriesDetailDto? = null,
)

@Serializable
internal data class StumpSeriesDetailDto(
    val id: String? = null,
    val resolvedName: String? = null,
    val media: List<StumpMediaNodeDto>? = null,
)

@Serializable
internal data class StumpMediaProgressQueryData(
    val mediaById: StumpMediaProgressDto? = null,
)

@Serializable
internal data class StumpMediaProgressDto(
    val id: String? = null,
    val pages: Int? = null,
    val readProgress: StumpActiveReadingSessionDto? = null,
    val readHistory: List<StumpFinishedReadingSessionDto>? = null,
)

@Serializable
internal data class StumpMediaNodeDto(
    val id: String? = null,
    val resolvedName: String? = null,
    val extension: String? = null,
    val pages: Int? = null,
    val thumbnail: StumpImageRefDto? = null,
    val metadata: StumpMediaMetadataDto? = null,
    val readProgress: StumpActiveReadingSessionDto? = null,
    val readHistory: List<StumpFinishedReadingSessionDto>? = null,
)

@Serializable
internal data class StumpMediaMetadataDto(
    val summary: String? = null,
    val title: String? = null,
    val volume: JsonElement? = null,
    val number: JsonElement? = null,
    val pageCount: Int? = null,
)

@Serializable
internal data class StumpImageRefDto(
    val url: String? = null,
)

@Serializable
internal data class StumpActiveReadingSessionDto(
    val page: Int? = null,
    val percentageCompleted: JsonElement? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class StumpFinishedReadingSessionDto(
    val completedAt: String? = null,
)

internal fun StumpLoginResponseDto.toSession(): StumpSession = StumpSession(
    accessToken = accessToken?.takeIf(String::isNotBlank)
        ?: error("Stump login response did not include an access token."),
    refreshToken = refreshToken?.takeIf(String::isNotBlank),
)

internal fun StumpRefreshTokenResponseDto.toSession(): StumpSession = StumpSession(
    accessToken = accessToken?.takeIf(String::isNotBlank)
        ?: error("Stump refresh response did not include an access token."),
    refreshToken = refreshToken?.takeIf(String::isNotBlank),
)

internal fun StumpLibraryNodeDto.toDomain(): Library? {
    val remoteId = id?.takeIf(String::isNotBlank) ?: return null
    return Library(
        id = SourceType.STUMP.remoteLibraryId(remoteId),
        sourceType = SourceType.STUMP,
        title = name?.takeIf(String::isNotBlank) ?: "Library $remoteId",
        remoteId = remoteId,
    )
}

internal fun StumpSeriesNodeDto.toEntry(
    baseUrl: String,
    libraryId: String,
): StumpSeriesEntry? {
    val remoteId = id?.takeIf(String::isNotBlank) ?: return null
    return StumpSeriesEntry(
        id = remoteId,
        libraryId = libraryId,
        title = resolvedName?.takeIf(String::isNotBlank) ?: "Series $remoteId",
        coverUrl = thumbnail?.url.toAbsoluteStumpUrl(baseUrl),
    )
}

internal fun StumpSeriesDetailDto.toCatalogEntries(
    baseUrl: String,
    series: StumpSeriesEntry,
): List<StumpMediaCatalogEntry> = media.orEmpty()
    .mapNotNull { node ->
        node.toCatalogEntry(
            baseUrl = baseUrl,
            series = series,
        )
    }

internal fun StumpMediaProgressDto.toReadingProgress(
    itemId: String,
    pageCount: Int?,
): ReadingProgress? {
    val activeProgress = readProgress
    if (activeProgress != null) {
        val resolvedPage = activeProgress.page
            ?.minus(1)
            ?.coerceAtLeast(0)
        val resolvedPercent = activeProgress.percentageCompleted.asDoubleOrNull()
            ?.div(100.0)
            ?.coerceIn(0.0, 1.0)
        return ReadingProgress(
            itemId = itemId,
            locatorType = LocatorType.PAGE,
            page = resolvedPage,
            percent = resolvedPercent,
            isFinished = resolvedPercent?.let { it >= 0.999 } == true,
            updatedAtEpochMillis = activeProgress.updatedAt.toEpochMillisOrNull(),
        )
    }
    val completedAtEpochMillis = readHistory.orEmpty()
        .mapNotNull(StumpFinishedReadingSessionDto::completedAt)
        .mapNotNull(String::toEpochMillisOrNull)
        .maxOrNull()
        ?: return null
    return ReadingProgress(
        itemId = itemId,
        locatorType = LocatorType.PAGE,
        page = pageCount?.minus(1)?.coerceAtLeast(0),
        percent = 1.0,
        isFinished = true,
        updatedAtEpochMillis = completedAtEpochMillis,
    )
}

internal fun StumpMediaNodeDto.toCatalogEntry(
    baseUrl: String,
    series: StumpSeriesEntry,
): StumpMediaCatalogEntry? {
    val mediaId = id?.takeIf(String::isNotBlank) ?: return null
    val format = extension.toComicFormatType() ?: return null
    val issueTitle = metadata?.title?.takeIf(String::isNotBlank)
        ?: resolvedName?.takeIf(String::isNotBlank)
        ?: series.title
    val pageCount = metadata?.pageCount ?: pages
    val item = ReadableItem(
        id = SourceType.STUMP.remoteItemId(mediaId),
        sourceType = SourceType.STUMP,
        libraryId = SourceType.STUMP.remoteLibraryId(series.libraryId),
        remoteId = mediaId,
        remoteFileType = series.id,
        title = issueTitle,
        format = format,
        kind = ReadableItemKind.COMIC,
        pageCount = pageCount?.takeIf { it > 0 },
        coverUrl = thumbnail?.url.toAbsoluteStumpUrl(baseUrl),
        description = metadata?.summary?.takeIf(String::isNotBlank),
        comicMetadata = ComicMetadata(
            series = series.title,
            issueTitle = issueTitle.takeIf { it != series.title },
            volume = metadata?.volume.asDisplayString(),
            issueNumber = metadata?.number.asDisplayString(),
        ),
    )
    return StumpMediaCatalogEntry(
        item = item,
        progress = StumpMediaProgressDto(
            id = id,
            pages = pages,
            readProgress = readProgress,
            readHistory = readHistory,
        ).toReadingProgress(
            itemId = item.id,
            pageCount = item.pageCount,
        ),
    )
}

private fun String?.toAbsoluteStumpUrl(
    baseUrl: String,
): String? {
    val resolved = this?.takeIf(String::isNotBlank) ?: return null
    return when {
        resolved.startsWith("http://", ignoreCase = true) -> resolved
        resolved.startsWith("https://", ignoreCase = true) -> resolved
        resolved.startsWith("/") -> baseUrl.trimEnd('/') + resolved
        else -> baseUrl.trimEnd('/') + "/" + resolved
    }
}

private fun String?.toComicFormatType(): FormatType? = when (this?.lowercase()) {
    "cbz" -> FormatType.CBZ
    "cbr" -> FormatType.CBR
    else -> null
}

private fun String?.toEpochMillisOrNull(): Long? = runCatching {
    this?.takeIf(String::isNotBlank)?.let(Instant::parse)?.toEpochMilli()
}.getOrNull()

private fun JsonElement?.asDoubleOrNull(): Double? = when (this) {
    null -> null
    is JsonPrimitive -> content.toDoubleOrNull()
    else -> null
}

private fun JsonElement?.asDisplayString(): String? {
    return when (this) {
        null -> null
        is JsonPrimitive -> content.takeIf { value -> value.isNotBlank() }
        else -> toString().takeIf(String::isNotBlank)
    }
}
