package com.example.unifiedreader.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class UnifiedReaderSemanticColors(
    val appBackground: Color,
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val surfaceSelected: Color,
    val readerChrome: Color,
    val readerChromeElevated: Color,
    val readerScrim: Color,
    val readerAccent: Color,
    val syncAccent: Color,
    val sourceAccent: Color,
)

internal val LocalUnifiedReaderSemanticColors = staticCompositionLocalOf {
    UnifiedReaderSemanticColors(
        appBackground = UnifiedReaderPalette.Paper,
        surfaceBase = UnifiedReaderPalette.WarmSurface,
        surfaceRaised = Color(0xFFFFF3E7),
        surfaceOverlay = Color(0xFFF0E3D6),
        surfaceSelected = Color(0xFFD3E7EF),
        readerChrome = Color(0xF2111418),
        readerChromeElevated = Color(0xFA171A1F),
        readerScrim = Color(0xB0000000),
        readerAccent = Color(0xFF9DBBFF),
        syncAccent = UnifiedReaderPalette.ReadGreen,
        sourceAccent = UnifiedReaderPalette.CoverBlue,
    )
}

object UnifiedReaderThemeTokens {
    val colors: UnifiedReaderSemanticColors
        @androidx.compose.runtime.Composable
        get() = LocalUnifiedReaderSemanticColors.current
}
