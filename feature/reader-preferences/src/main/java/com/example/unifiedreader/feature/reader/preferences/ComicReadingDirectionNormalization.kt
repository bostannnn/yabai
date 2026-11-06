package com.example.unifiedreader.feature.reader.preferences

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.comicSeriesPreferenceKey

data class LibraryDirectionNormalizationPlan(
    val seriesOverrideUpdatesByKey: Map<String, ComicReadingDirection?> = emptyMap(),
    val itemOverrideUpdatesByItemId: Map<String, ComicReadingDirection?> = emptyMap(),
)

val LibraryDirectionNormalizationPlan.isEmpty: Boolean
    get() = seriesOverrideUpdatesByKey.isEmpty() && itemOverrideUpdatesByItemId.isEmpty()

fun buildComicReadingDirectionReconciliationPlan(
    preferences: ComicReadingDirectionPreferences,
    items: List<ReadableItem>,
    itemOverridesByItemId: Map<String, ComicReadingDirection>,
): LibraryDirectionNormalizationPlan {
    val seriesOverrideUpdatesByKey = preferences.seriesDirectionsByKey
        .mapNotNull { (seriesKey, currentOverride) ->
            val libraryId = seriesKey.preferenceLibraryId() ?: return@mapNotNull null
            val inheritedDirection = preferences.libraryDirectionsById[libraryId] ?: ComicReadingDirection.LTR
            if (currentOverride == inheritedDirection) {
                seriesKey to null
            } else {
                null
            }
        }
        .toMap()

    val nextPreferences = preferences.copy(
        seriesDirectionsByKey = preferences.seriesDirectionsByKey - seriesOverrideUpdatesByKey.keys,
    )
    val itemOverrideUpdatesByItemId = items.asSequence()
        .filter { item -> item.kind == ReadableItemKind.COMIC }
        .mapNotNull { item ->
            val currentOverride = itemOverridesByItemId[item.id] ?: return@mapNotNull null
            val normalizedOverride = normalizeComicItemDirectionOverride(
                item = item,
                selectedDirection = currentOverride,
                preferences = nextPreferences,
            )
            if (normalizedOverride == currentOverride) {
                null
            } else {
                item.id to normalizedOverride
            }
        }
        .toMap()

    return LibraryDirectionNormalizationPlan(
        seriesOverrideUpdatesByKey = seriesOverrideUpdatesByKey,
        itemOverrideUpdatesByItemId = itemOverrideUpdatesByItemId,
    )
}

private fun String.preferenceLibraryId(): String? = substringBefore(
    delimiter = "::",
    missingDelimiterValue = "",
).takeIf { contains("::") && it.isNotBlank() }
