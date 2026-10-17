package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicPageCurlTurnTest {
    @Test
    fun `forward turn edge follows the finger one to one from a centred grab`() {
        val turn = sampleTurn(follow = ComicPageCurlFollow.Edge)
        val grab = turn.grabPoint(300f)
        val down = Offset(480f, 300f)

        val edge = turn.edgeForFinger(grab = grab, down = down, finger = Offset(330f, 280f))

        assertEquals(grab.x - 150f, edge.x, 0.01f)
        assertEquals(grab.y - 20f, edge.y, 0.01f)
    }

    @Test
    fun `low grabs lead with the bottom corner`() {
        val turn = sampleTurn(follow = ComicPageCurlFollow.Edge)
        val grab = turn.grabPoint(560f)

        val edge = turn.edgeForFinger(grab = grab, down = grab, finger = Offset(grab.x - 200f, grab.y))

        assertTrue("edge should lift above the finger, was ${edge.y}", edge.y < grab.y)
    }

    @Test
    fun `backward single page turn starts turned and brings the fold under the finger`() {
        val turn = sampleTurn(follow = ComicPageCurlFollow.Fold)
        val grab = turn.grabPoint(300f)
        val down = Offset(110f, 300f)

        val atDown = turn.edgeForFinger(grab = grab, down = down, finger = down)
        val dragged = turn.edgeForFinger(grab = grab, down = down, finger = Offset(260f, 300f))

        assertEquals(turn.sheet.turnedPoint(grab).x, atDown.x, 0.01f)
        val foldX = (dragged.x + grab.x) / 2f
        assertTrue("fold should sit just ahead of the finger, was $foldX", foldX in 250f..290f)
    }

    @Test
    fun `commit progress and velocity point toward the target page`() {
        val forward = sampleTurn(follow = ComicPageCurlFollow.Edge)
        val backward = sampleTurn(follow = ComicPageCurlFollow.Fold)
        val grab = forward.grabPoint(300f)

        assertEquals(0f, forward.commitProgress(grab, forward.restingEdge(grab)), 0.0001f)
        assertEquals(1f, forward.commitProgress(grab, forward.committedEdge(grab)), 0.0001f)
        assertEquals(0f, backward.commitProgress(grab, backward.restingEdge(grab)), 0.0001f)
        assertEquals(1f, backward.commitProgress(grab, backward.committedEdge(grab)), 0.0001f)
        // LTR: forward turns flick left, backward turns flick right.
        assertTrue(forward.commitVelocity(horizontalVelocity = -800f) > 0f)
        assertTrue(backward.commitVelocity(horizontalVelocity = 800f) > 0f)
    }

    @Test
    fun `automatic turn runs from rest to the target page`() {
        listOf(ComicPageCurlFollow.Edge, ComicPageCurlFollow.Fold).forEach { follow ->
            val turn = sampleTurn(follow = follow)
            val grab = turn.grabPoint(500f)

            val start = turn.automaticTurnEdge(grab = grab, fraction = 0f)
            val end = turn.automaticTurnEdge(grab = grab, fraction = 1f)

            assertEquals(turn.restingEdge(grab).x, start.x, 0.01f)
            assertEquals(turn.committedEdge(grab).x, end.x, 0.01f)
            assertEquals(grab.y, end.y, 0.01f)
        }
    }

    @Test
    fun `settling takes longer the further the page has to travel`() {
        val turn = sampleTurn(follow = ComicPageCurlFollow.Edge)
        val grab = turn.grabPoint(300f)

        val short = turn.settleDurationMillis(from = Offset(grab.x - 40f, grab.y), to = grab)
        val long = turn.settleDurationMillis(from = grab, to = turn.committedEdge(grab))

        assertTrue(short < long)
    }
}

private fun sampleTurn(
    follow: ComicPageCurlFollow,
): ComicPageCurlTurn {
    val paper = ComicRasterRect(left = 100, top = 0, width = 400, height = 600)
    return ComicPageCurlTurn(
        target = if (follow == ComicPageCurlFollow.Edge) ComicPageCurlTarget.Next else ComicPageCurlTarget.Previous,
        sheet = ComicPageCurlSheet(hingeX = 100f, top = 0f, bottom = 600f, width = 400f, outward = 1f),
        paperBounds = paper,
        bookBounds = paper,
        follow = follow,
        backIsFacingPage = false,
    )
}
