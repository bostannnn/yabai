package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

sealed interface ComicPageLoadResult {
    data class Success(
        val image: ImageBitmap,
    ) : ComicPageLoadResult

    data class Failure(
        val message: String,
    ) : ComicPageLoadResult
}

data class ComicReaderPreferencesSnapshot(
    val readingDirection: ComicReadingDirection,
    val pageFitMode: ComicPageFitMode,
    val spreadMode: ComicSpreadMode,
    val splitWidePages: Boolean,
    val resumeAnchorPage: Int? = null,
    val resumeSegment: ComicPageSegment?,
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
}

interface ComicPageSource {
    fun getPages(item: ReadableItem): List<ComicPageSpec>

    suspend fun loadPageMetadata(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageMetadata? = null

    suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority = ComicPageLoadPriority.Visible,
    ): ComicPageLoadResult

    fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) = Unit
}

@Stable
class ComicsReaderState internal constructor(
    initialPage: Int,
    private val pageCount: Int,
    initialReadingDirection: ComicReadingDirection,
    initialPageFitMode: ComicPageFitMode,
    initialSpreadMode: ComicSpreadMode,
    initialSplitWidePages: Boolean,
    initialResumeAnchorPage: Int?,
    initialResumeSegment: ComicPageSegment?,
) {
    var currentPage by mutableIntStateOf(initialPage)
        private set

    var isCurrentPageZoomed by mutableStateOf(false)
        private set

    var readingDirection by mutableStateOf(initialReadingDirection)
        private set

    var pageFitMode by mutableStateOf(initialPageFitMode)
        private set

    var spreadMode by mutableStateOf(initialSpreadMode)
        private set

    var splitWidePages by mutableStateOf(initialSplitWidePages)
        private set

    var resumeAnchorPage by mutableIntStateOf(initialResumeAnchorPage ?: initialPage)
        private set

    var resumeSegment by mutableStateOf(initialResumeSegment)
        private set

    private var hasUserNavigated by mutableStateOf(false)

    private var hasExplicitReadingDirection by mutableStateOf(false)

    private var hasExplicitPageFitMode by mutableStateOf(false)

    private var hasExplicitSpreadMode by mutableStateOf(false)

    private var hasExplicitSplitWidePages by mutableStateOf(false)

    private var hasRestoredResumeAnchor by mutableStateOf(initialResumeAnchorPage != null)

    private var hasRestoredResumeSegment by mutableStateOf(initialResumeSegment != null)

    val progressLabel: String
        get() = "${currentPage + 1} / $pageCount"

    fun onPageSettled(
        pageIndex: Int,
        resumeAnchorPage: Int = pageIndex,
        segment: ComicPageSegment? = null,
    ) {
        if (pageIndex != currentPage) {
            hasUserNavigated = true
        }
        currentPage = pageIndex
        this.resumeAnchorPage = resumeAnchorPage.coerceIn(0, pageCount - 1)
        resumeSegment = segment
        hasRestoredResumeAnchor = true
        hasRestoredResumeSegment = segment != null
    }

    fun onCurrentPageZoomChanged(isZoomed: Boolean) {
        isCurrentPageZoomed = isZoomed
    }

    fun onReadingDirectionChanged(direction: ComicReadingDirection) {
        hasExplicitReadingDirection = true
        readingDirection = direction
    }

    fun onPageFitModeChanged(fitMode: ComicPageFitMode) {
        hasExplicitPageFitMode = true
        pageFitMode = fitMode
    }

    fun onSpreadModeChanged(spreadMode: ComicSpreadMode) {
        hasExplicitSpreadMode = true
        this.spreadMode = spreadMode
    }

    fun onSplitWidePagesChanged(enabled: Boolean) {
        hasExplicitSplitWidePages = true
        splitWidePages = enabled
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
        if (hasUserNavigated || hasRestoredResumeSegment || resumeSegment == segment) return false
        resumeSegment = segment
        hasRestoredResumeSegment = segment != null
        return true
    }

    companion object {
        fun saver(pageCount: Int): Saver<ComicsReaderState, *> = listSaver(
            save = {
                listOf(
                    it.currentPage,
                    if (it.isCurrentPageZoomed) 1 else 0,
                    it.readingDirection.name,
                    it.pageFitMode.name,
                    it.spreadMode.name,
                    if (it.splitWidePages) 1 else 0,
                    it.resumeAnchorPage,
                    it.resumeSegment?.name.orEmpty(),
                    if (it.hasUserNavigated) 1 else 0,
                    if (it.hasExplicitReadingDirection) 1 else 0,
                    if (it.hasExplicitPageFitMode) 1 else 0,
                    if (it.hasExplicitSpreadMode) 1 else 0,
                    if (it.hasExplicitSplitWidePages) 1 else 0,
                    if (it.hasRestoredResumeAnchor) 1 else 0,
                    if (it.hasRestoredResumeSegment) 1 else 0,
                )
            },
            restore = { restored ->
                ComicsReaderState(
                    initialPage = (restored[0] as Int).coerceIn(0, pageCount - 1),
                    pageCount = pageCount,
                    initialReadingDirection = (restored[2] as String)
                        .let(ComicReadingDirection::valueOf),
                    initialPageFitMode = (restored[3] as String)
                        .let(ComicPageFitMode::valueOf),
                    initialSpreadMode = (restored[4] as String)
                        .let(ComicSpreadMode::valueOf),
                    initialSplitWidePages = (restored.getOrNull(5) as? Int) == 1,
                    initialResumeAnchorPage = (restored.getOrNull(6) as? Int),
                    initialResumeSegment = (restored.getOrNull(7) as? String)
                        ?.takeIf(String::isNotBlank)
                        ?.let(ComicPageSegment::valueOf),
                ).also { state ->
                    state.onCurrentPageZoomChanged((restored[1] as Int) == 1)
                    state.hasUserNavigated = (restored.getOrNull(8) as? Int) == 1
                    state.hasExplicitReadingDirection = (restored.getOrNull(9) as? Int) == 1
                    state.hasExplicitPageFitMode = (restored.getOrNull(10) as? Int) == 1
                    state.hasExplicitSpreadMode = (restored.getOrNull(11) as? Int) == 1
                    state.hasExplicitSplitWidePages = (restored.getOrNull(12) as? Int) == 1
                    state.hasRestoredResumeAnchor = (restored.getOrNull(13) as? Int) == 1
                    state.hasRestoredResumeSegment = (restored.getOrNull(14) as? Int) == 1
                }
            },
        )
    }
}

@Composable
fun rememberComicsReaderState(
    itemId: String,
    pageCount: Int,
    initialPage: Int = 0,
    initialReadingDirection: ComicReadingDirection = ComicReadingDirection.LTR,
    initialPageFitMode: ComicPageFitMode = ComicPageFitMode.FitPage,
    initialSpreadMode: ComicSpreadMode = ComicSpreadMode.Auto,
    initialSplitWidePages: Boolean = false,
    initialResumeAnchorPage: Int? = null,
    initialResumeSegment: ComicPageSegment? = null,
): ComicsReaderState {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safeInitialPage = initialPage.coerceIn(0, safePageCount - 1)
    return rememberSaveable(
        itemId,
        safePageCount,
        saver = ComicsReaderState.saver(pageCount = safePageCount),
    ) {
        ComicsReaderState(
            initialPage = safeInitialPage,
            pageCount = safePageCount,
            initialReadingDirection = initialReadingDirection,
            initialPageFitMode = initialPageFitMode,
            initialSpreadMode = initialSpreadMode,
            initialSplitWidePages = initialSplitWidePages,
            initialResumeAnchorPage = initialResumeAnchorPage,
            initialResumeSegment = initialResumeSegment,
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

enum class ComicPageFitMode(
    val label: String,
) {
    FitPage("Page"),
    FitWidth("Width"),
    FitHeight("Height"),
}

enum class ComicSpreadMode(
    val label: String,
) {
    Auto("Auto"),
    Single("Single"),
    Double("Spread"),
}
