package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted

class ComicsReaderSessionAdapter(
    private val sessionStore: ReaderSessionStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ComicReaderProgressStore, ComicReaderPreferencesStore {
    override val isHydratedFlow: StateFlow<Boolean> = sessionStore.isHydratedFlow

    override val savedPagesFlow: StateFlow<Map<String, Int>> = sessionStore.progressByItemIdFlow
        .map { progressByItemId ->
            progressByItemId.mapNotNullValues { _, snapshot ->
                snapshot.page?.takeIf { snapshot.locatorType == LocatorType.PAGE }
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap(),
        )

    override val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>> =
        sessionStore.sessionByItemIdFlow
            .map { sessionByItemId ->
                sessionByItemId.mapNotNullValues { _, snapshot ->
                    restoreComicReaderPreferences(snapshot)
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    override fun loadPage(item: ReadableItem): Int? = sessionStore.getProgress(item)?.page

    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
    ) {
        val safePageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        sessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = safePageIndex,
            ),
            isFinished = pageCount > 0 && safePageIndex >= pageCount - 1,
            syncState = if (item.sourceType == SourceType.GRIMMORY) {
                SyncState.DIRTY
            } else {
                SyncState.CLEAN
            },
            pageCount = pageCount.coerceAtLeast(0),
        )
    }

    override fun loadReadingDirection(item: ReadableItem): ComicReadingDirection =
        restoreComicReaderPreferences(sessionStore.getSession(item))
            ?.readingDirection
            ?: ComicReadingDirection.LTR

    override fun saveReadingDirection(
        item: ReadableItem,
        direction: ComicReadingDirection,
    ) {
        sessionStore.updateSession(
            item = item,
            preferences = buildComicReaderPreferencesPatch(
                readingDirection = direction,
            ),
        )
    }

    override fun loadPageFitMode(item: ReadableItem): ComicPageFitMode =
        restoreComicReaderPreferences(sessionStore.getSession(item))
            ?.pageFitMode
            ?: ComicPageFitMode.FitPage

    override fun savePageFitMode(
        item: ReadableItem,
        fitMode: ComicPageFitMode,
    ) {
        sessionStore.updateSession(
            item = item,
            preferences = buildComicReaderPreferencesPatch(
                pageFitMode = fitMode,
            ),
        )
    }

    override fun loadSpreadMode(item: ReadableItem): ComicSpreadMode =
        restoreComicReaderPreferences(sessionStore.getSession(item))
            ?.spreadMode
            ?: ComicSpreadMode.Auto

    override fun saveSpreadMode(
        item: ReadableItem,
        spreadMode: ComicSpreadMode,
    ) {
        sessionStore.updateSession(
            item = item,
            preferences = buildComicReaderPreferencesPatch(
                spreadMode = spreadMode,
            ),
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
