package com.example.unifiedreader.ui.theme

import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.settings.ThemeMode

private val Terminal95ComponentTokens = expressiveComponentTokens(
    surfaceTreatment = UnifiedReaderSurfaceTreatment.TerminalGrid,
    borderTreatment = UnifiedReaderBorderTreatment.Single,
    frameTreatment = UnifiedReaderFrameTreatment.Terminal,
    selectionTreatment = UnifiedReaderSelectionTreatment.Inverse,
)

private val CrimsonCourtComponentTokens = expressiveComponentTokens(
    surfaceTreatment = UnifiedReaderSurfaceTreatment.GothicVignette,
    borderTreatment = UnifiedReaderBorderTreatment.Double,
    frameTreatment = UnifiedReaderFrameTreatment.Ornate,
    selectionTreatment = UnifiedReaderSelectionTreatment.Inverse,
)

private val PanelPopComponentTokens = expressiveComponentTokens(
    surfaceTreatment = UnifiedReaderSurfaceTreatment.ComicHalftone,
    borderTreatment = UnifiedReaderBorderTreatment.Ink,
    frameTreatment = UnifiedReaderFrameTreatment.Comic,
    selectionTreatment = UnifiedReaderSelectionTreatment.Inverse,
    borderWidth = 2.dp,
)

internal fun ThemeMode.toComponentTokens(): UnifiedReaderComponentTokens = when (this) {
    ThemeMode.Terminal95 -> Terminal95ComponentTokens
    ThemeMode.CrimsonCourt -> CrimsonCourtComponentTokens
    ThemeMode.PanelPop -> PanelPopComponentTokens
    ThemeMode.Blueprint -> BlueprintComponentTokens
    ThemeMode.VhsNight -> VhsNightComponentTokens
    ThemeMode.DetectiveFile -> DetectiveFileComponentTokens
    else -> UnifiedReaderComponentTokens()
}

private fun expressiveComponentTokens(
    surfaceTreatment: UnifiedReaderSurfaceTreatment,
    borderTreatment: UnifiedReaderBorderTreatment,
    frameTreatment: UnifiedReaderFrameTreatment,
    selectionTreatment: UnifiedReaderSelectionTreatment,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
): UnifiedReaderComponentTokens = UnifiedReaderComponentTokens(
    appBackground = UnifiedReaderComponentRecipe(
        surfaceTreatment = surfaceTreatment,
        patternIntensity = 0.20f,
    ),
    chrome = UnifiedReaderComponentRecipe(
        surfaceTreatment = surfaceTreatment,
        patternIntensity = 0.42f,
        containerAlpha = 0.70f,
    ),
    control = UnifiedReaderComponentRecipe(
        surfaceTreatment = surfaceTreatment,
        selectionTreatment = selectionTreatment,
        patternIntensity = 0.36f,
        containerAlpha = 0.68f,
    ),
    sheet = UnifiedReaderComponentRecipe(
        surfaceTreatment = surfaceTreatment,
        borderTreatment = borderTreatment,
        frameTreatment = frameTreatment,
        selectionTreatment = selectionTreatment,
        patternIntensity = 0.62f,
        borderAlpha = 0.58f,
        frameAlpha = 0.58f,
        borderWidth = borderWidth,
        containerAlpha = 0.96f,
    ),
    metadata = UnifiedReaderComponentRecipe(
        surfaceTreatment = surfaceTreatment,
        borderTreatment = borderTreatment,
        frameTreatment = frameTreatment,
        patternIntensity = 0.30f,
        frameAlpha = 0.10f,
        borderWidth = borderWidth,
        containerAlpha = 0.38f,
    ),
    rail = UnifiedReaderComponentRecipe(
        surfaceTreatment = surfaceTreatment,
        borderTreatment = borderTreatment,
        frameTreatment = frameTreatment,
        selectionTreatment = selectionTreatment,
        patternIntensity = 0.38f,
        frameAlpha = 0.14f,
        borderWidth = borderWidth,
        containerAlpha = 0.76f,
    ),
    backToTop = UnifiedReaderComponentRecipe(
        surfaceTreatment = surfaceTreatment,
        borderTreatment = borderTreatment,
        frameTreatment = frameTreatment,
        selectionTreatment = selectionTreatment,
        patternIntensity = 0.38f,
        frameAlpha = 0.14f,
        borderWidth = borderWidth,
        containerAlpha = 0.88f,
    ),
)
