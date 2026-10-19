package com.example.unifiedreader.feature.reader.epub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.core.graphics.withTranslation
import androidx.core.view.children
import androidx.core.view.isVisible
import com.example.unifiedreader.feature.reader.core.curl.PageCurlDrag
import com.example.unifiedreader.feature.reader.core.curl.PageCurlFrames
import com.example.unifiedreader.feature.reader.core.curl.PageCurlState
import com.example.unifiedreader.feature.reader.core.curl.PageCurlSurface
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTapGrabFraction
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTapTurnDurationMillis
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTapTurner
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTarget
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTurn
import com.example.unifiedreader.feature.reader.core.curl.pageCurlDragGesture
import com.example.unifiedreader.feature.reader.core.curl.pageCurlFrames
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/**
 * What the page curl needs from the Readium navigator: the view to picture, a page move without
 * Readium's own animation, and whether there is a page to move to.
 */
internal class EpubPageCurlHost {
    var pageView: View? = null
    var navigator: EpubNavigatorFragment? = null
    /** Whether Readium shows two columns: a spread is asked for, but narrow screens still get one. */
    var showsTwoColumns: Boolean? = null
        private set
    private var isAtBookStart = false
    private var isAtBookEnd = false
    private val pageChanges = MutableStateFlow(0)

    fun onPageChanged(
        isAtBookStart: Boolean,
        isAtBookEnd: Boolean,
    ) {
        this.isAtBookStart = isAtBookStart
        this.isAtBookEnd = isAtBookEnd
        pageChanges.update { it + 1 }
    }

    suspend fun refreshColumns() {
        val columns = navigator?.evaluateJavascript(EpubColumnCountScript) ?: return
        showsTwoColumns = columns.trim('"', ' ') == "2"
    }

    fun canTurn(target: PageCurlTarget): Boolean {
        if (navigator?.isAdded != true || pageView == null) return false
        return when (target) {
            PageCurlTarget.Next -> !isAtBookEnd
            PageCurlTarget.Previous -> !isAtBookStart
        }
    }

    /**
     * Moves one page toward [target] and waits until Readium reports the new page, so a picture taken
     * next shows it (going back into the previous chapter, Readium lays that chapter out at its end).
     */
    suspend fun moveAndAwait(target: PageCurlTarget): Boolean {
        val navigator = navigator ?: return false
        val before = pageChanges.value
        val moved = when (target) {
            PageCurlTarget.Next -> navigator.goForward(animated = false)
            PageCurlTarget.Previous -> navigator.goBackward(animated = false)
        }
        if (!moved) return false
        withTimeoutOrNull(EpubPageChangeTimeoutMillis) {
            pageChanges.first { it != before }
        }
        // One more frame for the web view to paint the new position.
        withFrameNanos { }
        return true
    }

    fun capture(): ImageBitmap? = pageView?.captureForPageCurl()
}

/** One EPUB page turn: the current page pictured at the start, the turned-to page once Readium shows it. */
internal class EpubPageCurlSession(
    override val turn: PageCurlTurn,
    override val grab: Offset,
    val current: ImageBitmap,
) : PageCurlDrag {
    var frames by mutableStateOf<PageCurlFrames?>(null)
    var moved = false
    var preparation: Job? = null
}

/**
 * Curls EPUB pages over [content], the Readium navigator. A turn pictures the current page, covers
 * the navigator with that picture, moves Readium to the turned-to page underneath and pictures it too;
 * the curl runs between the two pictures. A completed turn uncovers the navigator already on the new
 * page; a cancelled one moves it back first.
 */
@Composable
internal fun EpubPageCurlLayer(
    host: EpubPageCurlHost,
    enabled: Boolean,
    twoPageSpread: Boolean,
    rightToLeft: Boolean,
    backTint: Color,
    tapTurner: PageCurlTapTurner,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember { PageCurlState() }
    var session by remember { mutableStateOf<EpubPageCurlSession?>(null) }
    val latestEnabled by rememberUpdatedState(enabled)
    val latestTwoPageSpread by rememberUpdatedState(twoPageSpread)
    val latestRightToLeft by rememberUpdatedState(rightToLeft)

    fun start(
        target: PageCurlTarget,
        touchY: Float?,
    ): EpubPageCurlSession? {
        if (!latestEnabled || session != null || !host.canTurn(target)) return null
        val view = host.pageView ?: return null
        val turn = resolveEpubPageCurlTurn(
            viewBounds = IntRect(left = 0, top = 0, right = view.width, bottom = view.height),
            twoPageSpread = host.showsTwoColumns ?: latestTwoPageSpread,
            rightToLeft = latestRightToLeft,
            target = target,
        ) ?: return null
        val current = host.capture() ?: return null
        val grab = if (touchY != null) {
            turn.grabPoint(touchY)
        } else {
            turn.sheet.grabPoint(turn.sheet.top + (turn.sheet.bottom - turn.sheet.top) * PageCurlTapGrabFraction)
        }
        val started = EpubPageCurlSession(turn = turn, grab = grab, current = current)
        session = started
        started.preparation = scope.launch {
            started.moved = host.moveAndAwait(target)
            val adjacent = if (started.moved) host.capture() else null
            if (adjacent != null) {
                started.frames = pageCurlFrames(turn = turn, currentPage = current, adjacentPage = adjacent)
            }
        }
        return started
    }

    suspend fun finish(
        turn: EpubPageCurlSession,
        committed: Boolean,
    ) {
        turn.preparation?.join()
        if (turn.moved && !committed) {
            host.moveAndAwait(turn.turn.target.opposite())
        }
    }

    DisposableEffect(tapTurner) {
        tapTurner.turnHandler = handler@{ target ->
            if (session != null) return@handler true
            val started = start(target = target, touchY = null) ?: return@handler false
            try {
                started.preparation?.join()
                if (started.frames != null) {
                    state.snapTo(started.turn.restingEdge(started.grab))
                    state.animateAlong(durationMillis = PageCurlTapTurnDurationMillis) { fraction ->
                        started.turn.automaticTurnEdge(grab = started.grab, fraction = fraction)
                    }
                }
            } finally {
                session = null
                state.reset()
            }
            true
        }
        onDispose {
            tapTurner.turnHandler = null
        }
    }

    Box(
        modifier = modifier.pageCurlDragGesture(
            enabled = enabled,
            rightToLeft = rightToLeft,
            state = state,
            gestureScope = scope,
            isTurnActive = { session != null },
            startTurn = { target, down -> start(target = target, touchY = down.y) },
            onTurnSettled = { drag, committed -> finish(drag as EpubPageCurlSession, committed) },
            onTurnEnded = { session = null },
            yieldToLongPress = true,
        ),
    ) {
        content()
        session?.let { turn ->
            val frames = turn.frames
            if (frames == null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(turn.current)
                }
            } else {
                PageCurlSurface(
                    turn = turn.turn,
                    grab = turn.grab,
                    frames = frames,
                    state = state,
                    backTint = backTint,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The back of a turning page: the page colour, lifted on a black page so the leaf still shows. */
internal fun epubPageCurlBackTint(
    theme: EpubReaderTheme,
): Color = when (theme) {
    EpubReaderTheme.Light -> epubReaderSurfaceBackground(theme)
    EpubReaderTheme.Dark -> Color(0xFF1E1E1E)
}

private fun PageCurlTarget.opposite(): PageCurlTarget = when (this) {
    PageCurlTarget.Next -> PageCurlTarget.Previous
    PageCurlTarget.Previous -> PageCurlTarget.Next
}

/**
 * Pictures this view as it is now. Children are drawn directly: drawn through the parent in software,
 * a child with a layer (the navigator's container has a hardware layer) comes from a software drawing
 * cache that is not redrawn when the page moves. Drawing in software also marks the views clean, so
 * the tree is invalidated afterwards, or the screen would keep the old page once the navigator moves.
 */
private fun View.captureForPageCurl(): ImageBitmap? {
    if (width <= 0 || height <= 0) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (this is ViewGroup) {
        background?.draw(canvas)
        children.filter { child -> child.isVisible }.forEach { child ->
            canvas.withTranslation(
                x = (child.left - scrollX).toFloat(),
                y = (child.top - scrollY).toFloat(),
            ) {
                child.draw(this)
            }
        }
    } else {
        draw(canvas)
    }
    invalidateTree()
    return bitmap.asImageBitmap()
}

private fun View.invalidateTree() {
    invalidate()
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).invalidateTree()
        }
    }
}

// Readium reports a page change within a few frames; past this the picture is taken anyway.
private const val EpubPageChangeTimeoutMillis = 400L

private val EpubColumnCountScript = """
    (function() {
      return getComputedStyle(document.documentElement).columnCount;
    })();
""".trimIndent()
