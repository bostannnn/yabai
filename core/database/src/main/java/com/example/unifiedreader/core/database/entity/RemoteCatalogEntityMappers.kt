package com.example.unifiedreader.core.database.entity

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType

fun Library.toEntity(): LibraryEntity = LibraryEntity(
    id = id,
    serverId = serverId,
    sourceType = sourceType.name,
    title = title,
)

fun LibraryEntity.toDomain(): Library = Library(
    id = id,
    serverId = serverId,
    sourceType = sourceType.toSourceType(),
    title = title,
)

fun ReadableItem.toEntity(
    updatedAtEpochMillis: Long,
): ReadableItemEntity = ReadableItemEntity(
    id = id,
    serverId = serverId,
    sourceType = sourceType.name,
    libraryId = libraryId,
    isLocalImport = isLocalImport,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format.name,
    kind = kind.name,
    pageCount = pageCount,
    coverUrl = coverUrl,
    description = description,
    addedAtEpochMillis = addedAtEpochMillis,
    bookAuthor = bookMetadata?.author,
    comicSeries = comicMetadata?.series,
    comicIssueTitle = comicMetadata?.issueTitle,
    comicVolume = comicMetadata?.volume,
    comicIssueNumber = comicMetadata?.issueNumber,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ReadableItemEntity.toDomain(): ReadableItem = ReadableItem(
    id = id,
    serverId = serverId,
    sourceType = sourceType.toSourceType(),
    libraryId = libraryId,
    isLocalImport = isLocalImport,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format.toFormatType(),
    kind = kind.toReadableItemKind(),
    pageCount = pageCount,
    coverUrl = coverUrl,
    description = description,
    addedAtEpochMillis = addedAtEpochMillis,
    bookMetadata = bookAuthor?.let { author ->
        BookMetadata(author = author)
    },
    comicMetadata = comicMetadataOrNull(),
)

private fun String.toSourceType(): SourceType =
    enumValueOfOrDefault(defaultValue = SourceType.KAVITA)

private fun String.toFormatType(): FormatType =
    enumValueOfOrDefault(defaultValue = FormatType.EPUB)

private fun String.toReadableItemKind(): ReadableItemKind =
    enumValueOfOrDefault(defaultValue = ReadableItemKind.BOOK)

private inline fun <reified T : Enum<T>> String.enumValueOfOrDefault(
    defaultValue: T,
): T = runCatching {
    enumValueOf<T>(this)
}.getOrDefault(defaultValue)

private fun ReadableItemEntity.comicMetadataOrNull(): ComicMetadata? {
    if (
        comicSeries == null &&
        comicIssueTitle == null &&
        comicVolume == null &&
        comicIssueNumber == null
    ) {
        return null
    }
    return ComicMetadata(
        series = comicSeries,
        issueTitle = comicIssueTitle,
        volume = comicVolume,
        issueNumber = comicIssueNumber,
    )
}
