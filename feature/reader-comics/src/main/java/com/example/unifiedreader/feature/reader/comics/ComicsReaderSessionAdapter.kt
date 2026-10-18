package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.normalizeComicItemModeOverride
import com.example.unifiedreader.feature.reader.preferences.normalizeComicItemDirectionOverride
import com.example.unifiedreader.feature.reader.preferences.resolveComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.resolveComicReadingDirection
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class ComicsReaderSessionAdapter(
    private val sessionStore: ReaderSessionStore,
    private val directionPreferencesStore: ComicReadingDirectionPreferencesStore,
    private val readerModePreferencesStore: ComicReaderModePreferencesStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ComicReaderProgressStore, ComicReaderPreferencesStore {
    override val isHydratedFlow: StateFlow<Boolean> = combine(
        sessionStore.isHydratedFlow,
        directionPreferencesStore.isHydratedFlow,
        readerModePreferencesStore.isHydratedFlow,
    ) { sessionHydrated, directionPreferencesHydrated, readerModePreferencesHydrated ->
        sessionHydrated && directionPreferencesHydrated && readerModePreferencesHydrated
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    override val savedPagesFlow: StateFlow<Map<String, Int>> = sessionStore.progressByItemIdFlow
        .map { progressByItemId ->
            progressByItemId.mapNotNullValues { _, snapshot ->
                savedComicReaderPage(snapshot)
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap(),
        )

    override val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>> =
        combine(
            sessionStore.sessionByItemIdFlow,
            sessionStore.progressByItemIdFlow,
        ) { sessionByItemId, progressByItemId ->
                sessionByItemId.mapNotNullValues { _, snapshot ->
                    effectiveComicReaderPreferences(
                        session = snapshot,
                        progress = progressByItemId[snapshot.itemId],
                    )
                }
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    override fun loadPage(item: ReadableItem): Int? = savedComicReaderPage(sessionStore.getProgress(item))

    override fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
        isFinished: Boolean,
    ) {
        val safePageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        sessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = safePageIndex,
            ),
            isFinished = isFinished,
            syncState = if (item.sourceType == SourceType.LOCAL) {
                SyncState.CLEAN
            } else {
                SyncState.DIRTY
            },
            pageCount = pageCount.coerceAtLeast(0),
        )
    }

    override suspend fun flushPendingWrites() {
        sessionStore.flushPendingWrites()
    }

    override fun requestFlushPendingWrites() {
        scope.launch {
            sessionStore.flushPendingWrites()
        }
    }

    override fun loadReadingDirection(item: ReadableItem): ComicReadingDirection =
        resolveComicReadingDirection(
            item = item,
            itemOverride = restoreComicReadingDirectionOverride(sessionStore.getSession(item)),
            preferences = directionPreferencesStore.preferencesFlow.value,
        )

    override fun loadReaderMode(item: ReadableItem): ComicReaderMode =
        resolveComicReaderMode(
            item = item,
            itemOverride = restoreComicReaderModeOverride(sessionStore.getSession(item)),
            preferences = readerModePreferencesStore.preferencesFlow.value,
        )

    override fun observePreferences(
        item: ReadableItem,
    ) = combine(
        sessionStore.sessionByItemIdFlow,
        sessionStore.progressByItemIdFlow,
        directionPreferencesStore.preferencesFlow,
        readerModePreferencesStore.preferencesFlow,
    ) { sessionByItemId, progressByItemId, directionPreferences, readerModePreferences ->
        sessionByItemId[item.id]
            .toResolvedComicReaderPreferences(
                item = item,
                progress = progressByItemId[item.id],
                directionPreferences = directionPreferences,
                readerModePreferences = readerModePreferences,
            )
    }.distinctUntilChanged()

    override fun saveReadingDirection(
        item: ReadableItem,
        direction: ComicReadingDirection,
    ) {
        val normalizedOverride = normalizeComicItemDirectionOverride(
            item = item,
            selectedDirection = direction,
            preferences = directionPreferencesStore.preferencesFlow.value,
        )
        sessionStore.updateSession(
            item = item,
            preferences = mapOf(
                ComicsReadingDirectionKey to normalizedOverride?.name.orEmpty(),
            ),
        )
    }

    override fun saveReaderMode(
        item: ReadableItem,
        mode: ComicReaderMode,
    ) {
        val normalizedOverride = normalizeComicItemModeOverride(
            item = item,
            selectedMode = mode,
            preferences = readerModePreferencesStore.preferencesFlow.value,
        )
        sessionStore.updateSession(
            item = item,
            preferences = mapOf(
                ComicsReaderModeKey to normalizedOverride?.name.orEmpty(),
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

    override fun loadPageTurnStyle(item: ReadableItem): ComicPageTurnStyle =
        restoreComicReaderPreferences(sessionStore.getSession(item))
            ?.pageTurnStyle
            ?: ComicPageTurnStyle.Slide

    override fun savePageTurnStyle(
        item: ReadableItem,
        pageTurnStyle: ComicPageTurnStyle,
    ) {
        sessionStore.updateSession(
            item = item,
            preferences = buildComicReaderPreferencesPatch(
                pageTurnStyle = pageTurnStyle,
            ),
        )
    }

    override fun loadSplitWidePages(item: ReadableItem): Boolean =
        restoreComicReaderPreferences(sessionStore.getSession(item))
            ?.splitWidePages
            ?: false

    override fun saveSplitWidePages(
        item: ReadableItem,
        enabled: Boolean,
    ) {
        sessionStore.updateSession(
            item = item,
            preferences = buildComicReaderPreferencesPatch(
                splitWidePages = enabled,
            ),
        )
    }

    override fun loadResumeAnchorPage(item: ReadableItem): Int? =
        effectiveComicReaderPreferences(
            session = sessionStore.getSession(item),
            progress = sessionStore.getProgress(item),
        )
            ?.resumeAnchorPage

    override fun saveResumeAnchorPage(
        item: ReadableItem,
        pageIndex: Int?,
        progressPage: Int?,
    ) {
        sessionStore.updateSession(
            item = item,
            preferences = mapOf(
                ComicsResumeAnchorPageKey to pageIndex?.toString().orEmpty(),
                ComicsResumeProgressPageKey to progressPage?.toString().orEmpty(),
            ),
        )
    }

    override fun loadResumeSegment(item: ReadableItem): ComicPageSegment? =
        effectiveComicReaderPreferences(
            session = sessionStore.getSession(item),
            progress = sessionStore.getProgress(item),
        )
            ?.resumeSegment

    override fun saveResumeSegment(
        item: ReadableItem,
        segment: ComicPageSegment?,
    ) {
        sessionStore.updateSession(
            item = item,
            preferences = mapOf(
                ComicsResumeSegmentKey to segment?.name.orEmpty(),
            ),
        )
    }

    override fun loadResumeScrollOffsetRatio(item: ReadableItem): Float? =
        effectiveComicReaderPreferences(
            session = sessionStore.getSession(item),
            progress = sessionStore.getProgress(item),
        )
            ?.resumeScrollOffsetRatio

    override fun saveResumeScrollOffsetRatio(
        item: ReadableItem,
        ratio: Float?,
    ) {
        sessionStore.updateSession(
            item = item,
            preferences = mapOf(
                ComicsResumeScrollOffsetRatioKey to ratio?.toString().orEmpty(),
            ),
        )
    }
}

private fun com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot?.toResolvedComicReaderPreferences(
    item: ReadableItem,
    progress: ReaderProgressSnapshot?,
    directionPreferences: ComicReadingDirectionPreferences,
    readerModePreferences: ComicReaderModePreferences,
): ComicReaderPreferencesSnapshot? {
    val restoredPreferences = effectiveComicReaderPreferences(
        session = this,
        progress = progress,
    )
        ?: ComicReaderPreferencesSnapshot(
            readerMode = ComicReaderMode.Paged,
            readingDirection = ComicReadingDirection.LTR,
            pageFitMode = ComicPageFitMode.FitPage,
            spreadMode = ComicSpreadMode.Auto,
            pageTurnStyle = ComicPageTurnStyle.Slide,
            splitWidePages = false,
            resumeAnchorPage = null,
            resumeSegment = null,
            resumeScrollOffsetRatio = null,
        )
    return restoredPreferences.copy(
        readerMode = resolveComicReaderMode(
            item = item,
            itemOverride = restoreComicReaderModeOverride(this),
            preferences = readerModePreferences,
        ),
        readingDirection = resolveComicReadingDirection(
            item = item,
            itemOverride = restoreComicReadingDirectionOverride(this),
            preferences = directionPreferences,
        ),
    )
}

private inline fun <K, V, R : Any> Map<K, V>.mapNotNullValues(
    transform: (K, V) -> R?,
): Map<K, R> = buildMap {
    for ((key, value) in this@mapNotNullValues) {
        val nextValue = transform(key, value) ?: continue
        put(key, nextValue)
    }
}
