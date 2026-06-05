package com.example.unifiedreader.ui.theme

import androidx.compose.ui.unit.dp

internal val DetectiveFileComponentTokens = UnifiedReaderComponentTokens(
    appBackground = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.DossierPaper,
        patternIntensity = 0.22f,
    ),
    chrome = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.DossierPaper,
        patternIntensity = 0.48f,
        containerAlpha = 0.82f,
    ),
    control = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.DossierPaper,
        selectionTreatment = UnifiedReaderSelectionTreatment.Stamp,
        patternIntensity = 0.38f,
        containerAlpha = 0.84f,
    ),
    sheet = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.DossierPaper,
        borderTreatment = UnifiedReaderBorderTreatment.Double,
        frameTreatment = UnifiedReaderFrameTreatment.Dossier,
        selectionTreatment = UnifiedReaderSelectionTreatment.Stamp,
        patternIntensity = 0.72f,
        borderAlpha = 0.62f,
        frameAlpha = 0.62f,
        borderWidth = 1.5.dp,
        containerAlpha = 0.98f,
    ),
    metadata = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.DossierPaper,
        borderTreatment = UnifiedReaderBorderTreatment.Dashed,
        frameTreatment = UnifiedReaderFrameTreatment.Dossier,
        patternIntensity = 0.36f,
        frameAlpha = 0.12f,
        containerAlpha = 0.72f,
    ),
    rail = UnifiedReaderComponentRecipe(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.DossierPaper,
        borderTreatment = UnifiedReaderBorderTreatment.Dashed,
        frameTreatment = UnifiedReaderFrameTreatment.Dossier,
        selectionTreatment = UnifiedReaderSelectionTreatment.Stamp,
        patternIntensity = 0.42f,
        frameAlpha = 0.16f,
        containerAlpha = 0.90f,
    ),
)
