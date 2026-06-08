package com.example.unifiedreader.ui.theme

import androidx.compose.ui.unit.dp

internal val BlueprintComponentTokens = UnifiedReaderComponentTokens(
    appBackground = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.BlueprintGrid,
        patternIntensity = 0.34f,
    ),
    chrome = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.BlueprintGrid,
        patternIntensity = 0.52f,
        containerAlpha = 0.74f,
    ),
    control = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.BlueprintGrid,
        selectionTreatment = UnifiedReaderSelectionTreatment.SideMark,
        patternIntensity = 0.44f,
        containerAlpha = 0.70f,
    ),
    sheet = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.BlueprintGrid,
        borderTreatment = UnifiedReaderBorderTreatment.Double,
        frameTreatment = UnifiedReaderFrameTreatment.Blueprint,
        patternIntensity = 0.72f,
        borderAlpha = 0.72f,
        frameAlpha = 0.72f,
        borderWidth = 1.5.dp,
        containerAlpha = 0.96f,
    ),
    metadata = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.BlueprintGrid,
        borderTreatment = UnifiedReaderBorderTreatment.Dashed,
        frameTreatment = UnifiedReaderFrameTreatment.Blueprint,
        patternIntensity = 0.38f,
        frameAlpha = 0.12f,
        containerAlpha = 0.46f,
    ),
    rail = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.BlueprintGrid,
        borderTreatment = UnifiedReaderBorderTreatment.Dashed,
        frameTreatment = UnifiedReaderFrameTreatment.Blueprint,
        selectionTreatment = UnifiedReaderSelectionTreatment.SideMark,
        patternIntensity = 0.52f,
        frameAlpha = 0.16f,
        containerAlpha = 0.82f,
    ),
    backToTop = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.BlueprintGrid,
        borderTreatment = UnifiedReaderBorderTreatment.Dashed,
        frameTreatment = UnifiedReaderFrameTreatment.Blueprint,
        selectionTreatment = UnifiedReaderSelectionTreatment.SideMark,
        patternIntensity = 0.52f,
        frameAlpha = 0.16f,
        containerAlpha = 0.90f,
    ),
)
