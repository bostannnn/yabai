package com.example.unifiedreader.feature.reader.epub

import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation

internal fun epubNavigatorContainerLayoutParams(
    verticalInsetPx: Int,
): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT,
    Gravity.CENTER,
).apply {
    topMargin = verticalInsetPx
    bottomMargin = verticalInsetPx
}

internal fun FrameLayout.updateEpubNavigatorContainer(
    verticalInsetPx: Int,
    backgroundColor: Int,
) {
    val child = getChildAt(0) ?: return
    child.setBackgroundColor(backgroundColor)
    val current = child.layoutParams as? FrameLayout.LayoutParams
    val updated = epubNavigatorContainerLayoutParams(verticalInsetPx)
    if (
        current == null ||
        current.topMargin != updated.topMargin ||
        current.bottomMargin != updated.bottomMargin
    ) {
        child.layoutParams = updated
    }
}

internal fun FrameLayout.updateEpubGutterTapListener(
    verticalInsetPx: Int,
    onSurfaceTap: () -> Unit,
) {
    var isTrackingGutterTap = false
    setOnTouchListener { view, event ->
        if (verticalInsetPx <= 0) return@setOnTouchListener false

        fun isCenterGutterTap(): Boolean {
            val isVerticalGutterTap = event.y < verticalInsetPx ||
                event.y > view.height - verticalInsetPx
            if (!isVerticalGutterTap) return false

            val sideZoneWidth = view.width * 0.25f
            return event.x > sideZoneWidth &&
                event.x < view.width - sideZoneWidth
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTrackingGutterTap = isCenterGutterTap()
                isTrackingGutterTap
            }

            MotionEvent.ACTION_UP -> {
                if (!isTrackingGutterTap) return@setOnTouchListener false
                isTrackingGutterTap = false
                onSurfaceTap()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                isTrackingGutterTap = false
                false
            }

            else -> isTrackingGutterTap
        }
    }
}

internal fun epubReaderSurfaceBackground(
    theme: EpubReaderTheme,
): Color = when (theme) {
    EpubReaderTheme.Light -> Color(0xFFFCFCFA)
    EpubReaderTheme.Dark -> Color(0xFF000000)
}

internal suspend fun applyEpubViewportPadding(
    navigator: EpubNavigatorFragment,
    publication: Publication,
    paddingPx: Int,
) {
    if (publication.metadata.presentation.layout == EpubLayout.FIXED) return
    navigator.evaluateJavascript(buildEpubViewportPaddingScript(paddingPx))
}

internal fun buildEpubViewportPaddingScript(
    paddingPx: Int,
): String {
    val clampedPaddingPx = paddingPx.coerceAtLeast(0)
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
          return padding;
        })();
    """.trimIndent()
}

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
