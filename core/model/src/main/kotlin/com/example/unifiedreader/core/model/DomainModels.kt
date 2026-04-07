package com.example.unifiedreader.core.model

enum class SourceType {
    KAVITA,
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
    val serverId: String,
    val sourceType: SourceType,
    val title: String,
)

data class ReadableItem(
    val id: String,
    val serverId: String,
    val sourceType: SourceType,
    val libraryId: String,
    val isLocalImport: Boolean = false,
    val title: String,
    val format: FormatType,
    val kind: ReadableItemKind = format.defaultReadableItemKind(),
    val pageCount: Int? = null,
    val coverUrl: String? = null,
    val description: String? = null,
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
