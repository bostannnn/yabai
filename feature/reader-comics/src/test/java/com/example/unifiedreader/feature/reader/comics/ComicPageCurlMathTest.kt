package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ComicPageCurlMathTest {
    @Test
    fun `shader surface is gated to api 33 and newer`() {
        assertFalse(isComicPageCurlShaderSupported(sdkInt = 32))
        assertTrue(isComicPageCurlShaderSupported(sdkInt = 33))
    }

    @Test
    fun `curl pager layer keeps slide path on unsupported api or slide style`() {
        assertFalse(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = ComicPageTurnStyle.Slide,
                sdkInt = 33,
            ),
        )
        assertFalse(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = ComicPageTurnStyle.Curl,
                readerMode = ComicReaderMode.VerticalScroll,
                sdkInt = 33,
            ),
        )
        assertFalse(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = ComicPageTurnStyle.Curl,
                sdkInt = 32,
            ),
        )
        assertTrue(
            shouldUseComicPageCurlPagerLayer(
                pageTurnStyle = ComicPageTurnStyle.Curl,
                sdkInt = 33,
            ),
        )
    }

    @Test
    fun `curl availability requires curl style paged mode api 33 unzoomed and composites`() {
        val available = resolveComicPageCurlAvailability(
            pageTurnStyle = ComicPageTurnStyle.Curl,
            readerMode = ComicReaderMode.Paged,
            sdkInt = 33,
            isCurrentPageZoomed = false,
            hasCurrentComposite = true,
            hasAdjacentComposite = true,
            containerSize = IntSize(400, 600),
        )

        assertTrue(available.isAvailable)
        assertNull(available.reason)
    }

    @Test
    fun `curl availability falls back for unsupported api slide style scroll mode or missing composites`() {
        assertEquals(
            ComicPageCurlUnavailableReason.UnsupportedApi,
            resolveComicPageCurlAvailability(
                pageTurnStyle = ComicPageTurnStyle.Curl,
                readerMode = ComicReaderMode.Paged,
                sdkInt = 32,
                isCurrentPageZoomed = false,
                hasCurrentComposite = true,
                hasAdjacentComposite = true,
            ).reason,
        )
        assertEquals(
            ComicPageCurlUnavailableReason.PageTurnStyleNotCurl,
            resolveComicPageCurlAvailability(
                pageTurnStyle = ComicPageTurnStyle.Slide,
                readerMode = ComicReaderMode.Paged,
                sdkInt = 33,
                isCurrentPageZoomed = false,
                hasCurrentComposite = true,
                hasAdjacentComposite = true,
            ).reason,
        )
        assertEquals(
            ComicPageCurlUnavailableReason.ReaderModeNotPaged,
            resolveComicPageCurlAvailability(
                pageTurnStyle = ComicPageTurnStyle.Curl,
                readerMode = ComicReaderMode.VerticalScroll,
                sdkInt = 33,
                isCurrentPageZoomed = false,
                hasCurrentComposite = true,
                hasAdjacentComposite = true,
            ).reason,
        )
        assertEquals(
            ComicPageCurlUnavailableReason.MissingAdjacentComposite,
            resolveComicPageCurlAvailability(
                pageTurnStyle = ComicPageTurnStyle.Curl,
                readerMode = ComicReaderMode.Paged,
                sdkInt = 33,
                isCurrentPageZoomed = false,
                hasCurrentComposite = true,
                hasAdjacentComposite = false,
            ).reason,
        )
    }

    @Test
    fun `curl availability is disabled while current page is zoomed`() {
        val availability = resolveComicPageCurlAvailability(
            pageTurnStyle = ComicPageTurnStyle.Curl,
            readerMode = ComicReaderMode.Paged,
            sdkInt = 33,
            isCurrentPageZoomed = true,
            hasCurrentComposite = true,
            hasAdjacentComposite = true,
        )

        assertFalse(availability.isAvailable)
        assertEquals(ComicPageCurlUnavailableReason.Zoomed, availability.reason)
    }

    @Test
    fun `curl commit threshold commits past threshold and cancels at or below it`() {
        assertFalse(shouldCommitComicPageCurl(progress = 0.25f))
        assertTrue(shouldCommitComicPageCurl(progress = 0.251f))
        assertTrue(shouldCommitComicPageCurl(progress = 0.1f, velocity = 1.6f))
        assertFalse(shouldCommitComicPageCurl(progress = 0.1f, velocity = -1.6f))
        assertFalse(shouldCommitComicPageCurl(progress = 0.4f, velocity = -1.6f))
    }

    @Test
    fun `short drags do not pick a curl target`() {
        assertNull(
            resolveComicPageCurlTarget(
                horizontalDrag = 8f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `target spread selection keeps reading order for ltr and rtl drags`() {
        assertEquals(
            2,
            resolveComicPageCurlTargetSpreadIndex(
                horizontalDrag = -32f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
                currentSpreadIndex = 1,
                spreadCount = 4,
            ),
        )
        assertEquals(
            2,
            resolveComicPageCurlTargetSpreadIndex(
                horizontalDrag = 32f,
                readingDirection = ComicReadingDirection.RTL,
                touchSlop = 8f,
                currentSpreadIndex = 1,
                spreadCount = 4,
            ),
        )
        assertEquals(
            0,
            resolveComicPageCurlTargetSpreadIndex(
                horizontalDrag = 32f,
                readingDirection = ComicReadingDirection.LTR,
                touchSlop = 8f,
                currentSpreadIndex = 1,
                spreadCount = 4,
            ),
        )
        assertNull(
            resolveComicPageCurlTargetSpreadIndex(
                horizontalDrag = -32f,
                readingDirection = ComicReadingDirection.RTL,
                touchSlop = 8f,
                currentSpreadIndex = 0,
                spreadCount = 4,
            ),
        )
    }

    @Test
    fun `edge resting at the grab point leaves the whole page flat`() {
        val sheet = ltrSheet()
        val grab = sheet.grabPoint(300f)

        val axis = calculateComicPageCurlAxis(sheet = sheet, grab = grab, edge = grab)

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
            val axis = calculateComicPageCurlAxis(sheet = sheet, grab = grab, edge = edge)

            val drawn = axis.screenPositionOf(grab)

            assertEquals("x for edge $edge", edge.x, drawn.x, 0.5f)
            assertEquals("y for edge $edge", edge.y, drawn.y, 0.5f)
        }
    }

    @Test
    fun `turned edge folds flat at the spine`() {
        val sheet = ltrSheet()
        val grab = sheet.grabPoint(300f)

        val axis = calculateComicPageCurlAxis(sheet = sheet, grab = grab, edge = sheet.turnedPoint(grab))

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
            val edge = clampComicPageCurlEdge(sheet = sheet, grab = grab, edge = finger)
            val axis = calculateComicPageCurlAxis(sheet = sheet, grab = grab, edge = edge)

            assertTrue(axis.coordinateOf(Offset(sheet.hingeX, sheet.top)) <= 0.5f)
            assertTrue(axis.coordinateOf(Offset(sheet.hingeX, sheet.bottom)) <= 0.5f)
        }
    }

    @Test
    fun `rtl sheet is hinged on the right and curls toward the left`() {
        val sheet = ComicPageCurlSheet(hingeX = 500f, top = 0f, bottom = 600f, width = 400f, outward = -1f)
        val grab = sheet.grabPoint(300f)

        val axis = calculateComicPageCurlAxis(sheet = sheet, grab = grab, edge = Offset(grab.x + 150f, grab.y))

        assertEquals(100f, grab.x, 0.0001f)
        assertEquals(-1f, axis.directionX, 0.0001f)
        assertEquals(900f, sheet.turnedPoint(grab).x, 0.0001f)
    }

    @Test
    fun `roll arc solver inverts the roll projection`() {
        val radius = 40f
        listOf(0f, 1f, 20f, 70f, 125f).forEach { distance ->
            val arc = solveComicPageCurlRollArc(distance = distance, radius = radius)

            assertEquals(distance, arc - radius * sin(arc / radius), 0.01f)
        }
    }
}

private fun ltrSheet() = ComicPageCurlSheet(hingeX = 100f, top = 0f, bottom = 600f, width = 400f, outward = 1f)

private fun ComicPageCurlSheet.corners() = listOf(
    Offset(hingeX, top),
    Offset(hingeX, bottom),
    Offset(hingeX + outward * width, top),
    Offset(hingeX + outward * width, bottom),
)

private fun ComicPageCurlAxis.coordinateOf(point: Offset): Float =
    (point.x - originX) * directionX + (point.y - originY) * directionY

// Where the shader draws paper point [paper] once it has lifted (mirror of the shader's mapping).
private fun ComicPageCurlAxis.screenPositionOf(paper: Offset): Offset {
    val arc = coordinateOf(paper)
    val screenCoordinate = if (arc >= PI.toFloat() * radius) {
        PI.toFloat() * radius - arc
    } else {
        radius * sin(arc / radius)
    }
    val shift = screenCoordinate - arc
    return Offset(paper.x + directionX * shift, paper.y + directionY * shift)
}
