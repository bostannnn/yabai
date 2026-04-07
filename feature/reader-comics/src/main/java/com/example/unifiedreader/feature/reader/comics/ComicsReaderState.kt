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
import kotlinx.coroutines.flow.StateFlow

data class ComicPageSpec(
    val id: String,
    val contentDescription: String,
)

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
)

interface ComicReaderProgressStore {
    val isHydratedFlow: StateFlow<Boolean>

    val savedPagesFlow: StateFlow<Map<String, Int>>

    fun loadPage(item: ReadableItem): Int?

    fun savePage(
        item: ReadableItem,
        pageIndex: Int,
        pageCount: Int,
    )
}

interface ComicReaderPreferencesStore {
    val isHydratedFlow: StateFlow<Boolean>

    val savedPreferencesFlow: StateFlow<Map<String, ComicReaderPreferencesSnapshot>>

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
}

interface ComicPageSource {
    fun getPages(item: ReadableItem): List<ComicPageSpec>

    suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
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

    private var hasUserNavigated by mutableStateOf(false)

    private var hasExplicitReadingDirection by mutableStateOf(false)

    private var hasExplicitPageFitMode by mutableStateOf(false)

    private var hasExplicitSpreadMode by mutableStateOf(false)

    val progressLabel: String
        get() = "${currentPage + 1} / $pageCount"

    fun onPageSettled(pageIndex: Int) {
        if (pageIndex != currentPage) {
            hasUserNavigated = true
        }
        currentPage = pageIndex
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

    fun restorePageIfUnchanged(pageIndex: Int): Boolean {
        val safePageIndex = pageIndex.coerceIn(0, pageCount - 1)
        if (hasUserNavigated || currentPage == safePageIndex) return false
        currentPage = safePageIndex
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

    companion object {
        fun saver(pageCount: Int): Saver<ComicsReaderState, *> = listSaver(
            save = {
                listOf(
                    it.currentPage,
                    if (it.isCurrentPageZoomed) 1 else 0,
                    it.readingDirection.name,
                    it.pageFitMode.name,
                    it.spreadMode.name,
                    if (it.hasUserNavigated) 1 else 0,
                    if (it.hasExplicitReadingDirection) 1 else 0,
                    if (it.hasExplicitPageFitMode) 1 else 0,
                    if (it.hasExplicitSpreadMode) 1 else 0,
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
                ).also { state ->
                    state.onCurrentPageZoomChanged((restored[1] as Int) == 1)
                    state.hasUserNavigated = (restored.getOrNull(5) as? Int) == 1
                    state.hasExplicitReadingDirection = (restored.getOrNull(6) as? Int) == 1
                    state.hasExplicitPageFitMode = (restored.getOrNull(7) as? Int) == 1
                    state.hasExplicitSpreadMode = (restored.getOrNull(8) as? Int) == 1
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
        )
    }
}

enum class ComicReadingDirection(
    val label: String,
) {
    LTR("LTR"),
    RTL("RTL"),
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
