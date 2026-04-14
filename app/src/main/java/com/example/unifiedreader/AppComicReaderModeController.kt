package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.comicSeriesPreferenceKey
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.comics.ComicsReaderModeKey
import com.example.unifiedreader.feature.reader.comics.restoreComicReaderModeOverride
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.buildComicReaderModeReconciliationPlan
import com.example.unifiedreader.feature.reader.preferences.isEmpty
import com.example.unifiedreader.feature.reader.preferences.normalizeComicSeriesModeOverride
import com.example.unifiedreader.feature.reader.preferences.resolveInheritedComicReaderMode
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AppComicReaderModeController @Inject constructor(
    private val preferencesStore: ComicReaderModePreferencesStore,
    private val readerSessionStore: ReaderSessionStore,
    private val readableItemRepository: ReadableItemRepository,
) {
    fun observePreferences(
        scope: CoroutineScope,
        updateState: AppUiStateUpdater,
    ): Job {
        val observationJob = SupervisorJob(scope.coroutineContext[Job])
        val observationScope = CoroutineScope(scope.coroutineContext + observationJob)
        observationScope.launch {
            preferencesStore.preferencesFlow.collect { preferences ->
                updateState { state ->
                    state.copy(comicReaderModePreferences = preferences)
                }
            }
        }
        observationScope.launch {
            combine(
                preferencesStore.preferencesFlow,
                readableItemRepository.observeItems(),
                readerSessionStore.sessionByItemIdFlow,
            ) { preferences, items, sessionsByItemId ->
                buildComicReaderModeReconciliationPlan(
                    preferences = preferences,
                    items = items,
                    itemOverridesByItemId = sessionsByItemId.mapNotNullValues { _, session ->
                        restoreComicReaderModeOverride(session)
                    },
                )
            }.collect { plan ->
                if (plan.isEmpty) return@collect
                plan.seriesOverrideUpdatesByKey.forEach { (seriesKey, overrideMode) ->
                    preferencesStore.setSeriesModeOverride(
                        seriesKey = seriesKey,
                        mode = overrideMode,
                    )
                }
                plan.itemOverrideUpdatesByItemId.forEach { (itemId, overrideMode) ->
                    readerSessionStore.updateSession(
                        itemId = itemId,
                        preferences = mapOf(
                            ComicsReaderModeKey to overrideMode?.name.orEmpty(),
                        ),
                    )
                }
            }
        }
        return observationJob
    }

    fun onLibraryModeSelected(
        scope: CoroutineScope,
        libraryId: String,
        mode: ComicReaderMode,
    ) {
        scope.launch {
            preferencesStore.setLibraryMode(
                libraryId = libraryId,
                mode = mode,
            )
        }
    }

    fun onSeriesModeSelected(
        scope: CoroutineScope,
        item: ReadableItem,
        mode: ComicReaderMode,
    ) {
        val seriesKey = item.comicSeriesPreferenceKey() ?: return
        val normalizedOverride = normalizeComicSeriesModeOverride(
            item = item,
            selectedMode = mode,
            preferences = preferencesStore.preferencesFlow.value,
        )
        scope.launch {
            preferencesStore.setSeriesModeOverride(
                seriesKey = seriesKey,
                mode = normalizedOverride,
            )
        }
    }

    fun effectiveSeriesMode(
        item: ReadableItem,
        currentPreferences: ComicReaderModePreferences,
    ): ComicReaderMode? = item
        .takeIf { current -> current.isKavitaSeriesContainer() || current.isStumpSeriesContainer() }
        ?.comicSeriesPreferenceKey()
        ?.let {
            resolveInheritedComicReaderMode(
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
