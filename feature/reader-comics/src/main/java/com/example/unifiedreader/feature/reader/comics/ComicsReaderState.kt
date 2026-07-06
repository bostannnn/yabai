package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.graphics.ImageBitmap
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class ComicPageSpec(
    val id: String,
    val pageIndex: Int,
    val contentDescription: String,
)

data class ComicPageMetadata(
    val width: Int,
    val height: Int,
) {
    val aspectRatio: Float
        get() = if (height > 0) width / height.toFloat() else 1f

    fun isWide(
        threshold: Float = DefaultWidePageAspectRatioThreshold,
    ): Boolean = width > 0 && height > 0 && aspectRatio >= threshold

    companion object {
        const val DefaultWidePageAspectRatioThreshold = 1.15f
    }
}

data class ComicPagePreviewOptions(
    val targetLongEdge: Int = 220,
    val maxPixelCount: Int = 90_000,
) {
    init {
        require(targetLongEdge > 0) { "targetLongEdge must be > 0" }
        require(maxPixelCount > 0) { "maxPixelCount must be > 0" }
    }
}

sealed interface ComicPageLoadResult {
    data class Success(
        val image: ImageBitmap,
    ) : ComicPageLoadResult

    data class Failure(
        val message: String,
    ) : ComicPageLoadResult
}

sealed interface ComicPagePreviewResult {
    data class Success(
        val image: ImageBitmap,
    ) : ComicPagePreviewResult

    data object Unavailable : ComicPagePreviewResult
}

data class ComicReaderPreferencesSnapshot(
    val readerMode: ComicReaderMode,
    val readingDirection: ComicReadingDirection,
    val pageFitMode: ComicPageFitMode,
    val spreadMode: ComicSpreadMode,
    val pageTurnStyle: ComicPageTurnStyle = ComicPageTurnStyle.Slide,
    val splitWidePages: Boolean,
    val resumeAnchorPage: Int? = null,
    val resumeSegment: ComicPageSegment?,
    val resumeScrollOffsetRatio: Float? = null,
)

enum class ComicPageLoadPriority {
    Visible,
    Prefetch,
}

interface ComicReaderProgressStore {
    val isHydratedFlow: StateFlow<Boolean>

    val savedPagesFlow: StateFlow<Map<String, Int>>

    fun loadPage(item: ReadableItem): Int?

    fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
        isFinished: Boolean = pageCount > 0 && pageIndex >= pageCount - 1,
    )

    suspend fun flushPendingWrites() = Unit

    fun requestFlushPendingWrites() = Unit
}

interface ComicReaderPreferencesStore {
    val isHydratedFlow: StateFlow<Boolean>

    val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>>

    fun observePreferences(
        item: ReadableItem,
    ): Flow<ComicReaderPreferencesSnapshot?> = savedPreferencesFlow.map { preferencesByItemId ->
        preferencesByItemId[item.id]
    }

    fun loadReadingDirection(
        item: ReadableItem,
    ): ComicReadingDirection = ComicReadingDirection.LTR

    fun saveReadingDirection(
        item: ReadableItem,
        direction: ComicReadingDirection,
    ) = Unit

    fun loadReaderMode(
        item: ReadableItem,
    ): ComicReaderMode = ComicReaderMode.Paged

    fun saveReaderMode(
        item: ReadableItem,
        mode: ComicReaderMode,
    ) = Unit

    fun loadPageFitMode(
        item: ReadableItem,
    ): ComicPageFitMode = ComicPageFitMode.FitPage

    fun savePageFitMode(
        item: ReadableItem,
        fitMode: ComicPageFitMode,
    ) = Unit

    fun loadSpreadMode(
        item: ReadableItem,
    ): ComicSpreadMode = ComicSpreadMode.Auto

    fun saveSpreadMode(
        item: ReadableItem,
        spreadMode: ComicSpreadMode,
    ) = Unit

    fun loadPageTurnStyle(
        item: ReadableItem,
    ): ComicPageTurnStyle = ComicPageTurnStyle.Slide

    fun savePageTurnStyle(
        item: ReadableItem,
        pageTurnStyle: ComicPageTurnStyle,
    ) = Unit

    fun loadSplitWidePages(
        item: ReadableItem,
    ): Boolean = false

    fun saveSplitWidePages(
        item: ReadableItem,
        enabled: Boolean,
    ) = Unit

    fun loadResumeAnchorPage(
        item: ReadableItem,
    ): Int? = null

    fun saveResumeAnchorPage(
        item: ReadableItem,
        pageIndex: Int?,
    ) = Unit

    fun loadResumeSegment(
        item: ReadableItem,
    ): ComicPageSegment? = null

    fun saveResumeSegment(
        item: ReadableItem,
        segment: ComicPageSegment?,
    ) = Unit

    fun loadResumeScrollOffsetRatio(
        item: ReadableItem,
    ): Float? = null

    fun saveResumeScrollOffsetRatio(
        item: ReadableItem,
        ratio: Float?,
    ) = Unit
}

interface ComicPageSource {
    fun getPages(item: ReadableItem): List<ComicPageSpec>

    fun observePages(
        item: ReadableItem,
    ): Flow<List<ComicPageSpec>> = flowOf(getPages(item))

    fun pagesUnavailableDiagnostic(
        item: ReadableItem,
    ): ComicPagesUnavailableDiagnostic = ComicPagesUnavailableDiagnostic(
        title = "No comic pages available",
        detail = "itemId=${item.id}, source=${item.sourceType}, format=${item.format}",
    )

    fun observePagesUnavailableDiagnostic(
        item: ReadableItem,
    ): Flow<ComicPagesUnavailableDiagnostic> = flowOf(pagesUnavailableDiagnostic(item))

    fun observePagePreviewVersion(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions,
    ): Flow<Long> = flowOf(0L)

    suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? = null

    suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority = ComicPageLoadPriority.Visible,
    ): ComicPageLoadResult

    suspend fun loadPagePreview(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions = ComicPagePreviewOptions(),
    ): ComicPagePreviewResult = ComicPagePreviewResult.Unavailable

    suspend fun loadPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions = ComicPagePreviewOptions(),
    ): ComicPageMetadata? = null

    suspend fun loadCachedPagePreviewMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
        options: ComicPagePreviewOptions = ComicPagePreviewOptions(),
    ): ComicPageMetadata? = null

    fun prefetchPagePreviews(
        item: ReadableItem,
        pages: List<ComicPageSpec>,
        options: ComicPagePreviewOptions = ComicPagePreviewOptions(),
        priorityPageIndex: Int? = null,
    ) = Unit

    fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) = Unit

    fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
        window: ComicPagePrefetchWindow,
    ) {
        prefetchAround(
            item = item,
            settledPageIndex = settledPageIndex,
            pageCount = pageCount,
        )
    }
}

data class ComicPagesUnavailableDiagnostic(
    val title: String,
    val detail: String,
)

@Stable
class ComicsReaderState internal constructor(
    initialPage: Int,
    pageCount: Int,
    initialReaderMode: ComicReaderMode,
    initialReadingDirection: ComicReadingDirection,
    initialPageFitMode: ComicPageFitMode,
    initialSpreadMode: ComicSpreadMode,
    initialPageTurnStyle: ComicPageTurnStyle = ComicPageTurnStyle.Slide,
    initialSplitWidePages: Boolean,
    initialResumeAnchorPage: Int?,
    initialResumeSegment: ComicPageSegment?,
    initialResumeScrollOffsetRatio: Float?,
) {
    private var pageCount by mutableIntStateOf(pageCount)

    var currentPage by mutableIntStateOf(initialPage)
        private set

    var isCurrentPageZoomed by mutableStateOf(false)
        private set

    var readerMode by mutableStateOf(initialReaderMode)
        private set

    var readingDirection by mutableStateOf(initialReadingDirection)
        private set

    var pageFitMode by mutableStateOf(initialPageFitMode)
        private set

    var spreadMode by mutableStateOf(initialSpreadMode)
        private set

    var pageTurnStyle by mutableStateOf(initialPageTurnStyle)
        private set

    var splitWidePages by mutableStateOf(initialSplitWidePages)
        private set

    var resumeAnchorPage by mutableIntStateOf(initialResumeAnchorPage ?: initialPage)
        private set

    var resumeSegment by mutableStateOf(sanitizeComicResumeSegment(initialResumeSegment))
        private set

    var resumeScrollOffsetRatio by mutableStateOf(
        sanitizeComicResumeScrollOffsetRatio(initialResumeScrollOffsetRatio),
    )
        private set

    private var hasUserNavigated by mutableStateOf(false)

    private var hasExplicitReaderMode by mutableStateOf(false)

    private var hasExplicitReadingDirection by mutableStateOf(false)

    private var hasExplicitPageFitMode by mutableStateOf(false)

    private var hasExplicitSpreadMode by mutableStateOf(false)

    private var hasExplicitPageTurnStyle by mutableStateOf(false)

    private var hasExplicitSplitWidePages by mutableStateOf(false)

    private var hasRestoredResumeAnchor by mutableStateOf(initialResumeAnchorPage != null)

    private var hasRestoredResumeSegment by mutableStateOf(
        sanitizeComicResumeSegment(initialResumeSegment) != null,
    )

    private var hasRestoredResumeScrollOffsetRatio by mutableStateOf(
        sanitizeComicResumeScrollOffsetRatio(initialResumeScrollOffsetRatio) != null,
    )

    val progressLabel: String
        get() = "${currentPage + 1} / $pageCount"

    fun onPageSettled(
        pageIndex: Int,
        resumeAnchorPage: Int = pageIndex,
        segment: ComicPageSegment? = null,
    ) {
        val sanitizedSegment = sanitizeComicResumeSegment(segment)
        if (pageIndex != currentPage) {
            hasUserNavigated = true
        }
        currentPage = pageIndex
        this.resumeAnchorPage = resumeAnchorPage.coerceIn(0, pageCount - 1)
        resumeSegment = sanitizedSegment
        resumeScrollOffsetRatio = null
        hasRestoredResumeAnchor = true
        hasRestoredResumeSegment = sanitizedSegment != null
        hasRestoredResumeScrollOffsetRatio = false
    }

    fun onCurrentPageZoomChanged(isZoomed: Boolean) {
        isCurrentPageZoomed = isZoomed
    }

    fun onReadingDirectionChanged(direction: ComicReadingDirection) {
        hasExplicitReadingDirection = true
        readingDirection = direction
    }

    fun onReaderModeChanged(mode: ComicReaderMode) {
        hasExplicitReaderMode = true
        readerMode = mode
        resumeAnchorPage = currentPage.coerceIn(0, pageCount - 1)
        resumeSegment = null
        resumeScrollOffsetRatio = null
        hasRestoredResumeAnchor = true
        hasRestoredResumeSegment = false
        hasRestoredResumeScrollOffsetRatio = false
    }

    fun onPageFitModeChanged(fitMode: ComicPageFitMode) {
        hasExplicitPageFitMode = true
        pageFitMode = fitMode
    }

    fun onSpreadModeChanged(spreadMode: ComicSpreadMode) {
        hasExplicitSpreadMode = true
        this.spreadMode = spreadMode
    }

    fun onPageTurnStyleChanged(pageTurnStyle: ComicPageTurnStyle) {
        hasExplicitPageTurnStyle = true
        this.pageTurnStyle = pageTurnStyle
    }

    fun onSplitWidePagesChanged(enabled: Boolean) {
        hasExplicitSplitWidePages = true
        splitWidePages = enabled
    }

    fun onContinuousPositionSettled(
        pageIndex: Int,
        offsetRatio: Float?,
    ) {
        val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
        if (safePageIndex != currentPage || resumeScrollOffsetRatio != offsetRatio) {
            hasUserNavigated = true
        }
        currentPage = safePageIndex
        resumeAnchorPage = safePageIndex
        resumeSegment = null
        resumeScrollOffsetRatio = sanitizeComicResumeScrollOffsetRatio(offsetRatio)
        hasRestoredResumeAnchor = true
        hasRestoredResumeSegment = false
        hasRestoredResumeScrollOffsetRatio = resumeScrollOffsetRatio != null
    }

    fun restorePageIfUnchanged(pageIndex: Int): Boolean {
        val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
        if (hasUserNavigated || currentPage == safePageIndex) return false
        currentPage = safePageIndex
        if (!hasRestoredResumeAnchor) {
            resumeAnchorPage = safePageIndex
        }
        return true
    }

    fun restoreReadingDirectionIfUnchanged(direction: ComicReadingDirection): Boolean {
        if (hasExplicitReadingDirection || readingDirection == direction) return false
        readingDirection = direction
        return true
    }

    fun restoreReaderModeIfUnchanged(mode: ComicReaderMode): Boolean {
        if (hasExplicitReaderMode || readerMode == mode) return false
        readerMode = mode
        return true
    }

    fun restorePageFitModeIfUnchanged(fitMode: ComicPageFitMode): Boolean {
        if (hasExplicitPageFitMode || pageFitMode == fitMode) return false
        pageFitMode = fitMode
        return true
    }

    fun restoreSpreadModeIfUnchanged(spreadMode: ComicSpreadMode): Boolean {
        if (hasExplicitSpreadMode || this.spreadMode == spreadMode) return false
        this.spreadMode = spreadMode
        return true
    }

    fun restorePageTurnStyleIfUnchanged(pageTurnStyle: ComicPageTurnStyle): Boolean {
        if (hasExplicitPageTurnStyle || this.pageTurnStyle == pageTurnStyle) return false
        this.pageTurnStyle = pageTurnStyle
        return true
    }

    fun restoreSplitWidePagesIfUnchanged(enabled: Boolean): Boolean {
        if (hasExplicitSplitWidePages || splitWidePages == enabled) return false
        splitWidePages = enabled
        return true
    }

    fun restoreResumeAnchorIfUnchanged(pageIndex: Int?): Boolean {
        val safePageIndex = pageIndex?.coerceIn(0, pageCount - 1) ?: return false
        if (hasUserNavigated || hasRestoredResumeAnchor || resumeAnchorPage == safePageIndex) return false
        resumeAnchorPage = safePageIndex
        hasRestoredResumeAnchor = true
        return true
    }

    fun restoreResumeSegmentIfUnchanged(segment: ComicPageSegment?): Boolean {
        val sanitizedSegment = sanitizeComicResumeSegment(segment)
        if (hasUserNavigated || hasRestoredResumeSegment || resumeSegment == sanitizedSegment) return false
        resumeSegment = sanitizedSegment
        hasRestoredResumeSegment = sanitizedSegment != null
        return true
    }

    fun restoreResumeScrollOffsetRatioIfUnchanged(ratio: Float?): Boolean {
        val sanitizedRatio = sanitizeComicResumeScrollOffsetRatio(ratio)
        if (hasUserNavigated ||
            hasRestoredResumeScrollOffsetRatio ||
            resumeScrollOffsetRatio == sanitizedRatio
        ) {
            return false
        }
        resumeScrollOffsetRatio = sanitizedRatio
        hasRestoredResumeScrollOffsetRatio = sanitizedRatio != null
        return true
    }

    internal fun syncPageCount(pageCount: Int) {
        val safePageCount = pageCount.coerceAtLeast(1)
        if (this.pageCount == safePageCount) return
        this.pageCount = safePageCount
        currentPage = currentPage.coerceIn(0, safePageCount - 1)
        resumeAnchorPage = resumeAnchorPage.coerceIn(0, safePageCount - 1)
    }

    companion object {
        fun saver(pageCount: Int): Saver<ComicsReaderState, *> = listSaver(
            save = {
                listOf(
                    it.currentPage,
                    if (it.isCurrentPageZoomed) 1 else 0,
                    it.readerMode.name,
                    it.readingDirection.name,
                    it.pageFitMode.name,
                    it.spreadMode.name,
                    if (it.splitWidePages) 1 else 0,
                    it.resumeAnchorPage,
                    sanitizeComicResumeSegment(it.resumeSegment)?.name.orEmpty(),
                    sanitizeComicResumeScrollOffsetRatio(it.resumeScrollOffsetRatio),
                    if (it.hasUserNavigated) 1 else 0,
                    if (it.hasExplicitReaderMode) 1 else 0,
                    if (it.hasExplicitReadingDirection) 1 else 0,
                    if (it.hasExplicitPageFitMode) 1 else 0,
                    if (it.hasExplicitSpreadMode) 1 else 0,
                    if (it.hasExplicitSplitWidePages) 1 else 0,
                    if (it.hasRestoredResumeAnchor) 1 else 0,
                    if (it.hasRestoredResumeSegment) 1 else 0,
                    if (it.hasRestoredResumeScrollOffsetRatio) 1 else 0,
                    it.pageTurnStyle.name,
                    if (it.hasExplicitPageTurnStyle) 1 else 0,
                )
            },
            restore = { restored ->
                ComicsReaderState(
                    initialPage = (restored[0] as Int).coerceIn(0, pageCount - 1),
                    pageCount = pageCount,
                    initialReaderMode = (restored[2] as String)
                        .let(ComicReaderMode::valueOf),
                    initialReadingDirection = (restored[3] as String)
                        .let(ComicReadingDirection::valueOf),
                    initialPageFitMode = (restored[4] as String)
                        .let(ComicPageFitMode::valueOf),
                    initialSpreadMode = (restored[5] as String)
                        .let(ComicSpreadMode::valueOf),
                    initialPageTurnStyle = (restored.getOrNull(19) as? String)
                        ?.let(ComicPageTurnStyle::valueOf)
                        ?: ComicPageTurnStyle.Slide,
                    initialSplitWidePages = (restored.getOrNull(6) as? Int) == 1,
                    initialResumeAnchorPage = (restored.getOrNull(7) as? Int),
                    initialResumeSegment = sanitizeComicResumeSegment(
                        (restored.getOrNull(8) as? String)
                            ?.takeIf(String::isNotBlank)
                            ?.let(ComicPageSegment::valueOf),
                    ),
                    initialResumeScrollOffsetRatio = restored.getOrNull(9) as? Float,
                ).also { state ->
                    state.onCurrentPageZoomChanged((restored[1] as Int) == 1)
                    state.hasUserNavigated = (restored.getOrNull(10) as? Int) == 1
                    state.hasExplicitReaderMode = (restored.getOrNull(11) as? Int) == 1
                    state.hasExplicitReadingDirection = (restored.getOrNull(12) as? Int) == 1
                    state.hasExplicitPageFitMode = (restored.getOrNull(13) as? Int) == 1
                    state.hasExplicitSpreadMode = (restored.getOrNull(14) as? Int) == 1
                    state.hasExplicitSplitWidePages = (restored.getOrNull(15) as? Int) == 1
                    state.hasRestoredResumeAnchor = (restored.getOrNull(16) as? Int) == 1
                    state.hasRestoredResumeSegment = (restored.getOrNull(17) as? Int) == 1
                    state.hasRestoredResumeScrollOffsetRatio = (restored.getOrNull(18) as? Int) == 1
                    state.hasExplicitPageTurnStyle = (restored.getOrNull(20) as? Int) == 1
                }
            },
        )
    }
}

internal fun isComicProgressFinished(
    pageIndex: Int,
    pageCount: Int,
    segment: ComicPageSegment?,
    readingDirection: ComicReadingDirection,
): Boolean {
    if (pageCount <= 0) return false
    val lastPageIndex = pageCount - 1
    if (pageIndex < lastPageIndex) return false
    if (pageIndex > lastPageIndex) return true
    return when (segment ?: ComicPageSegment.Full) {
        ComicPageSegment.Full -> true
        ComicPageSegment.LeftHalf -> readingDirection == ComicReadingDirection.RTL
        ComicPageSegment.RightHalf -> readingDirection == ComicReadingDirection.LTR
    }
}

internal fun sanitizeComicResumeSegment(
    segment: ComicPageSegment?,
): ComicPageSegment? = segment.takeIf { it == ComicPageSegment.Full }

internal fun sanitizeComicResumeScrollOffsetRatio(
    ratio: Float?,
): Float? = ratio?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)

enum class ComicPageFitMode(
    val label: String,
) {
    FitPage("Fit screen"),
    FitWidth("Fit width"),
    FitHeight("Fit height"),
}

enum class ComicSpreadMode(
    val label: String,
) {
    Auto("Auto"),
    Single("Single"),
    Double("Spread"),
}
