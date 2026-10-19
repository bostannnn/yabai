package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.example.unifiedreader.feature.reader.core.curl.PageCurlDrag
import com.example.unifiedreader.feature.reader.core.curl.PageCurlFrames
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTurn
import com.example.unifiedreader.feature.reader.core.curl.pageCurlFrames

/** A page turn in progress: the turn geometry, where the leaf was grabbed, and the composites it shows. */
internal data class ComicPageCurlInteraction(
    override val turn: PageCurlTurn,
    val targetSpreadIndex: Int,
    override val grab: Offset,
    val frames: PageCurlFrames,
) : PageCurlDrag

internal fun buildComicPageCurlInteraction(
    turn: PageCurlTurn,
    targetSpreadIndex: Int,
    grab: Offset,
    currentPage: ImageBitmap,
    adjacentPage: ImageBitmap,
): ComicPageCurlInteraction = ComicPageCurlInteraction(
    turn = turn,
    targetSpreadIndex = targetSpreadIndex,
    grab = grab,
    frames = pageCurlFrames(turn = turn, currentPage = currentPage, adjacentPage = adjacentPage),
)
