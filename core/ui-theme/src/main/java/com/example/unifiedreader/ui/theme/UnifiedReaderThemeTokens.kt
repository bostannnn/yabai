package com.example.unifiedreader.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class UnifiedReaderSemanticColors(
    val appBackground: Color,
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val surfaceSelected: Color,
    val onSurfaceSelected: Color,
    val readerChrome: Color,
    val readerChromeElevated: Color,
    val readerScrim: Color,
    val readerAccent: Color,
    val onReaderAccent: Color,
    val readerAccentContainer: Color,
    val onReaderAccentContainer: Color,
    val syncAccent: Color,
    val onSyncAccent: Color,
    val syncAccentContainer: Color,
    val onSyncAccentContainer: Color,
    val sourceAccent: Color,
    val onSourceAccent: Color,
    val sourceAccentContainer: Color,
    val onSourceAccentContainer: Color,
)

@Immutable
data class UnifiedReaderPresentationTokens(
    val cardCornerRadius: Dp,
    val coverCornerRadius: Dp,
    val controlCornerRadius: Dp,
    val chipCornerRadius: Dp,
    val shelfCardHorizontalPadding: Dp,
    val shelfCardVerticalPadding: Dp,
    val shelfItemSpacing: Dp,
    val searchHeight: Dp,
    val accentRailWidth: Dp,
    val alphabetRailWidth: Dp,
)

enum class UnifiedReaderSurfaceTreatment {
    Plain,
    TerminalGrid,
    GothicVignette,
    ComicHalftone,
    BlueprintGrid,
    VhsScanlines,
    DossierPaper,
}

enum class UnifiedReaderProgressTreatment {
    Solid,
    Segmented,
    TapeCounter,
    Dashed,
}

enum class UnifiedReaderBorderTreatment {
    Single,
    Double,
    Ink,
    Dashed,
}

enum class UnifiedReaderFrameTreatment {
    None,
    Terminal,
    Ornate,
    Comic,
    Blueprint,
    Vhs,
    Dossier,
}

enum class UnifiedReaderSelectionTreatment {
    Fill,
    Inverse,
    Underline,
    SideMark,
    Stamp,
}

@Immutable
data class UnifiedReaderComponentRecipe(
    val surfaceTreatment: UnifiedReaderSurfaceTreatment = UnifiedReaderSurfaceTreatment.Plain,
    val borderTreatment: UnifiedReaderBorderTreatment = UnifiedReaderBorderTreatment.Single,
    val frameTreatment: UnifiedReaderFrameTreatment = UnifiedReaderFrameTreatment.None,
    val selectionTreatment: UnifiedReaderSelectionTreatment = UnifiedReaderSelectionTreatment.Fill,
    val patternIntensity: Float = 0f,
    val borderAlpha: Float = 0f,
    val frameAlpha: Float = 0f,
    val borderWidth: Dp = 1.dp,
    val containerAlpha: Float = 0f,
)

@Immutable
data class UnifiedReaderComponentTokens(
    val appBackground: UnifiedReaderComponentRecipe = UnifiedReaderComponentRecipe(),
    val chrome: UnifiedReaderComponentRecipe = UnifiedReaderComponentRecipe(),
    val control: UnifiedReaderComponentRecipe = UnifiedReaderComponentRecipe(),
    val sheet: UnifiedReaderComponentRecipe = UnifiedReaderComponentRecipe(),
    val metadata: UnifiedReaderComponentRecipe = UnifiedReaderComponentRecipe(),
    val rail: UnifiedReaderComponentRecipe = UnifiedReaderComponentRecipe(
        containerAlpha = 0.94f,
    ),
)

@Immutable
data class UnifiedReaderPersonalityTokens(
    val surfaceTreatment: UnifiedReaderSurfaceTreatment,
    val progressTreatment: UnifiedReaderProgressTreatment,
    val borderTreatment: UnifiedReaderBorderTreatment,
)

val LocalUnifiedReaderSemanticColors = staticCompositionLocalOf {
    UnifiedReaderSemanticColors(
        appBackground = Color(0xFFFFF8F0),
        surfaceBase = Color(0xFFFFFBF7),
        surfaceRaised = Color(0xFFFFF3E7),
        surfaceOverlay = Color(0xFFF0E3D6),
        surfaceSelected = Color(0xFFD3E7EF),
        onSurfaceSelected = Color(0xFF0D3642),
        readerChrome = Color(0xF2111418),
        readerChromeElevated = Color(0xFA171A1F),
        readerScrim = Color(0xB0000000),
        readerAccent = Color(0xFF9DBBFF),
        onReaderAccent = Color(0xFF092052),
        readerAccentContainer = Color(0xFFD3E7EF),
        onReaderAccentContainer = Color(0xFF09232D),
        syncAccent = Color(0xFF476A53),
        onSyncAccent = Color(0xFFFFFFFF),
        syncAccentContainer = Color(0xFFCDEBDA),
        onSyncAccentContainer = Color(0xFF072111),
        sourceAccent = Color(0xFF405F6B),
        onSourceAccent = Color(0xFFFFFFFF),
        sourceAccentContainer = Color(0xFFFFDAD7),
        onSourceAccentContainer = Color(0xFF34100F),
    )
}

val LocalUnifiedReaderPresentationTokens = staticCompositionLocalOf {
    UnifiedReaderPresentationTokens(
        cardCornerRadius = 18.dp,
        coverCornerRadius = 6.dp,
        controlCornerRadius = 12.dp,
        chipCornerRadius = 8.dp,
        shelfCardHorizontalPadding = 14.dp,
        shelfCardVerticalPadding = 12.dp,
        shelfItemSpacing = 12.dp,
        searchHeight = 40.dp,
        accentRailWidth = 4.dp,
        alphabetRailWidth = 28.dp,
    )
}

val LocalUnifiedReaderPersonalityTokens = staticCompositionLocalOf {
    UnifiedReaderPersonalityTokens(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.Plain,
        progressTreatment = UnifiedReaderProgressTreatment.Solid,
        borderTreatment = UnifiedReaderBorderTreatment.Single,
    )
}

val LocalUnifiedReaderComponentTokens = staticCompositionLocalOf {
    UnifiedReaderComponentTokens()
}

object UnifiedReaderThemeTokens {
    val colors: UnifiedReaderSemanticColors
        @androidx.compose.runtime.Composable
        get() = LocalUnifiedReaderSemanticColors.current

    val presentation: UnifiedReaderPresentationTokens
        @androidx.compose.runtime.Composable
        get() = LocalUnifiedReaderPresentationTokens.current

    val personality: UnifiedReaderPersonalityTokens
        @androidx.compose.runtime.Composable
        get() = LocalUnifiedReaderPersonalityTokens.current

    val components: UnifiedReaderComponentTokens
        @androidx.compose.runtime.Composable
        get() = LocalUnifiedReaderComponentTokens.current
}
