package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
    val dateFinished: String? = null,
    @SerialName("date_finished")
    val dateFinishedSnake: String? = null,
    val readStatus: String? = null,
    val personalRating: Int? = null,
    val addedOn: String? = null,
    val metadata: GrimmoryBookMetadataDto? = null,
    val pdfProgress: GrimmoryPdfProgressDto? = null,
    val epubProgress: GrimmoryEpubProgressDto? = null,
)

@Serializable
internal data class GrimmoryPersonalRatingRequestDto(
    val ids: List<Long>,
    val rating: Int,
)

@Serializable
data class GrimmoryPersonalRatingResponseDto(
    val bookId: Long? = null,
    val personalRating: Int? = null,
)

@Serializable
data class GrimmoryReadingSessionPageDto(
    val content: List<GrimmoryReadingSessionDto>? = null,
    val totalPages: Int? = null,
    val last: Boolean? = null,
    val number: Int? = null,
)

@Serializable
data class GrimmoryReadingSessionDto(
    val id: Long? = null,
    val bookId: Long? = null,
    val bookTitle: String? = null,
    val bookType: String? = null,
    val startTime: String? = null,
    @SerialName("start_time")
    val startTimeSnake: String? = null,
    val endTime: String? = null,
    @SerialName("end_time")
    val endTimeSnake: String? = null,
    val durationSeconds: Int? = null,
    @SerialName("duration_seconds")
    val durationSecondsSnake: Int? = null,
    val durationMillis: Long? = null,
    val startProgress: Float? = null,
    @SerialName("start_progress")
    val startProgressSnake: Float? = null,
    val endProgress: Float? = null,
    @SerialName("end_progress")
    val endProgressSnake: Float? = null,
    val progressDelta: Float? = null,
    val startLocation: String? = null,
    val endLocation: String? = null,
    val createdAt: String? = null,
    @SerialName("created_at")
    val createdAtSnake: String? = null,
)

@Serializable
internal data class GrimmoryReadingSessionRequestDto(
    val bookId: Long,
    val bookType: String? = null,
    val startTime: String,
    val endTime: String,
    val durationSeconds: Int,
    val durationFormatted: String? = null,
    val startProgress: Float? = null,
    val endProgress: Float? = null,
    val progressDelta: Float? = null,
    val startLocation: String? = null,
    val endLocation: String? = null,
)

@Serializable
data class GrimmoryBookNoteV2Dto(
    val id: Long? = null,
    val userId: Long? = null,
    val bookId: Long? = null,
    val cfi: String? = null,
    val selectedText: String? = null,
    val noteContent: String? = null,
    val color: String? = null,
    val chapterTitle: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class GrimmoryNotebookEntryDto(
    val id: Long? = null,
    val type: String? = null,
    val bookId: Long? = null,
    val text: String? = null,
    val note: String? = null,
    val color: String? = null,
    val style: String? = null,
    val chapterTitle: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class GrimmoryCreateBookNoteV2RequestDto(
    val bookId: Long,
    val cfi: String,
    val selectedText: String? = null,
    val noteContent: String,
    val color: String? = null,
    val chapterTitle: String? = null,
)

@Serializable
internal data class GrimmoryUpdateBookNoteV2RequestDto(
    val noteContent: String? = null,
    val color: String? = null,
    val chapterTitle: String? = null,
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
    val publisher: String? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val language: String? = null,
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
    val dateFinished: JsonElement? = null,
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
        contentType = LibraryContentType.BOOKS,
    )
}

internal fun GrimmoryBookDto.toDomain(
    baseUrl: String,
): ReadableItem? = toCatalogEntry(
    baseUrl = baseUrl,
)?.item

internal fun GrimmoryBookDto.toCatalogEntry(
    baseUrl: String,
    progressStateIncluded: Boolean = true,
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
            authors = metadata?.authors.toFacetNames(),
            seriesName = metadata?.seriesName?.trim()?.takeIf(String::isNotBlank),
            publishers = metadata?.publisher.toFacetNames(),
            genres = metadata?.genres.toFacetNames(),
            tags = metadata?.tags.toFacetNames(),
            languages = metadata?.language.toFacetNames(),
        ),
    )
    val progress = toReadingProgress(item)
    return GrimmoryBookCatalogEntry(
        item = item,
        progress = progress,
        isExplicitUnreadReset = progressStateIncluded && progress == null,
        personalRating = personalRating,
        readStatus = readStatus,
        dateFinished = dateFinished ?: dateFinishedSnake,
        lastReadTime = lastReadTime,
    )
}

private fun String?.toFacetNames(): List<String> = listOfNotNull(
    this?.trim()?.takeIf(String::isNotBlank),
)

private fun List<String>?.toFacetNames(): List<String> = orEmpty()
    .mapNotNull { value -> value.trim().takeIf(String::isNotBlank) }
    .distinctBy(String::lowercase)

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

internal fun String.toEpochMillisOrNull(): Long? = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrNull()
