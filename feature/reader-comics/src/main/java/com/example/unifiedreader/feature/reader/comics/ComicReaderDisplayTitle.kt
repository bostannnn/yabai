package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.ReadableItem

internal fun comicReaderDisplayTitle(
    item: ReadableItem,
): String {
    val seriesTitle = item.comicMetadata?.series?.trim().orEmpty()
    val detailTitle = listOfNotNull(
        item.comicMetadata?.issueTitle?.trim()?.takeIf(String::isNotEmpty),
        item.comicMetadata?.volume?.trim()?.takeIf(String::isNotEmpty)?.let(::formatComicVolumeLabel),
        item.title.trim().takeIf(String::isNotEmpty),
    ).firstOrNull().orEmpty()
    return listOfNotNull(seriesTitle.takeIf(String::isNotEmpty), detailTitle.takeIf(String::isNotEmpty))
        .distinct()
        .joinToString(" • ")
        .ifBlank { item.title }
}

private fun formatComicVolumeLabel(
    volume: String,
): String = if (volume.startsWith("vol", ignoreCase = true) ||
    volume.startsWith("volume", ignoreCase = true)
) {
    volume
} else {
    "Volume $volume"
}
