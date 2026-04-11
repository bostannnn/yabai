package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.comicSeriesPreferenceKey
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.comics.ComicsReadingDirectionKey
import com.example.unifiedreader.feature.reader.comics.restoreComicReadingDirectionOverride
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.buildComicReadingDirectionReconciliationPlan
import com.example.unifiedreader.feature.reader.preferences.isEmpty
import com.example.unifiedreader.feature.reader.preferences.normalizeComicSeriesDirectionOverride
import com.example.unifiedreader.feature.reader.preferences.resolveInheritedComicReadingDirection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AppComicReadingDirectionController @Inject constructor(
    private val preferencesStore: ComicReadingDirectionPreferencesStore,
    private val readerSessionStore: ReaderSessionStore,
    private val readableItemRepository: ReadableItemRepository,
) {
    fun observePreferences(
        scope: CoroutineScope,
        updateState: AppUiStateUpdater,
    ) {
        scope.launch {
            preferencesStore.preferencesFlow.collect { preferences ->
                updateState { state ->
                    state.copy(comicReadingDirectionPreferences = preferences)
                }
            }
        }
        scope.launch {
            combine(
                preferencesStore.preferencesFlow,
                readableItemRepository.observeItems(),
                readerSessionStore.sessionByItemIdFlow,
            ) { preferences, items, sessionsByItemId ->
                buildComicReadingDirectionReconciliationPlan(
                    preferences = preferences,
                    items = items,
                    itemOverridesByItemId = sessionsByItemId.mapNotNullValues { _, session ->
                        restoreComicReadingDirectionOverride(session)
                    },
                )
            }.collect { plan ->
                if (plan.isEmpty) return@collect
                plan.seriesOverrideUpdatesByKey.forEach { (seriesKey, overrideDirection) ->
                    preferencesStore.setSeriesDirectionOverride(
                        seriesKey = seriesKey,
                        direction = overrideDirection,
                    )
                }
                plan.itemOverrideUpdatesByItemId.forEach { (itemId, overrideDirection) ->
                    readerSessionStore.updateSession(
                        itemId = itemId,
                        preferences = mapOf(
                            ComicsReadingDirectionKey to overrideDirection?.name.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    fun onLibraryDirectionSelected(
        scope: CoroutineScope,
        libraryId: String,
        direction: ComicReadingDirection,
    ) {
        scope.launch {
            preferencesStore.setLibraryDirection(
                libraryId = libraryId,
                direction = direction,
            )
        }
    }

    fun onSeriesDirectionSelected(
        scope: CoroutineScope,
        item: ReadableItem,
        direction: ComicReadingDirection,
    ) {
        val seriesKey = item.comicSeriesPreferenceKey() ?: return
        val normalizedOverride = normalizeComicSeriesDirectionOverride(
            item = item,
            selectedDirection = direction,
            preferences = preferencesStore.preferencesFlow.value,
        )
        scope.launch {
            preferencesStore.setSeriesDirectionOverride(
                seriesKey = seriesKey,
                direction = normalizedOverride,
            )
        }
    }

    fun effectiveSeriesDirection(
        item: ReadableItem,
        currentPreferences: ComicReadingDirectionPreferences,
    ): ComicReadingDirection? = item
        .takeIf { current -> current.isKavitaSeriesContainer() || current.isStumpSeriesContainer() }
        ?.comicSeriesPreferenceKey()
        ?.let {
            resolveInheritedComicReadingDirection(
                item = item,
                preferences = currentPreferences,
            )
        }
}

private inline fun <K, V, R : Any> Map<K, V>.mapNotNullValues(
    transform: (K, V) -> R?,
): Map<K, R> = buildMap {
    for ((key, value) in this@mapNotNullValues) {
        val nextValue = transform(key, value) ?: continue
        put(key, nextValue)
    }
}
