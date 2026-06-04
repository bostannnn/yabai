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
    )
}

val LocalUnifiedReaderPersonalityTokens = staticCompositionLocalOf {
    UnifiedReaderPersonalityTokens(
        surfaceTreatment = UnifiedReaderSurfaceTreatment.Plain,
        progressTreatment = UnifiedReaderProgressTreatment.Solid,
        borderTreatment = UnifiedReaderBorderTreatment.Single,
    )
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
}
