package com.example.unifiedreader.core.model

enum class SourceType {
    KAVITA,
    STUMP,
    GRIMMORY,
    LOCAL,
}

enum class FormatType {
    CBZ,
    CBR,
    PDF,
    EPUB,
}

enum class ReadableItemKind {
    BOOK,
    COMIC,
}

enum class LocatorType {
    PAGE,
    CFI,
    PERCENT,
}

enum class SyncState {
    CLEAN,
    DIRTY,
    SYNCING,
    FAILED,
}

data class Library(
    val id: String,
    val sourceType: SourceType,
    val title: String,
    val remoteId: String? = null,
    val contentType: LibraryContentType = LibraryContentType.UNKNOWN,
)

enum class LibraryContentType {
    BOOKS,
    COMICS,
    UNKNOWN,
}

data class ReadableItem(
    val id: String,
    val sourceType: SourceType,
    val libraryId: String,
    val remoteId: String? = null,
    val isLocalImport: Boolean = false,
    val remoteFileId: String? = null,
    val remoteFileType: String? = null,
    val title: String,
    val format: FormatType,
    val kind: ReadableItemKind = format.defaultReadableItemKind(),
    val pageCount: Int? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val addedAtEpochMillis: Long? = null,
    val bookMetadata: BookMetadata? = null,
    val comicMetadata: ComicMetadata? = null,
)

data class BookMetadata(
    val author: String? = null,
)

data class ComicMetadata(
    val series: String? = null,
    val issueTitle: String? = null,
    val volume: String? = null,
    val issueNumber: String? = null,
)

data class ReadingProgress(
    val itemId: String,
    val locatorType: LocatorType,
    val page: Int? = null,
    val cfi: String? = null,
    val percent: Double? = null,
    val isFinished: Boolean = false,
    val syncState: SyncState = SyncState.CLEAN,
    val updatedAtEpochMillis: Long? = null,
)

fun ReadableItem.libraryPrimaryText(): String = when (kind) {
    ReadableItemKind.BOOK -> title
    ReadableItemKind.COMIC -> comicMetadata?.series?.takeIf(String::isNotBlank) ?: title
}

fun ReadableItem.librarySecondaryText(): String? = when (kind) {
    ReadableItemKind.BOOK -> bookMetadata?.author?.takeIf(String::isNotBlank)
    ReadableItemKind.COMIC -> {
        val issueTitle = comicMetadata?.issueTitle?.takeIf(String::isNotBlank) ?: return null
        if (issueTitle == libraryPrimaryText() || issueTitle == title) null else issueTitle
    }
}

fun ReadableItem.libraryDisplayLabel(): String = when (kind) {
    ReadableItemKind.BOOK -> title
    ReadableItemKind.COMIC -> listOfNotNull(
        libraryPrimaryText().takeIf(String::isNotBlank),
        librarySecondaryText(),
    ).distinct().joinToString(": ").ifBlank { title }
}

private fun FormatType.defaultReadableItemKind(): ReadableItemKind = when (this) {
    FormatType.CBZ,
    FormatType.CBR -> ReadableItemKind.COMIC
    FormatType.EPUB,
    FormatType.PDF -> ReadableItemKind.BOOK
}

fun SourceType.remoteLibraryId(
    remoteId: String,
): String = "${name.lowercase()}:library:$remoteId"

fun SourceType.remoteItemId(
    remoteId: String,
): String = "${name.lowercase()}:item:$remoteId"

fun kavitaSeriesContainerId(
    seriesKey: String,
): String = "kavita:series:$seriesKey"

fun stumpSeriesContainerId(
    seriesKey: String,
): String = "stump:series:$seriesKey"

fun ReadableItem.isKavitaSeriesContainer(): Boolean =
    sourceType == SourceType.KAVITA && id.startsWith("kavita:series:")

fun ReadableItem.isStumpSeriesContainer(): Boolean =
    sourceType == SourceType.STUMP && id.startsWith("stump:series:")

fun ReadableItem.kavitaSeriesGroupKey(): String? {
    if (sourceType != SourceType.KAVITA || kind != ReadableItemKind.COMIC || isKavitaSeriesContainer()) {
        return null
    }
    val seriesKey = remoteFileType?.takeIf(String::isNotBlank)
        ?: comicMetadata?.series?.takeIf(String::isNotBlank)
        ?: title.takeIf(String::isNotBlank)
        ?: return null
    return "$libraryId::$seriesKey"
}

fun ReadableItem.stumpSeriesGroupKey(): String? {
    if (sourceType != SourceType.STUMP || kind != ReadableItemKind.COMIC || isStumpSeriesContainer()) {
        return null
    }
    val seriesKey = remoteFileType?.takeIf(String::isNotBlank)
        ?: comicMetadata?.series?.takeIf(String::isNotBlank)
        ?: title.takeIf(String::isNotBlank)
        ?: return null
    return "$libraryId::$seriesKey"
}

fun ReadableItem.comicSeriesPreferenceKey(): String? {
    if (kind != ReadableItemKind.COMIC) return null
    return when (sourceType) {
        SourceType.KAVITA -> {
            when {
                isKavitaSeriesContainer() -> remoteId?.takeIf(String::isNotBlank)
                else -> kavitaSeriesGroupKey()
            }
        }

        SourceType.STUMP -> {
            when {
                isStumpSeriesContainer() -> remoteId?.takeIf(String::isNotBlank)
                else -> stumpSeriesGroupKey()
            }
        }

        SourceType.GRIMMORY,
        SourceType.LOCAL -> null
    }
}

fun ReadableItem.belongsToKavitaSeries(
    seriesKey: String,
): Boolean = kavitaSeriesGroupKey() == seriesKey

fun ReadableItem.belongsToStumpSeries(
    seriesKey: String,
): Boolean = stumpSeriesGroupKey() == seriesKey

fun buildKavitaSeriesContainer(
    seriesKey: String,
    issues: List<ReadableItem>,
): ReadableItem {
    val firstIssue = issues.first()
    val seriesTitle = issues.firstNotNullOfOrNull { issue ->
        issue.comicMetadata?.series?.takeIf(String::isNotBlank)
    } ?: firstIssue.title
    val issueCount = issues.size
    return ReadableItem(
        id = kavitaSeriesContainerId(seriesKey),
        sourceType = SourceType.KAVITA,
        libraryId = firstIssue.libraryId,
        remoteId = seriesKey,
        title = seriesTitle,
        format = firstIssue.format,
        kind = ReadableItemKind.COMIC,
        coverUrl = firstIssue.kavitaSeriesCoverUrl() ?: firstIssue.coverUrl,
        description = issues.firstNotNullOfOrNull { issue ->
            issue.description?.takeIf(String::isNotBlank)
        },
        addedAtEpochMillis = issues.mapNotNull(ReadableItem::addedAtEpochMillis).maxOrNull(),
        comicMetadata = ComicMetadata(
            series = seriesTitle,
            issueTitle = if (issueCount == 1) "1 issue" else "$issueCount issues",
        ),
    )
}

fun buildStumpSeriesContainer(
    seriesKey: String,
    issues: List<ReadableItem>,
): ReadableItem {
    val firstIssue = issues.first()
    val seriesTitle = issues.firstNotNullOfOrNull { issue ->
        issue.comicMetadata?.series?.takeIf(String::isNotBlank)
    } ?: firstIssue.title
    val issueCount = issues.size
    return ReadableItem(
        id = stumpSeriesContainerId(seriesKey),
        sourceType = SourceType.STUMP,
        libraryId = firstIssue.libraryId,
        remoteId = seriesKey,
        remoteFileId = firstIssue.remoteFileType,
        title = seriesTitle,
        format = firstIssue.format,
        kind = ReadableItemKind.COMIC,
        // Series containers should fetch the series thumbnail via the series id.
        coverUrl = null,
        description = issues.firstNotNullOfOrNull { issue ->
            issue.description?.takeIf(String::isNotBlank)
        },
        addedAtEpochMillis = issues.mapNotNull(ReadableItem::addedAtEpochMillis).maxOrNull(),
        comicMetadata = ComicMetadata(
            series = seriesTitle,
            issueTitle = if (issueCount == 1) "1 issue" else "$issueCount issues",
        ),
    )
}

private fun ReadableItem.kavitaSeriesCoverUrl(): String? {
    val seriesId = remoteFileType?.takeIf(String::isNotBlank) ?: return null
    val existingCoverUrl = coverUrl?.takeIf(String::isNotBlank) ?: return null
    val baseUrl = existingCoverUrl.substringBefore("/api/Image/", missingDelimiterValue = "")
        .takeIf(String::isNotBlank)
        ?: return null
    val apiKey = existingCoverUrl.substringAfter("apiKey=", missingDelimiterValue = "")
        .substringBefore('&')
        .takeIf(String::isNotBlank)
    return buildString {
        append(baseUrl.trimEnd('/'))
        append("/api/Image/series-cover?seriesId=")
        append(seriesId)
        apiKey?.let { key ->
            append("&apiKey=")
            append(key)
        }
    }
}
