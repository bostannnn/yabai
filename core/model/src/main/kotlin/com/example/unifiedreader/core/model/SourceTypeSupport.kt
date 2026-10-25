package com.example.unifiedreader.core.model

fun SourceType.displayName(): String = when (this) {
    SourceType.KAVITA -> "Kavita"
    SourceType.STUMP -> "Stump"
    SourceType.GRIMMORY -> "Grimmory"
    SourceType.LOCAL -> "Local"
}

/**
 * An EPUB's printed page count, which Grimmory's book metadata gives. Kavita's page count for an
 * EPUB is its number of files and a local EPUB has none, so neither is a length in pages.
 */
fun ReadableItem.printedPageCount(): Int? = pageCount
    ?.takeIf { count -> count > 0 && format == FormatType.EPUB && sourceType == SourceType.GRIMMORY }
