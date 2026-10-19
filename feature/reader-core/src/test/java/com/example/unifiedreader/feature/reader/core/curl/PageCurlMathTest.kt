package com.example.unifiedreader.feature.reader.core.curl

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlMathTest {
    @Test
    fun `shader surface is gated to api 33 and newer`() {
        assertFalse(isPageCurlShaderSupported(sdkInt = 32))
        assertTrue(isPageCurlShaderSupported(sdkInt = 33))
    }

    @Test
    fun `curl commit threshold commits past threshold and cancels at or below it`() {
        assertFalse(shouldCommitPageCurl(progress = 0.25f))
        assertTrue(shouldCommitPageCurl(progress = 0.251f))
        assertTrue(shouldCommitPageCurl(progress = 0.1f, velocity = 1.6f))
        assertFalse(shouldCommitPageCurl(progress = 0.1f, velocity = -1.6f))
        assertFalse(shouldCommitPageCurl(progress = 0.4f, velocity = -1.6f))
    }

    @Test
    fun `drags past the slop pick the target in reading order`() {
        assertEquals(PageCurlTarget.Next, resolvePageCurlTarget(horizontalDrag = -9f, rightToLeft = false, touchSlop = 8f))
        assertEquals(PageCurlTarget.Previous, resolvePageCurlTarget(horizontalDrag = 9f, rightToLeft = false, touchSlop = 8f))
        assertEquals(PageCurlTarget.Next, resolvePageCurlTarget(horizontalDrag = 9f, rightToLeft = true, touchSlop = 8f))
    }

    @Test
    fun `short drags do not pick a curl target`() {
        assertNull(
            resolvePageCurlTarget(
                horizontalDrag = 8f,
                rightToLeft = false,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `edge resting at the grab point leaves the whole page flat`() {
        val sheet = ltrSheet()
        val grab = sheet.grabPoint(300f)

        val axis = calculatePageCurlAxis(sheet = sheet, grab = grab, edge = grab)

        sheet.corners().forEach { corner -> assertTrue(axis.coordinateOf(corner) <= 0f) }
    }

    @Test
    fun `grabbed paper point is drawn under the edge on and off the roll`() {
        val sheet = ltrSheet()
        val grab = sheet.grabPoint(300f)
        // Off the roll (flap lying flat on top), tilted, and on the roll (short lift).
        listOf(
            Offset(grab.x - 240f, grab.y),
            Offset(grab.x - 310f, grab.y - 70f),
            Offset(grab.x - 12f, grab.y),
        ).forEach { edge ->
            val axis = calculatePageCurlAxis(sheet = sheet, grab = grab, edge = edge)

            val drawn = axis.screenPositionOf(grab)

            assertEquals("x for edge $edge", edge.x, drawn.x, 0.5f)
            assertEquals("y for edge $edge", edge.y, drawn.y, 0.5f)
        }
    }

    @Test
    fun `turned edge folds flat at the spine`() {
        val sheet = ltrSheet()
        val grab = sheet.grabPoint(300f)

        val axis = calculatePageCurlAxis(sheet = sheet, grab = grab, edge = sheet.turnedPoint(grab))

        assertEquals(0f, axis.radius, 0.001f)
        assertEquals(1f, axis.directionX, 0.0001f)
        assertEquals(0f, axis.coordinateOf(Offset(sheet.hingeX, sheet.top)), 0.01f)
        assertEquals(0f, axis.coordinateOf(Offset(sheet.hingeX, sheet.bottom)), 0.01f)
    }

    @Test
    fun `clamped edge never lifts the spine`() {
        val sheet = ltrSheet()
        val grab = sheet.grabPoint(520f)
        // Dragged far past the spine and diagonally away from the grabbed corner.
        listOf(
            Offset(-900f, 520f),
            Offset(-300f, -400f),
            Offset(50f, 1500f),
        ).forEach { finger ->
            val edge = clampPageCurlEdge(sheet = sheet, grab = grab, edge = finger)
            val axis = calculatePageCurlAxis(sheet = sheet, grab = grab, edge = edge)

            assertTrue(axis.coordinateOf(Offset(sheet.hingeX, sheet.top)) <= 0.5f)
            assertTrue(axis.coordinateOf(Offset(sheet.hingeX, sheet.bottom)) <= 0.5f)
        }
    }

    @Test
    fun `rtl sheet is hinged on the right and curls toward the left`() {
        val sheet = PageCurlSheet(hingeX = 500f, top = 0f, bottom = 600f, width = 400f, outward = -1f)
        val grab = sheet.grabPoint(300f)

        val axis = calculatePageCurlAxis(sheet = sheet, grab = grab, edge = Offset(grab.x + 150f, grab.y))

        assertEquals(100f, grab.x, 0.0001f)
        assertEquals(-1f, axis.directionX, 0.0001f)
        assertEquals(900f, sheet.turnedPoint(grab).x, 0.0001f)
    }

    @Test
    fun `roll arc solver inverts the roll projection`() {
        val radius = 40f
        listOf(0f, 1f, 20f, 70f, 125f).forEach { distance ->
            val arc = solvePageCurlRollArc(distance = distance, radius = radius)

            assertEquals(distance, arc - radius * sin(arc / radius), 0.01f)
        }
    }
}

private fun ltrSheet() = PageCurlSheet(hingeX = 100f, top = 0f, bottom = 600f, width = 400f, outward = 1f)

private fun PageCurlSheet.corners() = listOf(
    Offset(hingeX, top),
    Offset(hingeX, bottom),
    Offset(hingeX + outward * width, top),
    Offset(hingeX + outward * width, bottom),
)

private fun PageCurlAxis.coordinateOf(point: Offset): Float =
    (point.x - originX) * directionX + (point.y - originY) * directionY

// Where the shader draws paper point [paper] once it has lifted (mirror of the shader's mapping).
private fun PageCurlAxis.screenPositionOf(paper: Offset): Offset {
    val arc = coordinateOf(paper)
    val screenCoordinate = if (arc >= PI.toFloat() * radius) {
        PI.toFloat() * radius - arc
    } else {
        radius * sin(arc / radius)
    }
    val shift = screenCoordinate - arc
    return Offset(paper.x + directionX * shift, paper.y + directionY * shift)
}
