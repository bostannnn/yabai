package com.example.unifiedreader.feature.reader.epub

import androidx.compose.ui.unit.IntRect
import com.example.unifiedreader.feature.reader.core.curl.PageCurlFollow
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTarget
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTurn
import com.example.unifiedreader.feature.reader.core.curl.buildPageCurlTurn

/**
 * The page turn over the reader's [viewBounds]. A single page is the whole view, hinged on its
 * reading-start edge: a forward turn lifts the current page, a backward turn brings the previous page
 * back over it. A two-page spread turns its outgoing half over the middle, and the back of that leaf
 * is the facing page of the spread it turns to.
 */
internal fun resolveEpubPageCurlTurn(
    viewBounds: IntRect,
    twoPageSpread: Boolean,
    rightToLeft: Boolean,
    target: PageCurlTarget,
): PageCurlTurn? {
    if (viewBounds.isEmpty) return null
    if (!twoPageSpread) {
        val follow = when (target) {
            PageCurlTarget.Next -> PageCurlFollow.Edge
            PageCurlTarget.Previous -> PageCurlFollow.Fold
        }
        return buildPageCurlTurn(
            target = target,
            paperBounds = viewBounds,
            bookBounds = viewBounds,
            outward = if (rightToLeft) -1f else 1f,
            follow = follow,
            backIsFacingPage = false,
        )
    }
    val middle = viewBounds.left + viewBounds.width / 2
    // Forward turns lift the page on the reading-end side, backward turns the one on the start side.
    val turnsRightHalf = (target == PageCurlTarget.Next) != rightToLeft
    val paperBounds = if (turnsRightHalf) {
        IntRect(left = middle, top = viewBounds.top, right = viewBounds.right, bottom = viewBounds.bottom)
    } else {
        IntRect(left = viewBounds.left, top = viewBounds.top, right = middle, bottom = viewBounds.bottom)
    }
    return buildPageCurlTurn(
        target = target,
        paperBounds = paperBounds,
        bookBounds = viewBounds,
        outward = if (turnsRightHalf) 1f else -1f,
        follow = PageCurlFollow.Edge,
        backIsFacingPage = true,
    )
}
