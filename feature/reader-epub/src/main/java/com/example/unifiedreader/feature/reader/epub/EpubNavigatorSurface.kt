package com.example.unifiedreader.feature.reader.epub

import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.example.unifiedreader.feature.reader.core.ReaderTapSide
import com.example.unifiedreader.feature.reader.core.ReaderTapZoneMaxWidth
import com.example.unifiedreader.feature.reader.core.readerTapSide
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import java.util.Locale

/** The navigator inside the page's strips: [verticalInsetPx] above and below the text, [sideInsetPx] beside a spread. */
internal fun epubNavigatorContainerLayoutParams(
    verticalInsetPx: Int,
    sideInsetPx: Int,
): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT,
    Gravity.CENTER,
).apply {
    topMargin = verticalInsetPx
    bottomMargin = verticalInsetPx
    leftMargin = sideInsetPx
    rightMargin = sideInsetPx
}

internal fun FrameLayout.updateEpubNavigatorContainer(
    verticalInsetPx: Int,
    sideInsetPx: Int,
    backgroundColor: Int,
) {
    val child = getChildAt(0) ?: return
    child.setBackgroundColor(backgroundColor)
    val current = child.layoutParams as? FrameLayout.LayoutParams
    val updated = epubNavigatorContainerLayoutParams(verticalInsetPx, sideInsetPx)
    if (current?.margins() != updated.margins()) {
        child.layoutParams = updated
    }
}

private fun FrameLayout.LayoutParams.margins(): List<Int> = listOf(leftMargin, topMargin, rightMargin, bottomMargin)

/**
 * Taps on the strips around the navigator, which Readium never sees, act like taps on the page: the
 * same side zones across the whole width ([readerTapSide]) turn it, the middle opens the menu.
 */
internal fun FrameLayout.updateEpubStripTapListener(
    onTap: (ReaderTapSide) -> Unit,
) {
    var isTrackingStripTap = false
    setOnTouchListener { view, event ->
        fun isStripTap(): Boolean {
            val navigator = (view as? FrameLayout)?.getChildAt(0) ?: return false
            return event.x < navigator.left || event.x > navigator.right ||
                event.y < navigator.top || event.y > navigator.bottom
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTrackingStripTap = isStripTap()
                isTrackingStripTap
            }

            MotionEvent.ACTION_UP -> {
                if (!isTrackingStripTap) return@setOnTouchListener false
                isTrackingStripTap = false
                val maxZoneWidth = ReaderTapZoneMaxWidth.value * view.resources.displayMetrics.density
                onTap(readerTapSide(event.x, view.width.toFloat(), maxZoneWidth))
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                isTrackingStripTap = false
                false
            }

            else -> isTrackingStripTap
        }
    }
}

/** Calls [onTouch] on every touch down here, before the content sees it, without consuming it. */
internal fun Modifier.onEveryTouchDown(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch()
    }
}

/**
 * Pads the book's body above and below inside the web view, and keeps Readium's line-length cap in step
 * with the text size: the reader sizes text with the web view's text zoom, which Readium's 40 rem cap
 * doesn't follow, so bigger text made shorter lines between the same wide margins. [textScale] is the
 * Text size setting (1.0 when the book keeps its own styling).
 */
internal suspend fun applyEpubViewportPadding(
    navigator: EpubNavigatorFragment,
    publication: Publication,
    paddingPx: Int,
    textScale: Double,
) {
    if (publication.metadata.presentation.layout == EpubLayout.FIXED) return
    navigator.evaluateJavascript(buildEpubViewportPaddingScript(paddingPx, textScale))
}

internal fun buildEpubViewportPaddingScript(
    paddingPx: Int,
    textScale: Double = 1.0,
): String {
    val clampedPaddingPx = paddingPx.coerceAtLeast(0)
    // Readium sets the cap on the root, and on the body when scrolled; an inline !important wins over both.
    // Smaller text keeps today's cap, so it still fits more on the page.
    val lineLength = String.format(Locale.ROOT, "%.2frem", EpubLineLengthRem * textScale.coerceAtLeast(1.0))
    return """
        (function() {
          var padding = '${clampedPaddingPx}px';
          var root = document.documentElement;
          var body = document.body;
          if (!root || !body) return 'missing-root';
          root.style.boxSizing = 'border-box';
          body.style.boxSizing = 'border-box';
          body.style.paddingTop = padding;
          body.style.paddingBottom = padding;
          root.style.setProperty('--RS__maxLineLength', '$lineLength', 'important');
          body.style.setProperty('--RS__maxLineLength', '$lineLength', 'important');
          return padding;
        })();
    """.trimIndent()
}

// Readium CSS's line-length cap at 100% text: about 70 characters of book type.
private const val EpubLineLengthRem = 40.0

/**
 * Where the navigator starts. Changing the layout mode rebuilds it, and the new one must open where the
 * old one stopped, not where the book was opened; its first location event is saved as progress.
 */
internal class EpubNavigatorResumePoint {
    private var removedNavigatorLocator: Locator? = null

    fun startLocator(initialLocator: Locator?): Locator? = removedNavigatorLocator ?: initialLocator

    fun onNavigatorRemoved(locator: Locator) {
        removedNavigatorLocator = locator
    }
}

/**
 * Whether the reader sees the end of the book: the last page of the last reading-order resource.
 * Readium reports a page by where it starts, so the book's last position, or a progression near 1,
 * begins before the end (half-way through a two-position book). A paginated layout knows its page
 * ([pageIndex] of [totalPages], a spread counting as one); scroll mode asks the web view whether it is
 * scrolled to the bottom ([scrolledToResourceEnd]).
 */
internal fun isEpubBookEnd(
    isLastResource: Boolean,
    isScrollMode: Boolean,
    pageIndex: Int,
    totalPages: Int?,
    scrolledToResourceEnd: Boolean?,
): Boolean = when {
    !isLastResource -> false
    isScrollMode -> scrolledToResourceEnd == true
    else -> totalPages != null && totalPages > 0 && pageIndex >= totalPages - 1
}

internal val EpubScrolledToResourceEndScript = """
    (function() {
      var element = document.scrollingElement || document.documentElement;
      return window.scrollY + window.innerHeight >= element.scrollHeight - 2;
    })();
""".trimIndent()
