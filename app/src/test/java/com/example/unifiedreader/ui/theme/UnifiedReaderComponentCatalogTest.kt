package com.example.unifiedreader.ui.theme

import com.example.unifiedreader.feature.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedReaderComponentCatalogTest {
    @Test
    fun requestedThemesUseDistinctComponentLanguages() {
        val blueprint = ThemeMode.Blueprint.toComponentTokens()
        val vhs = ThemeMode.VhsNight.toComponentTokens()
        val dossier = ThemeMode.DetectiveFile.toComponentTokens()

        assertEquals(UnifiedReaderFrameTreatment.Blueprint, blueprint.sheet.frameTreatment)
        assertEquals(UnifiedReaderFrameTreatment.Vhs, vhs.sheet.frameTreatment)
        assertEquals(UnifiedReaderFrameTreatment.Dossier, dossier.sheet.frameTreatment)
        assertNotEquals(blueprint.control.selectionTreatment, vhs.control.selectionTreatment)
        assertNotEquals(vhs.control.selectionTreatment, dossier.control.selectionTreatment)
    }

    @Test
    fun restrainedThemesKeepDefaultComponentRecipes() {
        val graphite = ThemeMode.Graphite.toComponentTokens()

        assertEquals(UnifiedReaderFrameTreatment.None, graphite.sheet.frameTreatment)
        assertEquals(0f, graphite.appBackground.patternIntensity)
        assertTrue(graphite.rail.containerAlpha > 0f)
    }

    @Test
    fun compactControlsDoNotInheritPanelFrames() {
        val expressiveThemes = listOf(
            ThemeMode.Terminal95,
            ThemeMode.CrimsonCourt,
            ThemeMode.PanelPop,
            ThemeMode.Blueprint,
            ThemeMode.VhsNight,
            ThemeMode.DetectiveFile,
        )

        expressiveThemes.forEach { themeMode ->
            val control = themeMode.toComponentTokens().control

            assertEquals(themeMode.name, UnifiedReaderFrameTreatment.None, control.frameTreatment)
            assertEquals(themeMode.name, 0f, control.borderAlpha)
            assertEquals(themeMode.name, 0f, control.frameAlpha)
        }
    }

    @Test
    fun structuralChromeDoesNotDrawEnclosingFrames() {
        val expressiveThemes = listOf(
            ThemeMode.Terminal95,
            ThemeMode.CrimsonCourt,
            ThemeMode.PanelPop,
            ThemeMode.Blueprint,
            ThemeMode.VhsNight,
            ThemeMode.DetectiveFile,
        )

        expressiveThemes.forEach { themeMode ->
            val chrome = themeMode.toComponentTokens().chrome

            assertEquals(themeMode.name, UnifiedReaderFrameTreatment.None, chrome.frameTreatment)
            assertEquals(themeMode.name, 0f, chrome.borderAlpha)
            assertEquals(themeMode.name, 0f, chrome.frameAlpha)
        }
    }

    @Test
    fun compactMetadataAndRailsUseFrameOnlyAccents() {
        val expressiveThemes = listOf(
            ThemeMode.Terminal95,
            ThemeMode.CrimsonCourt,
            ThemeMode.PanelPop,
            ThemeMode.Blueprint,
            ThemeMode.VhsNight,
            ThemeMode.DetectiveFile,
        )

        expressiveThemes.forEach { themeMode ->
            val components = themeMode.toComponentTokens()

            assertEquals(themeMode.name, 0f, components.metadata.borderAlpha)
            assertTrue(themeMode.name, components.metadata.frameAlpha > 0f)
            assertEquals(themeMode.name, 0f, components.rail.borderAlpha)
            assertTrue(themeMode.name, components.rail.frameAlpha > 0f)
        }
    }
}
