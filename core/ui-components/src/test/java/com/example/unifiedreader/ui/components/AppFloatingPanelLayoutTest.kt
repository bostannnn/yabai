package com.example.unifiedreader.ui.components

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFloatingPanelLayoutTest {

    @Test
    fun `every placement is a full-width bottom sheet in a compact window`() {
        AppFloatingPanelPlacement.entries.forEach { placement ->
            val layout = resolveAppFloatingPanelLayout(
                placement = placement,
                windowWidth = AppWindowWidth.Compact,
                width = 400.dp,
            )
            assertEquals(placement.name, AppFloatingPanelMode.BottomSheet, layout.mode)
            assertEquals(placement.name, Alignment.BottomCenter, layout.alignment)
            assertNull(placement.name, layout.width)
            assertFalse(placement.name, layout.fillHeight)
        }
    }

    @Test
    fun `medium and expanded windows get edge panels`() {
        listOf(AppWindowWidth.Medium, AppWindowWidth.Expanded).forEach { windowWidth ->
            val layout = resolveAppFloatingPanelLayout(
                placement = AppFloatingPanelPlacement.End,
                windowWidth = windowWidth,
                width = 400.dp,
            )
            assertEquals(windowWidth.name, AppFloatingPanelMode.EdgePanel, layout.mode)
        }
    }

    @Test
    fun `start and end placements are full-height edge panels at the requested width`() {
        val start = resolveAppFloatingPanelLayout(
            placement = AppFloatingPanelPlacement.Start,
            windowWidth = AppWindowWidth.Medium,
            width = 360.dp,
        )
        val end = resolveAppFloatingPanelLayout(
            placement = AppFloatingPanelPlacement.End,
            windowWidth = AppWindowWidth.Medium,
            width = 400.dp,
        )
        assertEquals(AppFloatingPanelMode.EdgePanel, start.mode)
        assertEquals(Alignment.CenterStart, start.alignment)
        assertEquals(360.dp, start.width)
        assertTrue(start.fillHeight)

        assertEquals(AppFloatingPanelMode.EdgePanel, end.mode)
        assertEquals(Alignment.CenterEnd, end.alignment)
        assertEquals(400.dp, end.width)
        assertTrue(end.fillHeight)
    }

    @Test
    fun `top-end placement is a content-sized card under the bar`() {
        val layout = resolveAppFloatingPanelLayout(
            placement = AppFloatingPanelPlacement.TopEnd,
            windowWidth = AppWindowWidth.Medium,
            width = 400.dp,
        )
        assertEquals(AppFloatingPanelMode.AnchoredCard, layout.mode)
        assertEquals(Alignment.TopEnd, layout.alignment)
        assertEquals(400.dp, layout.width)
        assertFalse(layout.fillHeight)
    }

    @Test
    fun `default widths match the side sheet for end placements and the contents panel for start`() {
        assertEquals(360.dp, AppFloatingPanelDefaults.width(AppFloatingPanelPlacement.Start))
        assertEquals(AppSideSheetDefaults.width, AppFloatingPanelDefaults.width(AppFloatingPanelPlacement.End))
        assertEquals(AppSideSheetDefaults.width, AppFloatingPanelDefaults.width(AppFloatingPanelPlacement.TopEnd))
    }
}
