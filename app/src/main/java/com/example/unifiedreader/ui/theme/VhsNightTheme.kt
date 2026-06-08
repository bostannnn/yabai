package com.example.unifiedreader.ui.theme

import androidx.compose.ui.unit.dp

internal val VhsNightComponentTokens = UnifiedReaderComponentTokens(
    appBackground = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.VhsScanlines,
        patternIntensity = 0.24f,
    ),
    chrome = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.VhsScanlines,
        patternIntensity = 0.48f,
        containerAlpha = 0.70f,
    ),
    control = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.VhsScanlines,
        selectionTreatment = UnifiedReaderSelectionTreatment.Underline,
        patternIntensity = 0.42f,
        containerAlpha = 0.64f,
    ),
    sheet = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.VhsScanlines,
        borderTreatment = UnifiedReaderBorderTreatment.Double,
        frameTreatment = UnifiedReaderFrameTreatment.Vhs,
        patternIntensity = 0.66f,
        borderAlpha = 0.46f,
        frameAlpha = 0.46f,
        borderWidth = 1.5.dp,
        containerAlpha = 0.97f,
    ),
    metadata = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.VhsScanlines,
        frameTreatment = UnifiedReaderFrameTreatment.Vhs,
        patternIntensity = 0.34f,
        frameAlpha = 0.08f,
        containerAlpha = 0.34f,
    ),
    rail = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.VhsScanlines,
        frameTreatment = UnifiedReaderFrameTreatment.Vhs,
        selectionTreatment = UnifiedReaderSelectionTreatment.Underline,
        patternIntensity = 0.46f,
        frameAlpha = 0.10f,
        containerAlpha = 0.78f,
    ),
    backToTop = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.VhsScanlines,
        frameTreatment = UnifiedReaderFrameTreatment.Vhs,
        selectionTreatment = UnifiedReaderSelectionTreatment.Underline,
        patternIntensity = 0.46f,
        frameAlpha = 0.10f,
        containerAlpha = 0.88f,
    ),
)
