package com.example.unifiedreader.feature.reader.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderZoomViewportStateTest {
    @Test
    fun `layout resize preserves normalized viewport position`() {
        val state = ReaderZoomViewportState()
        state.updateLayout(squareLayout(viewportEdge = 1000))
        state.zoomTo(
            targetScale = 2f,
            focalPoint = Offset(500f, 500f),
            resetOffsetAtMinimum = false,
        )
        state.onPan(Offset(x = 250f, y = -125f))

        state.updateLayout(squareLayout(viewportEdge = 2000))

        assertOffsetEquals(Offset(x = 500f, y = -250f), state.offset)
        assertOffsetEquals(Offset(x = 0.5f, y = -0.25f), state.normalizedOffset)
    }

    @Test
    fun `restored normalized position resolves against first ready layout`() {
        val state = ReaderZoomViewportState(
            initialScale = 2f,
            initialNormalizedOffset = Offset(x = 0.5f, y = -0.25f),
        )
        state.updateLayout(ReaderZoomLayout.Empty)
        state.updateLayout(squareLayout(viewportEdge = 1000))

        assertOffsetEquals(Offset(x = 250f, y = -125f), state.offset)
    }

    @Test
    fun `pan range is independent from current edge direction`() {
        val state = ReaderZoomViewportState()
        state.updateLayout(squareLayout(viewportEdge = 1000))
        state.zoomTo(
            targetScale = 2f,
            focalPoint = Offset(500f, 500f),
            resetOffsetAtMinimum = false,
        )
        state.onPan(Offset(x = 1000f, y = 0f))

        assertTrue(state.canPan(Offset(x = 40f, y = 0f)))
        assertFalse(state.canConsumePan(Offset(x = 40f, y = 0f)))
        assertTrue(state.canConsumePan(Offset(x = -40f, y = 0f)))
    }

    @Test
    fun `pan reports the exact consumed remainder at an edge`() {
        val state = ReaderZoomViewportState()
        state.updateLayout(squareLayout(viewportEdge = 1000))
        state.zoomTo(
            targetScale = 2f,
            focalPoint = Offset(500f, 500f),
            resetOffsetAtMinimum = false,
        )
        state.onPan(Offset(x = 490f, y = 0f))

        val requested = Offset(x = 40f, y = 0f)
        val consumed = state.onPan(requested)

        assertOffsetEquals(Offset(x = 10f, y = 0f), consumed)
        assertOffsetEquals(Offset(x = 30f, y = 0f), requested - consumed)
    }

    private fun squareLayout(viewportEdge: Int) = ReaderZoomLayout(
        viewportSize = IntSize(viewportEdge, viewportEdge),
        contentSize = Size(viewportEdge.toFloat(), viewportEdge.toFloat()),
    )
}

private fun assertOffsetEquals(expected: Offset, actual: Offset) {
    assertEquals(expected.x, actual.x, 0.001f)
    assertEquals(expected.y, actual.y, 0.001f)
}
