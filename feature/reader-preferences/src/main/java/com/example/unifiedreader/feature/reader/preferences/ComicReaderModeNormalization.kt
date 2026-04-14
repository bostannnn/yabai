package com.example.unifiedreader.feature.reader.preferences

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.comicSeriesPreferenceKey

data class LibraryModeNormalizationPlan(
    val seriesOverrideUpdatesByKey: Map<String, ComicReaderMode?> = emptyMap(),
    val itemOverrideUpdatesByItemId: Map<String, ComicReaderMode?> = emptyMap(),
)

val LibraryModeNormalizationPlan.isEmpty: Boolean
    get() = seriesOverrideUpdatesByKey.isEmpty() && itemOverrideUpdatesByItemId.isEmpty()

fun buildComicReaderModeReconciliationPlan(
    preferences: ComicReaderModePreferences,
    items: List<ReadableItem>,
    itemOverridesByItemId: Map<String, ComicReaderMode>,
): LibraryModeNormalizationPlan {
    val seriesOverrideUpdatesByKey = preferences.seriesModesByKey
        .mapNotNull { (seriesKey, currentOverride) ->
            val libraryId = seriesKey.preferenceLibraryId() ?: return@mapNotNull null
            val inheritedMode = preferences.libraryModesById[libraryId] ?: ComicReaderMode.Paged
            if (currentOverride == inheritedMode) {
                seriesKey to null
            } else {
                null
            }
        }
        .toMap()

    val nextPreferences = preferences.copy(
        seriesModesByKey = preferences.seriesModesByKey - seriesOverrideUpdatesByKey.keys,
    )
    val itemOverrideUpdatesByItemId = items.asSequence()
        .filter { item -> item.kind == ReadableItemKind.COMIC }
        .mapNotNull { item ->
            val currentOverride = itemOverridesByItemId[item.id] ?: return@mapNotNull null
            val normalizedOverride = normalizeComicItemModeOverride(
                item = item,
                selectedMode = currentOverride,
                preferences = nextPreferences,
            )
            if (normalizedOverride == currentOverride) {
                null
            } else {
                item.id to normalizedOverride
            }
        }
        .toMap()

    return LibraryModeNormalizationPlan(
        seriesOverrideUpdatesByKey = seriesOverrideUpdatesByKey,
        itemOverrideUpdatesByItemId = itemOverrideUpdatesByItemId,
    )
}

private fun String.preferenceLibraryId(): String? = substringBefore(
    delimiter = "::",
    missingDelimiterValue = "",
).takeIf { contains("::") && it.isNotBlank() }
