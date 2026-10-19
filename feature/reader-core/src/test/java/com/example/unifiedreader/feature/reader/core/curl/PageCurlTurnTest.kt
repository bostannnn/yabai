package com.example.unifiedreader.feature.reader.core.curl

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlTurnTest {
    @Test
    fun `forward turn starts at rest and catches the grabbed edge up with the finger`() {
        val turn = sampleTurn(follow = PageCurlFollow.Edge)
        val grab = turn.grabPoint(300f)
        val start = Offset(400f, 300f)

        val atStart = turn.edgeForFinger(grab = grab, start = start, finger = start)
        val halfway = turn.edgeForFinger(grab = grab, start = start, finger = Offset(350f, 300f))
        val caughtUp = turn.edgeForFinger(grab = grab, start = start, finger = Offset(250f, 280f))

        assertEquals(grab.x, atStart.x, 0.01f)
        assertEquals(grab.y, atStart.y, 0.01f)
        // Halfway through the catch-up a quarter of the 100 px lead is left: the edge moved 75 px
        // while the finger moved 50.
        assertEquals(375f, halfway.x, 0.01f)
        assertEquals(250f, caughtUp.x, 0.01f)
        assertEquals(280f, caughtUp.y, 0.01f)
    }

    @Test
    fun `off centre grabs pick the page up nearer the corner so the corner leads`() {
        val turn = sampleTurn(follow = PageCurlFollow.Edge)
        val grab = turn.grabPoint(450f)
        val start = Offset(480f, 450f)

        val edge = turn.edgeForFinger(grab = grab, start = start, finger = Offset(200f, 450f))

        assertTrue("grab should sit below the touch, was ${grab.y}", grab.y > 450f)
        assertEquals(300f, turn.grabPoint(300f).y, 0.01f)
        assertEquals(200f, edge.x, 0.01f)
        assertEquals(450f, edge.y, 0.01f)
    }

    @Test
    fun `backward single page turn starts turned and brings the roll's leading edge under the finger`() {
        val turn = sampleTurn(follow = PageCurlFollow.Fold)
        val grab = turn.grabPoint(300f)
        val start = Offset(110f, 300f)

        val atStart = turn.edgeForFinger(grab = grab, start = start, finger = start)
        val dragged = turn.edgeForFinger(grab = grab, start = start, finger = Offset(260f, 300f))
        val axis = calculatePageCurlAxis(sheet = turn.sheet, grab = grab, edge = dragged)

        assertEquals(turn.sheet.turnedPoint(grab).x, atStart.x, 0.01f)
        assertEquals(260f, axis.originX + axis.directionX * axis.radius, 0.5f)
    }

    @Test
    fun `backward turn dragged past the page lays the previous page flat instead of overshooting`() {
        val turn = sampleTurn(follow = PageCurlFollow.Fold)
        val grab = turn.grabPoint(300f)

        val edge = turn.edgeForFinger(grab = grab, start = Offset(300f, 300f), finger = Offset(700f, 300f))

        assertEquals(grab.x, edge.x, 0.01f)
        assertEquals(grab.y, edge.y, 0.01f)
        assertEquals(1f, turn.commitProgress(grab = grab, edge = edge), 0.0001f)
    }

    @Test
    fun `commit progress and velocity point toward the target page`() {
        val forward = sampleTurn(follow = PageCurlFollow.Edge)
        val backward = sampleTurn(follow = PageCurlFollow.Fold)
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
        listOf(PageCurlFollow.Edge, PageCurlFollow.Fold).forEach { follow ->
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
        val turn = sampleTurn(follow = PageCurlFollow.Edge)
        val grab = turn.grabPoint(300f)

        val short = turn.settleDurationMillis(from = Offset(grab.x - 40f, grab.y), to = grab)
        val long = turn.settleDurationMillis(from = grab, to = turn.committedEdge(grab))

        assertTrue(short < long)
    }
}

private fun sampleTurn(
    follow: PageCurlFollow,
): PageCurlTurn {
    val paper = IntRect(left = 100, top = 0, right = 500, bottom = 600)
    return PageCurlTurn(
        target = if (follow == PageCurlFollow.Edge) PageCurlTarget.Next else PageCurlTarget.Previous,
        sheet = PageCurlSheet(hingeX = 100f, top = 0f, bottom = 600f, width = 400f, outward = 1f),
        paperBounds = paper,
        bookBounds = paper,
        follow = follow,
        backIsFacingPage = false,
    )
}
