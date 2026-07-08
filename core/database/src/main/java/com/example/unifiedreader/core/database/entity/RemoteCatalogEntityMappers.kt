package com.example.unifiedreader.core.database.entity

import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryRootFacetEntries
import com.example.unifiedreader.core.model.normalizedAuthors
import com.example.unifiedreader.core.model.primaryAuthor

fun Library.toEntity(): LibraryEntity = LibraryEntity(
    id = id,
    sourceType = sourceType.name,
    title = title,
    remoteId = remoteId,
    contentType = contentType.name,
)

fun Library.toRemoteLibraryCacheEntity(
    baseUrl: String,
    username: String,
): RemoteLibraryCacheEntity = RemoteLibraryCacheEntity(
    sourceType = sourceType.name,
    baseUrl = baseUrl,
    username = username,
    libraryId = id,
    title = title,
    remoteId = remoteId,
    contentType = contentType.name,
)

fun LibraryEntity.toDomain(): Library = Library(
    id = id,
    sourceType = sourceType.toSourceType(),
    title = title,
    remoteId = remoteId,
    contentType = contentType.toLibraryContentType(),
)

fun RemoteLibraryCacheEntity.toDomain(): Library = Library(
    id = libraryId,
    sourceType = sourceType.toSourceType(),
    title = title,
    remoteId = remoteId,
    contentType = contentType.toLibraryContentType(),
)

fun ReadableItem.toEntity(
    updatedAtEpochMillis: Long,
): ReadableItemEntity = ReadableItemEntity(
    id = id,
    sourceType = sourceType.name,
    libraryId = libraryId,
    remoteId = remoteId,
    isLocalImport = isLocalImport,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format.name,
    kind = kind.name,
    pageCount = pageCount,
    coverUrl = coverUrl,
    seriesCoverUrl = seriesCoverUrl,
    description = description,
    addedAtEpochMillis = addedAtEpochMillis,
    bookAuthor = bookMetadata?.primaryAuthor(),
    bookAuthorsJson = encodeMetadataValues(bookMetadata?.normalizedAuthors()),
    bookSeriesName = bookMetadata?.seriesName,
    bookPublishersJson = encodeMetadataValues(bookMetadata?.publishers),
    bookGenresJson = encodeMetadataValues(bookMetadata?.genres),
    bookTagsJson = encodeMetadataValues(bookMetadata?.tags),
    bookLanguagesJson = encodeMetadataValues(bookMetadata?.languages),
    comicSeries = comicMetadata?.series,
    comicIssueTitle = comicMetadata?.issueTitle,
    comicVolume = comicMetadata?.volume,
    comicIssueNumber = comicMetadata?.issueNumber,
    comicWritersJson = encodeMetadataValues(comicMetadata?.writers),
    comicPencillersJson = encodeMetadataValues(comicMetadata?.pencillers),
    comicPublishersJson = encodeMetadataValues(comicMetadata?.publishers),
    comicGenresJson = encodeMetadataValues(comicMetadata?.genres),
    comicTagsJson = encodeMetadataValues(comicMetadata?.tags),
    comicLanguagesJson = encodeMetadataValues(comicMetadata?.languages),
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun ReadableItemEntity.toDomain(): ReadableItem = ReadableItem(
    id = id,
    sourceType = sourceType.toSourceType(),
    libraryId = libraryId,
    remoteId = remoteId,
    isLocalImport = isLocalImport,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format.toFormatType(),
    kind = kind.toReadableItemKind(),
    pageCount = pageCount,
    coverUrl = coverUrl,
    seriesCoverUrl = seriesCoverUrl,
    description = description,
    addedAtEpochMillis = addedAtEpochMillis,
    bookMetadata = bookMetadataOrNull(),
    comicMetadata = comicMetadataOrNull(),
)

fun ReadableItemLibraryEntity.toDomain(): ReadableItem = ReadableItem(
    id = id,
    sourceType = sourceType.toSourceType(),
    libraryId = libraryId,
    remoteId = remoteId,
    isLocalImport = isLocalImport,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format.toFormatType(),
    kind = kind.toReadableItemKind(),
    pageCount = pageCount,
    coverUrl = coverUrl,
    seriesCoverUrl = seriesCoverUrl,
    description = description,
    addedAtEpochMillis = addedAtEpochMillis,
    bookMetadata = bookMetadataOrNull(),
    comicMetadata = comicMetadataOrNull(),
)

fun ReadableItem.toGridRootEntity(): LibraryGridRootEntity = LibraryGridRootEntity(
    id = id,
    sourceType = sourceType.name,
    libraryId = libraryId,
    remoteId = remoteId,
    isLocalImport = isLocalImport,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format.name,
    kind = kind.name,
    pageCount = pageCount,
    coverUrl = coverUrl,
    seriesCoverUrl = seriesCoverUrl,
    description = description,
    addedAtEpochMillis = addedAtEpochMillis,
    issueCount = issueCount,
    bookAuthor = bookMetadata?.primaryAuthor(),
    bookAuthorsJson = encodeMetadataValues(bookMetadata?.normalizedAuthors()),
    bookSeriesName = bookMetadata?.seriesName,
    bookPublishersJson = encodeMetadataValues(bookMetadata?.publishers),
    bookGenresJson = encodeMetadataValues(bookMetadata?.genres),
    bookTagsJson = encodeMetadataValues(bookMetadata?.tags),
    bookLanguagesJson = encodeMetadataValues(bookMetadata?.languages),
    comicSeries = comicMetadata?.series,
    comicIssueTitle = comicMetadata?.issueTitle,
    comicVolume = comicMetadata?.volume,
    comicIssueNumber = comicMetadata?.issueNumber,
    comicWritersJson = encodeMetadataValues(comicMetadata?.writers),
    comicPencillersJson = encodeMetadataValues(comicMetadata?.pencillers),
    comicPublishersJson = encodeMetadataValues(comicMetadata?.publishers),
    comicGenresJson = encodeMetadataValues(comicMetadata?.genres),
    comicTagsJson = encodeMetadataValues(comicMetadata?.tags),
    comicLanguagesJson = encodeMetadataValues(comicMetadata?.languages),
)

fun ReadableItem.toRootFacetEntities(): List<LibraryRootFacetEntity> =
    libraryRootFacetEntries().map { entry ->
        LibraryRootFacetEntity(
            rootId = id,
            sourceType = sourceType.name,
            libraryId = libraryId,
            dimension = entry.dimension.name,
            value = entry.value,
        )
    }

fun LibraryGridRootEntity.toDomain(): ReadableItem = ReadableItem(
    id = id,
    sourceType = sourceType.toSourceType(),
    libraryId = libraryId,
    remoteId = remoteId,
    isLocalImport = isLocalImport,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format.toFormatType(),
    kind = kind.toReadableItemKind(),
    pageCount = pageCount,
    coverUrl = coverUrl,
    seriesCoverUrl = seriesCoverUrl,
    description = description,
    addedAtEpochMillis = addedAtEpochMillis,
    issueCount = issueCount,
    bookMetadata = bookMetadataOrNull(),
    comicMetadata = comicMetadataOrNull(),
)

private fun String.toSourceType(): SourceType =
    enumValueOfOrDefault(defaultValue = SourceType.KAVITA)

private fun String.toFormatType(): FormatType =
    enumValueOfOrDefault(defaultValue = FormatType.EPUB)

private fun String.toReadableItemKind(): ReadableItemKind =
    enumValueOfOrDefault(defaultValue = ReadableItemKind.BOOK)

private fun String.toLibraryContentType(): LibraryContentType =
    enumValueOfOrDefault(defaultValue = LibraryContentType.UNKNOWN)

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
        comicIssueNumber == null &&
        comicWritersJson == null &&
        comicPencillersJson == null &&
        comicPublishersJson == null &&
        comicGenresJson == null &&
        comicTagsJson == null &&
        comicLanguagesJson == null
    ) {
        return null
    }
    return ComicMetadata(
        series = comicSeries,
        issueTitle = comicIssueTitle,
        volume = comicVolume,
        issueNumber = comicIssueNumber,
        writers = decodeMetadataValues(comicWritersJson),
        pencillers = decodeMetadataValues(comicPencillersJson),
        publishers = decodeMetadataValues(comicPublishersJson),
        genres = decodeMetadataValues(comicGenresJson),
        tags = decodeMetadataValues(comicTagsJson),
        languages = decodeMetadataValues(comicLanguagesJson),
    )
}

private fun ReadableItemLibraryEntity.comicMetadataOrNull(): ComicMetadata? {
    if (
        comicSeries == null &&
        comicIssueTitle == null &&
        comicVolume == null &&
        comicIssueNumber == null &&
        comicWritersJson == null &&
        comicPencillersJson == null &&
        comicPublishersJson == null &&
        comicGenresJson == null &&
        comicTagsJson == null &&
        comicLanguagesJson == null
    ) {
        return null
    }
    return ComicMetadata(
        series = comicSeries,
        issueTitle = comicIssueTitle,
        volume = comicVolume,
        issueNumber = comicIssueNumber,
        writers = decodeMetadataValues(comicWritersJson),
        pencillers = decodeMetadataValues(comicPencillersJson),
        publishers = decodeMetadataValues(comicPublishersJson),
        genres = decodeMetadataValues(comicGenresJson),
        tags = decodeMetadataValues(comicTagsJson),
        languages = decodeMetadataValues(comicLanguagesJson),
    )
}

private fun ReadableItemEntity.bookMetadataOrNull(): BookMetadata? {
    if (
        bookAuthor == null &&
        bookAuthorsJson == null &&
        bookSeriesName == null &&
        bookPublishersJson == null &&
        bookGenresJson == null &&
        bookTagsJson == null &&
        bookLanguagesJson == null
    ) {
        return null
    }
    return BookMetadata(
        author = bookAuthor,
        authors = decodeMetadataValues(bookAuthorsJson),
        seriesName = bookSeriesName,
        publishers = decodeMetadataValues(bookPublishersJson),
        genres = decodeMetadataValues(bookGenresJson),
        tags = decodeMetadataValues(bookTagsJson),
        languages = decodeMetadataValues(bookLanguagesJson),
    )
}

private fun ReadableItemLibraryEntity.bookMetadataOrNull(): BookMetadata? {
    if (
        bookAuthor == null &&
        bookAuthorsJson == null &&
        bookSeriesName == null &&
        bookPublishersJson == null &&
        bookGenresJson == null &&
        bookTagsJson == null &&
        bookLanguagesJson == null
    ) {
        return null
    }
    return BookMetadata(
        author = bookAuthor,
        authors = decodeMetadataValues(bookAuthorsJson),
        seriesName = bookSeriesName,
        publishers = decodeMetadataValues(bookPublishersJson),
        genres = decodeMetadataValues(bookGenresJson),
        tags = decodeMetadataValues(bookTagsJson),
        languages = decodeMetadataValues(bookLanguagesJson),
    )
}

private fun LibraryGridRootEntity.comicMetadataOrNull(): ComicMetadata? {
    if (
        comicSeries == null &&
        comicIssueTitle == null &&
        comicVolume == null &&
        comicIssueNumber == null &&
        comicWritersJson == null &&
        comicPencillersJson == null &&
        comicPublishersJson == null &&
        comicGenresJson == null &&
        comicTagsJson == null &&
        comicLanguagesJson == null
    ) {
        return null
    }
    return ComicMetadata(
        series = comicSeries,
        issueTitle = comicIssueTitle,
        volume = comicVolume,
        issueNumber = comicIssueNumber,
        writers = decodeMetadataValues(comicWritersJson),
        pencillers = decodeMetadataValues(comicPencillersJson),
        publishers = decodeMetadataValues(comicPublishersJson),
        genres = decodeMetadataValues(comicGenresJson),
        tags = decodeMetadataValues(comicTagsJson),
        languages = decodeMetadataValues(comicLanguagesJson),
    )
}

private fun LibraryGridRootEntity.bookMetadataOrNull(): BookMetadata? {
    if (
        bookAuthor == null &&
        bookAuthorsJson == null &&
        bookSeriesName == null &&
        bookPublishersJson == null &&
        bookGenresJson == null &&
        bookTagsJson == null &&
        bookLanguagesJson == null
    ) {
        return null
    }
    return BookMetadata(
        author = bookAuthor,
        authors = decodeMetadataValues(bookAuthorsJson),
        seriesName = bookSeriesName,
        publishers = decodeMetadataValues(bookPublishersJson),
        genres = decodeMetadataValues(bookGenresJson),
        tags = decodeMetadataValues(bookTagsJson),
        languages = decodeMetadataValues(bookLanguagesJson),
    )
}

private fun encodeMetadataValues(
    values: List<String>?,
): String? = values
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?.distinctBy(String::lowercase)
    ?.takeIf(List<String>::isNotEmpty)
    ?.joinToString(METADATA_VALUES_DELIMITER)

private fun decodeMetadataValues(
    values: String?,
): List<String> = values
    ?.split(METADATA_VALUES_DELIMITER)
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?.distinctBy(String::lowercase)
    .orEmpty()

private const val METADATA_VALUES_DELIMITER = "\u001F"
