package com.example.unifiedreader.feature.reader.comics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicPageViewportStateTest {
    private val containerSize = IntSize(width = 1000, height = 1000)
    private val squareImageSize = IntSize(width = 1000, height = 1000)

    @Test
    fun `zoom keeps focal content point stationary`() {
        val state = viewportState()
        val focalPoint = Offset(x = 250f, y = 300f)
        val contentPointBefore = state.contentPointAt(focalPoint, containerSize)

        state.onGesture(
            centroid = focalPoint,
            zoomChange = 2f,
            panChange = Offset.Zero,
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        assertOffsetEquals(contentPointBefore, state.contentPointAt(focalPoint, containerSize))
        assertOffsetEquals(Offset(250f, 200f), state.offset)
    }

    @Test
    fun `pan follows screen distance at every zoom`() {
        val state = viewportState()
        state.zoomTo(
            targetScale = 4f,
            focalPoint = containerSize.center,
            resetOffsetAtMinimum = false,
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        val consumed = state.onPan(
            panChange = Offset(x = 120f, y = -85f),
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        assertOffsetEquals(Offset(120f, -85f), consumed)
        assertOffsetEquals(Offset(120f, -85f), state.offset)
    }

    @Test
    fun `pan clamps at bounds and reports directional handoff`() {
        val state = viewportState()
        state.zoomTo(
            targetScale = 2f,
            focalPoint = containerSize.center,
            resetOffsetAtMinimum = false,
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )
        state.onPan(
            panChange = Offset(x = 1000f, y = 0f),
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        assertEquals(500f, state.offset.x, 0.001f)
        assertFalse(
            state.canConsumePan(
                panChange = Offset(x = 40f, y = 0f),
                containerSize = containerSize,
                imageSize = squareImageSize,
                fitMode = ComicPageFitMode.FitPage,
            ),
        )
        assertTrue(
            state.canConsumePan(
                panChange = Offset(x = -40f, y = 0f),
                containerSize = containerSize,
                imageSize = squareImageSize,
                fitMode = ComicPageFitMode.FitPage,
            ),
        )
    }

    @Test
    fun `pan remains consumable when either axis has room`() {
        val state = viewportState()
        state.zoomTo(
            targetScale = 2f,
            focalPoint = containerSize.center,
            resetOffsetAtMinimum = false,
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )
        state.onPan(
            panChange = Offset(x = 1000f, y = 0f),
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        assertTrue(
            state.canConsumePan(
                panChange = Offset(x = 40f, y = 30f),
                containerSize = containerSize,
                imageSize = squareImageSize,
                fitMode = ComicPageFitMode.FitPage,
            ),
        )
    }

    @Test
    fun `composite spread normalizes pages to their rendered height`() {
        val composite = equalHeightCompositeImageSize(
            listOf(
                IntSize(width = 1000, height = 2000),
                IntSize(width = 1000, height = 1000),
            ),
        )

        assertEquals(IntSize(width = 3000, height = 2000), composite)
    }

    @Test
    fun `composite spread uses the same minimum ratio as row weights`() {
        val composite = equalHeightCompositeImageSize(
            imageSizes = listOf(
                IntSize(width = 400, height = 1000),
                IntSize(width = 600, height = 1000),
            ),
            minimumAspectRatio = 0.5f,
        )

        assertEquals(IntSize(width = 1100, height = 1000), composite)
    }

    @Test
    fun `layout resize preserves relative pan position`() {
        val state = viewportState()
        state.zoomTo(
            targetScale = 2f,
            focalPoint = containerSize.center,
            resetOffsetAtMinimum = false,
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )
        state.onPan(
            panChange = Offset(x = 250f, y = -125f),
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        state.reconcileLayout(
            containerSize = IntSize(width = 2000, height = 2000),
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        assertOffsetEquals(Offset(x = 500f, y = -250f), state.offset)
    }

    @Test
    fun `fit width can pan vertically without consuming horizontal paging`() {
        val state = viewportState()
        val portraitImage = IntSize(width = 1000, height = 2000)

        assertTrue(
            state.canConsumePan(
                panChange = Offset(x = 0f, y = -40f),
                containerSize = containerSize,
                imageSize = portraitImage,
                fitMode = ComicPageFitMode.FitWidth,
            ),
        )
        assertFalse(
            state.canConsumePan(
                panChange = Offset(x = -40f, y = 0f),
                containerSize = containerSize,
                imageSize = portraitImage,
                fitMode = ComicPageFitMode.FitWidth,
            ),
        )
    }

    @Test
    fun `reset zoom recenters the viewport`() {
        val state = viewportState()
        state.zoomTo(
            targetScale = 2f,
            focalPoint = Offset(200f, 200f),
            resetOffsetAtMinimum = false,
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        state.zoomTo(
            targetScale = 1f,
            focalPoint = Offset(200f, 200f),
            resetOffsetAtMinimum = true,
            containerSize = containerSize,
            imageSize = squareImageSize,
            fitMode = ComicPageFitMode.FitPage,
        )

        assertEquals(1f, state.scale, 0.001f)
        assertOffsetEquals(Offset.Zero, state.offset)
    }

    private fun viewportState() = ComicPageViewportState(
        initialScale = 1f,
        initialOffsetX = 0f,
        initialOffsetY = 0f,
    )
}

private fun ComicPageViewportState.contentPointAt(
    screenPoint: Offset,
    containerSize: IntSize,
): Offset {
    val center = containerSize.center
    return (screenPoint - center - offset) / scale
}

private val IntSize.center: Offset
    get() = Offset(width / 2f, height / 2f)

private fun assertOffsetEquals(expected: Offset, actual: Offset) {
    assertEquals(expected.x, actual.x, 0.001f)
    assertEquals(expected.y, actual.y, 0.001f)
}
