package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.enumValueOrNull

const val ComicsReadingDirectionKey = "readingDirection"
const val ComicsPageFitModeKey = "pageFitMode"
const val ComicsSpreadModeKey = "spreadMode"

fun restoreComicReaderPreferences(
    session: ReaderSessionSnapshot?,
): ComicReaderPreferencesSnapshot? = restoreComicReaderPreferences(
    preferences = session?.preferences.orEmpty(),
)

fun restoreComicReaderPreferences(
    preferences: Map<String, String>,
): ComicReaderPreferencesSnapshot? {
    val hasKnownPreference = preferences.containsKey(ComicsReadingDirectionKey) ||
        preferences.containsKey(ComicsPageFitModeKey) ||
        preferences.containsKey(ComicsSpreadModeKey)
    if (!hasKnownPreference) return null
    return ComicReaderPreferencesSnapshot(
        readingDirection = preferences[ComicsReadingDirectionKey]
            ?.enumValueOrNull<ComicReadingDirection>()
            ?: ComicReadingDirection.LTR,
        pageFitMode = preferences[ComicsPageFitModeKey]
            ?.enumValueOrNull<ComicPageFitMode>()
            ?: ComicPageFitMode.FitPage,
        spreadMode = preferences[ComicsSpreadModeKey]
            ?.enumValueOrNull<ComicSpreadMode>()
            ?: ComicSpreadMode.Auto,
    )
}

fun buildComicReaderPreferencesMap(
    preferences: ComicReaderPreferencesSnapshot,
): Map<String, String> = mapOf(
    ComicsReadingDirectionKey to preferences.readingDirection.name,
    ComicsPageFitModeKey to preferences.pageFitMode.name,
    ComicsSpreadModeKey to preferences.spreadMode.name,
)

fun buildComicReaderPreferencesPatch(
    readingDirection: ComicReadingDirection? = null,
    pageFitMode: ComicPageFitMode? = null,
    spreadMode: ComicSpreadMode? = null,
): Map<String, String> = buildMap {
    readingDirection?.let { put(ComicsReadingDirectionKey, it.name) }
    pageFitMode?.let { put(ComicsPageFitModeKey, it.name) }
    spreadMode?.let { put(ComicsSpreadModeKey, it.name) }
}
