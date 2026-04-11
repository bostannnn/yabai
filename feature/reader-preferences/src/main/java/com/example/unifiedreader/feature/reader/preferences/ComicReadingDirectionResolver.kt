package com.example.unifiedreader.feature.reader.preferences

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.comicSeriesPreferenceKey

data class ComicReadingDirectionPreferences(
    val libraryDirectionsById: Map<String, ComicReadingDirection> = emptyMap(),
    val seriesDirectionsByKey: Map<String, ComicReadingDirection> = emptyMap(),
)

fun resolveInheritedComicReadingDirection(
    item: ReadableItem,
    preferences: ComicReadingDirectionPreferences,
): ComicReadingDirection = item.comicSeriesPreferenceKey()
    ?.let(preferences.seriesDirectionsByKey::get)
    ?: preferences.libraryDirectionsById[item.libraryId]
    ?: ComicReadingDirection.LTR

fun resolveComicReadingDirection(
    item: ReadableItem,
    itemOverride: ComicReadingDirection?,
    preferences: ComicReadingDirectionPreferences,
): ComicReadingDirection = itemOverride ?: resolveInheritedComicReadingDirection(
    item = item,
    preferences = preferences,
)

fun normalizeComicSeriesDirectionOverride(
    item: ReadableItem,
    selectedDirection: ComicReadingDirection,
    preferences: ComicReadingDirectionPreferences,
): ComicReadingDirection? {
    val inheritedDirection = preferences.libraryDirectionsById[item.libraryId] ?: ComicReadingDirection.LTR
    return selectedDirection.takeUnless { it == inheritedDirection }
}

fun normalizeComicItemDirectionOverride(
    item: ReadableItem,
    selectedDirection: ComicReadingDirection,
    preferences: ComicReadingDirectionPreferences,
): ComicReadingDirection? {
    val inheritedDirection = resolveInheritedComicReadingDirection(
        item = item,
        preferences = preferences,
    )
    return selectedDirection.takeUnless { it == inheritedDirection }
}
