package com.example.unifiedreader.feature.reader.preferences

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.comicSeriesPreferenceKey

fun resolveInheritedComicReaderMode(
    item: ReadableItem,
    preferences: ComicReaderModePreferences,
): ComicReaderMode = item.comicSeriesPreferenceKey()
    ?.let(preferences.seriesModesByKey::get)
    ?: preferences.libraryModesById[item.libraryId]
    ?: ComicReaderMode.Paged

fun resolveComicReaderMode(
    item: ReadableItem,
    itemOverride: ComicReaderMode?,
    preferences: ComicReaderModePreferences,
): ComicReaderMode = itemOverride ?: resolveInheritedComicReaderMode(
    item = item,
    preferences = preferences,
)

fun normalizeComicSeriesModeOverride(
    item: ReadableItem,
    selectedMode: ComicReaderMode,
    preferences: ComicReaderModePreferences,
): ComicReaderMode? {
    val inheritedMode = preferences.libraryModesById[item.libraryId] ?: ComicReaderMode.Paged
    return selectedMode.takeUnless { it == inheritedMode }
}

fun normalizeComicItemModeOverride(
    item: ReadableItem,
    selectedMode: ComicReaderMode,
    preferences: ComicReaderModePreferences,
): ComicReaderMode? {
    val inheritedMode = resolveInheritedComicReaderMode(
        item = item,
        preferences = preferences,
    )
    return selectedMode.takeUnless { it == inheritedMode }
}
