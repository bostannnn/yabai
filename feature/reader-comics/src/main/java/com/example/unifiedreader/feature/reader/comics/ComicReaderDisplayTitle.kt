package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.comicVolumeLabel
import com.example.unifiedreader.core.model.comicVolumeNumber
import com.example.unifiedreader.core.model.isGeneratedComicVolumeTitle
import com.example.unifiedreader.core.model.ownIssueTitle
import com.example.unifiedreader.core.model.volumeNumber

/** The comics reader's top bar: the issue ("Neon Ronin #2") over where it sits ("Neon Ronin · issue 2"). */
internal data class ComicReaderTitles(
    val title: String,
    val subtitle: String?,
)

/**
 * The issue's name for the top bar and, under it, the first thing that says more without
 * repeating it: the issue's own title, its volume, its number in the series, or the file's name.
 * Kavita's generated names ("Chapter v1") and impossible numbers ("-100000") are left out.
 */
internal fun comicReaderTitles(item: ReadableItem): ComicReaderTitles {
    val metadata = item.comicMetadata
    val series = metadata?.series?.trim()?.takeIf(String::isNotEmpty)
    val issueNumber = metadata?.issueNumber?.trim()
        ?.takeIf { number -> number.isNotEmpty() && !number.startsWith("-") && comicVolumeNumber(number) == null }
    val volume = metadata?.volume?.trim()?.takeIf(String::isNotEmpty)?.let(::formatComicVolumeLabel)
        ?: metadata?.volumeNumber()?.let(::comicVolumeLabel)
    val ownTitle = metadata?.ownIssueTitle()
    val fileTitle = item.title.trim()
        .takeIf(String::isNotEmpty)
        ?.takeUnless(::isGeneratedComicVolumeTitle)
        ?.takeUnless { title -> GeneratedChapterTitle.matches(title) }
    val title = when {
        series != null && issueNumber != null -> "$series #$issueNumber"
        series != null -> series
        else -> ownTitle ?: fileTitle ?: item.title
    }
    val subtitle = listOfNotNull(
        ownTitle,
        volume,
        if (series != null && issueNumber != null) "$series · issue $issueNumber" else null,
        fileTitle,
    ).firstOrNull { line -> !line.equals(title, ignoreCase = true) }
    return ComicReaderTitles(title = title, subtitle = subtitle)
}

private fun formatComicVolumeLabel(
    volume: String,
): String = when {
    volume.startsWith("vol", ignoreCase = true) -> volume
    // Western comics' ComicInfo keeps the series' first year in Volume: it reads as the year.
    ComicSeriesYear.matches(volume) -> volume
    else -> "Volume $volume"
}

private val ComicSeriesYear = Regex("""^(19|20)\d{2}$""")

// Kavita's placeholder for a chapter without a title, with an impossible number ("Chapter -100000").
private val GeneratedChapterTitle = Regex("""(?i)^chapter\s+-\d+(\.\d+)?$""")
