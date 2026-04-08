package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.BookMetadata
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class GrimmoryLoginRequestDto(
    val username: String,
    val password: String,
)

@Serializable
internal data class GrimmoryRefreshTokenRequestDto(
    val refreshToken: String,
)

@Serializable
internal data class GrimmoryLoginResponseDto(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val isDefaultPassword: String? = null,
)

@Serializable
internal data class GrimmoryApiErrorDto(
    val message: String? = null,
    val error: String? = null,
    val detail: String? = null,
    val path: String? = null,
)

@Serializable
internal data class GrimmoryLibraryDto(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
internal data class GrimmoryBookDto(
    val id: Long? = null,
    val libraryId: Long? = null,
    val libraryName: String? = null,
    val primaryFile: GrimmoryBookFileDto? = null,
    val title: String? = null,
    val lastReadTime: String? = null,
    val addedOn: String? = null,
    val metadata: GrimmoryBookMetadataDto? = null,
    val pdfProgress: GrimmoryPdfProgressDto? = null,
    val epubProgress: GrimmoryEpubProgressDto? = null,
)

@Serializable
internal data class GrimmoryBookFileDto(
    val id: Long? = null,
    val bookId: Long? = null,
    val fileName: String? = null,
    val bookType: String? = null,
    val extension: String? = null,
)

@Serializable
internal data class GrimmoryBookMetadataDto(
    val title: String? = null,
    val subtitle: String? = null,
    val description: String? = null,
    val seriesName: String? = null,
    val seriesNumber: Float? = null,
    val pageCount: Int? = null,
    val authors: List<String>? = null,
    val thumbnailUrl: String? = null,
)

@Serializable
internal data class GrimmoryPdfProgressDto(
    val page: Int? = null,
    val percentage: Float? = null,
)

@Serializable
internal data class GrimmoryEpubProgressDto(
    val cfi: String? = null,
    val href: String? = null,
    val percentage: Float? = null,
    val ttsPositionCfi: String? = null,
)

@Serializable
internal data class GrimmoryReadProgressRequestDto(
    val bookId: Long,
    val fileProgress: GrimmoryBookFileProgressDto? = null,
    @SerialName("epubProgress")
    val deprecatedEpubProgress: GrimmoryEpubProgressDto? = null,
    @SerialName("pdfProgress")
    val deprecatedPdfProgress: GrimmoryPdfProgressDto? = null,
    val dateFinished: String? = null,
)

@Serializable
internal data class GrimmoryBookFileProgressDto(
    val bookFileId: Long,
    val positionData: String,
    val positionHref: String? = null,
    val progressPercent: Float,
    val ttsPositionCfi: String? = null,
)

internal fun GrimmoryLoginResponseDto.toSession(): GrimmorySession = GrimmorySession(
    accessToken = accessToken?.takeIf(String::isNotBlank)
        ?: error("Grimmory login response did not include an access token."),
    refreshToken = refreshToken?.takeIf(String::isNotBlank)
        ?: error("Grimmory login response did not include a refresh token."),
    isDefaultPassword = isDefaultPassword.toBoolean(),
)

internal fun JsonObject.toLoginSession(): GrimmorySession = GrimmorySession(
    accessToken = requiredString(
        "accessToken",
        "access_token",
        "token",
    ) ?: error("Grimmory login response did not include an access token."),
    refreshToken = requiredString(
        "refreshToken",
        "refresh_token",
    ) ?: error("Grimmory login response did not include a refresh token."),
    isDefaultPassword = optionalString("isDefaultPassword")?.toBoolean()
        ?: this["isDefaultPassword"]?.jsonPrimitive?.booleanOrNull
        ?: false,
)

internal fun GrimmoryApiErrorDto.toUserMessage(
    statusDescription: String,
): String = listOfNotNull(
    message?.takeIf(String::isNotBlank),
    detail?.takeIf(String::isNotBlank),
    error?.takeIf(String::isNotBlank),
).firstOrNull() ?: statusDescription

private fun JsonObject.requiredString(
    vararg keys: String,
): String? = keys.firstNotNullOfOrNull(::optionalString)

private fun JsonObject.optionalString(
    key: String,
): String? = this[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)

internal fun GrimmoryLibraryDto.toDomain(): Library? {
    val libraryId = id?.toString() ?: return null
    return Library(
        id = SourceType.GRIMMORY.remoteLibraryId(libraryId),
        sourceType = SourceType.GRIMMORY,
        title = name?.takeIf(String::isNotBlank) ?: "Library $libraryId",
        remoteId = libraryId,
    )
}

internal fun GrimmoryBookDto.toDomain(
    baseUrl: String,
): ReadableItem? = toCatalogEntry(
    baseUrl = baseUrl,
)?.item

internal fun GrimmoryBookDto.toCatalogEntry(
    baseUrl: String,
): GrimmoryBookCatalogEntry? {
    val bookId = id?.toString() ?: return null
    val libraryId = libraryId?.toString() ?: return null
    val format = primaryFile?.toFormatType() ?: return null
    val metadataTitle = metadata?.title?.takeIf(String::isNotBlank)
    val fallbackTitle = title?.takeIf(String::isNotBlank)
        ?: primaryFile?.fileName?.takeIf(String::isNotBlank)
    val itemTitle = metadataTitle ?: fallbackTitle ?: "Book $bookId"
    val item = ReadableItem(
        id = SourceType.GRIMMORY.remoteItemId(bookId),
        sourceType = SourceType.GRIMMORY,
        libraryId = SourceType.GRIMMORY.remoteLibraryId(libraryId),
        remoteId = bookId,
        remoteFileId = primaryFile?.id?.toString(),
        remoteFileType = primaryFile?.bookType?.takeIf(String::isNotBlank) ?: format.name,
        title = itemTitle,
        format = format,
        kind = ReadableItemKind.BOOK,
        pageCount = metadata?.pageCount,
        coverUrl = metadata?.thumbnailUrl.toAbsoluteGrimmoryCoverUrl(
            baseUrl = baseUrl,
            remoteBookId = bookId,
        ),
        description = metadata?.description,
        addedAtEpochMillis = addedOn?.toEpochMillisOrNull(),
        bookMetadata = BookMetadata(
            author = metadata?.authors?.firstOrNull(String::isNotBlank),
        ),
    )
    return GrimmoryBookCatalogEntry(
        item = item,
        progress = toReadingProgress(item),
    )
}

private fun GrimmoryBookDto.toReadingProgress(
    item: ReadableItem,
): ReadingProgress? {
    val updatedAtEpochMillis = lastReadTime?.toEpochMillisOrNull()
    return when (item.format) {
        FormatType.EPUB -> epubProgress?.toReadingProgress(
            itemId = item.id,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        FormatType.PDF -> pdfProgress?.toReadingProgress(
            itemId = item.id,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        else -> null
    }
}

private fun GrimmoryEpubProgressDto.toReadingProgress(
    itemId: String,
    updatedAtEpochMillis: Long?,
): ReadingProgress? {
    val localPercent = percentage.toLocalProgressPercent()
    val locatorType = if (cfi.isNullOrBlank()) LocatorType.PERCENT else LocatorType.CFI
    if (locatorType == LocatorType.PERCENT && localPercent == null) return null
    return ReadingProgress(
        itemId = itemId,
        locatorType = locatorType,
        cfi = cfi?.takeIf(String::isNotBlank)?.toReaderLocatorJson(
            href = href,
            percent = localPercent,
        ),
        percent = localPercent,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun GrimmoryPdfProgressDto.toReadingProgress(
    itemId: String,
    updatedAtEpochMillis: Long?,
): ReadingProgress? {
    val localPercent = percentage.toLocalProgressPercent()
    val pageIndex = page?.let { (it - 1).coerceAtLeast(0) }
    if (pageIndex == null && localPercent == null) return null
    return ReadingProgress(
        itemId = itemId,
        locatorType = if (pageIndex == null) LocatorType.PERCENT else LocatorType.PAGE,
        page = pageIndex,
        percent = localPercent,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun Float?.toLocalProgressPercent(): Double? = this?.let { value ->
    (value / 100f).toDouble().coerceIn(0.0, 1.0)
}

private fun String?.toAbsoluteGrimmoryCoverUrl(
    baseUrl: String,
    remoteBookId: String,
): String {
    val normalizedBaseUrl = baseUrl.trimEnd('/')
    val thumbnailUrl = this?.trim().orEmpty()
    if (thumbnailUrl.isBlank()) {
        return "$normalizedBaseUrl/api/v1/media/book/$remoteBookId/thumbnail"
    }
    if (thumbnailUrl.startsWith("http://", ignoreCase = true) ||
        thumbnailUrl.startsWith("https://", ignoreCase = true)
    ) {
        return thumbnailUrl
    }
    return if (thumbnailUrl.startsWith("/")) {
        normalizedBaseUrl + thumbnailUrl
    } else {
        "$normalizedBaseUrl/$thumbnailUrl"
    }
}

private fun String.toReaderLocatorJson(
    href: String?,
    percent: Double?,
): String {
    val locatorHref = href?.takeIf(String::isNotBlank) ?: return this
    return buildJsonObject {
        put("href", JsonPrimitive(locatorHref))
        put("type", JsonPrimitive("application/xhtml+xml"))
        put(
            "locations",
            buildJsonObject {
                put(
                    "fragments",
                    buildJsonArray {
                        add(JsonPrimitive(this@toReaderLocatorJson))
                    },
                )
                percent?.let { value ->
                    put("totalProgression", JsonPrimitive(value))
                }
            },
        )
    }.toString()
}

private fun GrimmoryBookFileDto.toFormatType(): FormatType? {
    val normalizedType = bookType?.trim()?.uppercase()
    val normalizedExtension = extension?.trim()?.lowercase()
        ?: fileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    return when {
        normalizedType == "EPUB" || normalizedExtension == "epub" -> FormatType.EPUB
        normalizedType == "PDF" || normalizedExtension == "pdf" -> FormatType.PDF
        // CBZ/CBR are handled by the Kavita/local comic readers, not Grimmory.
        else -> null
    }
}

private fun String.toEpochMillisOrNull(): Long? = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrNull()
