package com.example.unifiedreader.data.kavita

import kotlin.math.abs

private const val KavitaInvalidChapterNumberMagnitudeThreshold = 10_000.0
private val GeneratedChapterTitlePattern = Regex(
    pattern = """(?i)^chapter\s+(-?\d+(?:\.\d+)?)$""",
)

internal fun preferredKavitaIssueTitle(
    explicitTitle: String?,
    volumeName: String?,
): String? = listOfNotNull(
    explicitTitle.normalizedKavitaChapterTitle(),
    volumeName.toKavitaVolumeTitle(),
).firstOrNull()

internal fun KavitaChapterInfoDto.preferredKavitaIssueTitleFallback(
    volumeName: String?,
): String? = listOfNotNull(
    chapterTitle.normalizedKavitaChapterTitle(),
    title.normalizedKavitaChapterInfoTitle(),
    volumeName.toKavitaVolumeTitle(),
    pageDimensions.orEmpty().firstNotNullOfOrNull(KavitaFileDimensionDto::displayFileTitle),
).firstOrNull()

private fun String?.normalizedKavitaChapterTitle(): String? {
    val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val generatedTitleToken = GeneratedChapterTitlePattern.matchEntire(normalized)
        ?.groupValues
        ?.getOrNull(1)
    if (generatedTitleToken != null && !isValidKavitaChapterToken(generatedTitleToken)) {
        return null
    }
    return normalized
}

private fun String?.normalizedKavitaChapterInfoTitle(): String? {
    val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return normalized.toKavitaArchiveDisplayTitle() ?: normalized.normalizedKavitaChapterTitle()
}

private fun String?.toKavitaVolumeTitle(): String? {
    val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return if (normalized.startsWith("vol", ignoreCase = true) ||
        normalized.startsWith("volume", ignoreCase = true)
    ) {
        normalized
    } else {
        "Volume $normalized"
    }
}

private fun KavitaFileDimensionDto.displayFileTitle(): String? = fileName.toKavitaArchiveDisplayTitle()

private fun String?.toKavitaArchiveDisplayTitle(): String? {
    val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val leafName = normalized
        .substringAfterLast('/', missingDelimiterValue = normalized)
        .substringAfterLast('\\', missingDelimiterValue = normalized)
        .trim()
        .takeIf(String::isNotEmpty)
        ?: return null
    val extension = leafName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf(String::isNotEmpty)
        ?: return null
    if (extension !in KavitaDisplayableArchiveExtensions) {
        return null
    }
    return leafName.substringBeforeLast('.', missingDelimiterValue = leafName)
        .trim()
        .takeIf(String::isNotEmpty)
}

internal fun isValidKavitaChapterToken(
    token: String,
): Boolean {
    val normalized = token.trim()
    val numericValue = normalized.toDoubleOrNull()
    return when {
        numericValue == null -> true
        numericValue < 0.0 -> false
        abs(numericValue) > KavitaInvalidChapterNumberMagnitudeThreshold -> false
        else -> true
    }
}

private val KavitaDisplayableArchiveExtensions = setOf(
    "cbz",
    "cbr",
    "cb7",
    "cbt",
    "zip",
    "rar",
    "7z",
    "pdf",
    "epub",
)
