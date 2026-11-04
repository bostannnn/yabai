package com.example.unifiedreader.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The static pattern a character theme draws on its screen ground (design system: `ground-pattern`). */
enum class AppGroundPattern {
    None,

    /** A dot every 16 dp (Terminal 95). */
    DotGrid,

    /** A 1 px line every 2 dp (VHS Night). */
    Scanlines,

    /** 1 px lines every 24 dp, both ways (Blueprint). */
    Grid,

    /** Small specks scattered over a 20 dp tile (Detective File). */
    Grain,
}

/**
 * What a character theme adds over its palette; a quiet theme has none of it
 * ([UnifiedReaderQuietCharacter]). The design system's README (Colour, "Quiet and character
 * themes") lists the four things and where each may appear.
 *
 * [labelColor] is `label-accent` for section headings and group labels, [barEdgeColor] the top
 * bar's glass edge (`bar-edge`); [Color.Unspecified] keeps the quiet colour. [accentSelection]
 * fills the rail's and the drawer's selected item with `primary`.
 */
@Immutable
data class UnifiedReaderThemeCharacter(
    val groundPattern: AppGroundPattern = AppGroundPattern.None,
    val patternColor: Color = Color.Transparent,
    val labelColor: Color = Color.Unspecified,
    val barEdgeColor: Color = Color.Unspecified,
    val accentSelection: Boolean = false,
)

val UnifiedReaderQuietCharacter = UnifiedReaderThemeCharacter()

val LocalUnifiedReaderThemeCharacter = staticCompositionLocalOf { UnifiedReaderQuietCharacter }
