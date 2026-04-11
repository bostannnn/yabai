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

fun buildLibraryDirectionNormalizationPlan(
    libraryId: String,
    selectedDirection: ComicReadingDirection,
    preferences: ComicReadingDirectionPreferences,
    items: List<ReadableItem>,
    itemOverridesByItemId: Map<String, ComicReadingDirection>,
): LibraryDirectionNormalizationPlan {
    val seriesKeysInLibrary = buildSet {
        preferences.seriesDirectionsByKey.keys
            .asSequence()
            .filter { key -> key.startsWith("$libraryId::") }
            .forEach(::add)
        items.asSequence()
            .filter { item -> item.libraryId == libraryId && item.kind == ReadableItemKind.COMIC }
            .mapNotNull(ReadableItem::comicSeriesPreferenceKey)
            .forEach(::add)
    }
    val seriesOverrideUpdatesByKey = seriesKeysInLibrary
        .mapNotNull { seriesKey ->
            val currentOverride = preferences.seriesDirectionsByKey[seriesKey] ?: return@mapNotNull null
            val normalizedOverride = currentOverride.takeUnless { it == selectedDirection }
            if (normalizedOverride == currentOverride) {
                null
            } else {
                seriesKey to normalizedOverride
            }
        }
        .toMap()

    val nextSeriesDirectionsByKey = preferences.seriesDirectionsByKey
        .toMutableMap()
        .apply {
            seriesOverrideUpdatesByKey.forEach { (seriesKey, overrideDirection) ->
                if (overrideDirection == null) {
                    remove(seriesKey)
                } else {
                    put(seriesKey, overrideDirection)
                }
            }
        }
    val nextPreferences = preferences.copy(
        libraryDirectionsById = preferences.libraryDirectionsById + (libraryId to selectedDirection),
        seriesDirectionsByKey = nextSeriesDirectionsByKey,
    )
    val itemOverrideUpdatesByItemId = items.asSequence()
        .filter { item -> item.libraryId == libraryId && item.kind == ReadableItemKind.COMIC }
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
