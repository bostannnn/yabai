package com.example.unifiedreader.core.model

val BookReaderFormats: Set<FormatType> = setOf(
    FormatType.EPUB,
    FormatType.PDF,
)

val ComicReaderFormats: Set<FormatType> = setOf(
    FormatType.CBZ,
    FormatType.CBR,
)

val AppReadableFormats: Set<FormatType> = BookReaderFormats + ComicReaderFormats

fun FormatType.isBookReaderFormat(): Boolean = this in BookReaderFormats

fun FormatType.isComicReaderFormat(): Boolean = this in ComicReaderFormats

fun String?.toReadableFormatTypeOrNull(): FormatType? {
    val normalized = this?.trim()
        ?.removePrefix(".")
        ?.uppercase()
        ?: return null
    return when (normalized) {
        "EPUB" -> FormatType.EPUB
        "PDF" -> FormatType.PDF
        "CBZ", "ZIP" -> FormatType.CBZ
        "CBR", "RAR" -> FormatType.CBR
        else -> null
    }
}

fun readableFormatFromTypeOrExtension(
    type: String?,
    extension: String?,
    fileName: String? = null,
): FormatType? {
    return type.toReadableFormatTypeOrNull()
        ?: extension.toReadableFormatTypeOrNull()
        ?: fileName?.substringAfterLast('.', missingDelimiterValue = "")
            .toReadableFormatTypeOrNull()
}
