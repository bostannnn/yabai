package com.example.unifiedreader.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.unifiedreader.feature.settings.ThemeMode
import com.example.unifiedreader.ui.theme.UnifiedReaderBorderTreatment.Dashed
import com.example.unifiedreader.ui.theme.UnifiedReaderBorderTreatment.Double
import com.example.unifiedreader.ui.theme.UnifiedReaderBorderTreatment.Ink
import com.example.unifiedreader.ui.theme.UnifiedReaderBorderTreatment.Single
import com.example.unifiedreader.ui.theme.UnifiedReaderProgressTreatment.Dashed as DashedProgress
import com.example.unifiedreader.ui.theme.UnifiedReaderProgressTreatment.Segmented
import com.example.unifiedreader.ui.theme.UnifiedReaderProgressTreatment.Solid
import com.example.unifiedreader.ui.theme.UnifiedReaderProgressTreatment.TapeCounter
import com.example.unifiedreader.ui.theme.UnifiedReaderSurfaceTreatment.BlueprintGrid
import com.example.unifiedreader.ui.theme.UnifiedReaderSurfaceTreatment.ComicHalftone
import com.example.unifiedreader.ui.theme.UnifiedReaderSurfaceTreatment.DossierPaper
import com.example.unifiedreader.ui.theme.UnifiedReaderSurfaceTreatment.GothicVignette
import com.example.unifiedreader.ui.theme.UnifiedReaderSurfaceTreatment.Plain
import com.example.unifiedreader.ui.theme.UnifiedReaderSurfaceTreatment.TerminalGrid
import com.example.unifiedreader.ui.theme.UnifiedReaderSurfaceTreatment.VhsScanlines

private val LightUnifiedReaderColorScheme = lightColorScheme(
    primary = UnifiedReaderPalette.CoverBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E7EF),
    onPrimaryContainer = Color(0xFF09232D),
    secondary = UnifiedReaderPalette.ReadGreen,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDEBDA),
    onSecondaryContainer = Color(0xFF072111),
    tertiary = UnifiedReaderPalette.BookmarkRed,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD7),
    onTertiaryContainer = Color(0xFF34100F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = UnifiedReaderPalette.Paper,
    onBackground = UnifiedReaderPalette.Ink,
    surface = UnifiedReaderPalette.WarmSurface,
    onSurface = UnifiedReaderPalette.Ink,
    surfaceDim = Color(0xFFE3D7CA),
    surfaceBright = Color(0xFFFFFBF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF3E7),
    surfaceContainer = Color(0xFFF7EBDE),
    surfaceContainerHigh = Color(0xFFF0E3D6),
    surfaceContainerHighest = Color(0xFFE8DCCF),
    surfaceVariant = Color(0xFFEDE4D9),
    onSurfaceVariant = Color(0xFF514941),
    outline = Color(0xFF82766B),
    outlineVariant = UnifiedReaderPalette.SoftOutline,
    inverseSurface = Color(0xFF303033),
    inverseOnSurface = Color(0xFFF3EFEA),
    inversePrimary = Color(0xFFAFCBD4),
    scrim = Color(0xFF1B1C1F),
)

private val DarkUnifiedReaderColorScheme = darkColorScheme(
    primary = Color(0xFFAFCBD4),
    onPrimary = Color(0xFF12323D),
    primaryContainer = Color(0xFF294A55),
    onPrimaryContainer = Color(0xFFD3E7EF),
    secondary = Color(0xFFAFCFB9),
    onSecondary = Color(0xFF193525),
    secondaryContainer = Color(0xFF304C3A),
    onSecondaryContainer = Color(0xFFCDEBDA),
    tertiary = Color(0xFFF0B8B4),
    onTertiary = Color(0xFF512522),
    tertiaryContainer = Color(0xFF6B3B38),
    onTertiaryContainer = Color(0xFFFFDAD7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = UnifiedReaderPalette.DarkPaper,
    onBackground = UnifiedReaderPalette.DarkInk,
    surface = UnifiedReaderPalette.DarkSurface,
    onSurface = UnifiedReaderPalette.DarkInk,
    surfaceDim = Color(0xFF111417),
    surfaceBright = Color(0xFF373A3E),
    surfaceContainerLowest = Color(0xFF0C0F12),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2125),
    surfaceContainerHigh = Color(0xFF282C30),
    surfaceContainerHighest = Color(0xFF33373C),
    surfaceVariant = UnifiedReaderPalette.DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC3C8CC),
    outline = UnifiedReaderPalette.DarkOutline,
    outlineVariant = Color(0xFF424A4F),
    inverseSurface = Color(0xFFE8E1D7),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = UnifiedReaderPalette.CoverBlue,
    scrim = Color(0xFF000000),
)

private val GraphiteUnifiedReaderColorScheme = darkColorScheme(
    primary = Color(0xFFB7C7D9),
    onPrimary = Color(0xFF223244),
    primaryContainer = Color(0xFF33475E),
    onPrimaryContainer = Color(0xFFD6E4F5),
    secondary = Color(0xFFC4CFD8),
    onSecondary = Color(0xFF29333B),
    secondaryContainer = Color(0xFF3F4A54),
    onSecondaryContainer = Color(0xFFE0E8EF),
    tertiary = Color(0xFFE4B8A7),
    onTertiary = Color(0xFF4A2B20),
    tertiaryContainer = Color(0xFF664134),
    onTertiaryContainer = Color(0xFFFFDBCE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101214),
    onBackground = Color(0xFFE7EAEE),
    surface = Color(0xFF171A1D),
    onSurface = Color(0xFFE7EAEE),
    surfaceDim = Color(0xFF101214),
    surfaceBright = Color(0xFF363A3F),
    surfaceContainerLowest = Color(0xFF0B0D0F),
    surfaceContainerLow = Color(0xFF1D2125),
    surfaceContainer = Color(0xFF252A2F),
    surfaceContainerHigh = Color(0xFF30363C),
    surfaceContainerHighest = Color(0xFF3C434A),
    surfaceVariant = Color(0xFF454B52),
    onSurfaceVariant = Color(0xFFC8D0D8),
    outline = Color(0xFF98A2AD),
    outlineVariant = Color(0xFF4E5660),
    inverseSurface = Color(0xFFE1E5EA),
    inverseOnSurface = Color(0xFF262A2E),
    inversePrimary = Color(0xFF405F7A),
    scrim = Color(0xFF000000),
)

private val HighContrastUnifiedReaderColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF201600),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE7A3),
    tertiary = Color(0xFF82D4FF),
    onTertiary = Color(0xFF001F2D),
    tertiaryContainer = Color(0xFF004D68),
    onTertiaryContainer = Color(0xFFC2EFFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0C0C0C),
    onSurface = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3A3A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1F1F1F),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF3A3A3A),
    surfaceVariant = Color(0xFF4E4E4E),
    onSurfaceVariant = Color(0xFFECECEC),
    outline = Color(0xFFFFFFFF),
    outlineVariant = Color(0xFF777777),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
    inversePrimary = Color(0xFF000000),
    scrim = Color(0xFF000000),
)

private val OledReaderUnifiedReaderColorScheme = darkColorScheme(
    primary = Color(0xFFB9C7FF),
    onPrimary = Color(0xFF202A55),
    primaryContainer = Color(0xFF303A68),
    onPrimaryContainer = Color(0xFFE0E5FF),
    secondary = Color(0xFFBFC6D8),
    onSecondary = Color(0xFF292E3A),
    secondaryContainer = Color(0xFF3F4552),
    onSecondaryContainer = Color(0xFFDDE3F2),
    tertiary = Color(0xFFE5B8C8),
    onTertiary = Color(0xFF462534),
    tertiaryContainer = Color(0xFF603B4A),
    onTertiaryContainer = Color(0xFFFFD9E6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE7EAF2),
    surface = Color(0xFF08090B),
    onSurface = Color(0xFFE7EAF2),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A2D33),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0C0E12),
    surfaceContainer = Color(0xFF14171D),
    surfaceContainerHigh = Color(0xFF20242C),
    surfaceContainerHighest = Color(0xFF2D323B),
    surfaceVariant = Color(0xFF3E4654),
    onSurfaceVariant = Color(0xFFC4CAD6),
    outline = Color(0xFF8D96A6),
    outlineVariant = Color(0xFF3E4654),
    inverseSurface = Color(0xFFE4E7EF),
    inverseOnSurface = Color(0xFF1C1F26),
    inversePrimary = Color(0xFF4B5D9E),
    scrim = Color(0xFF000000),
)

private val SepiaReadingUnifiedReaderColorScheme = lightColorScheme(
    primary = Color(0xFF665037),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECD8BC),
    onPrimaryContainer = Color(0xFF241A0D),
    secondary = Color(0xFF5F5941),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE7DDBF),
    onSecondaryContainer = Color(0xFF1F1C0D),
    tertiary = Color(0xFF7B4E3F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF321207),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7EBD8),
    onBackground = Color(0xFF211A12),
    surface = Color(0xFFFFF7EA),
    onSurface = Color(0xFF211A12),
    surfaceDim = Color(0xFFDACDB9),
    surfaceBright = Color(0xFFFFF8EF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF0DD),
    surfaceContainer = Color(0xFFF4E5CE),
    surfaceContainerHigh = Color(0xFFEAD9C0),
    surfaceContainerHighest = Color(0xFFE0CEB3),
    surfaceVariant = Color(0xFFE8DDCC),
    onSurfaceVariant = Color(0xFF50483B),
    outline = Color(0xFF807464),
    outlineVariant = Color(0xFFD0C3B0),
    inverseSurface = Color(0xFF362F24),
    inverseOnSurface = Color(0xFFFBEEDA),
    inversePrimary = Color(0xFFDCC5A5),
    scrim = Color(0xFF1D160F),
)

private val Terminal95UnifiedReaderColorScheme = darkColorScheme(
    primary = Color(0xFF7CFF8A),
    onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF00551D),
    onPrimaryContainer = Color(0xFFC8FFD0),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Color(0xFF5A4000),
    onSecondaryContainer = Color(0xFFFFE8A6),
    tertiary = Color(0xFF6DE7FF),
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF005462),
    onTertiaryContainer = Color(0xFFB9F4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF020704),
    onBackground = Color(0xFFE4F8E7),
    surface = Color(0xFF07110A),
    onSurface = Color(0xFFE4F8E7),
    surfaceDim = Color(0xFF020704),
    surfaceBright = Color(0xFF29382C),
    surfaceContainerLowest = Color(0xFF010301),
    surfaceContainerLow = Color(0xFF0B150E),
    surfaceContainer = Color(0xFF101D13),
    surfaceContainerHigh = Color(0xFF18271B),
    surfaceContainerHighest = Color(0xFF223126),
    surfaceVariant = Color(0xFF243B2A),
    onSurfaceVariant = Color(0xFFC0D8C6),
    outline = Color(0xFF5CE486),
    outlineVariant = Color(0xFF315A3B),
    inverseSurface = Color(0xFFE4F8E7),
    inverseOnSurface = Color(0xFF111D14),
    inversePrimary = Color(0xFF006D29),
    scrim = Color(0xFF000000),
)

private val CrimsonCourtUnifiedReaderColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B86),
    onPrimary = Color(0xFF4B0015),
    primaryContainer = Color(0xFF791B30),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFE8C276),
    onSecondary = Color(0xFF3A2900),
    secondaryContainer = Color(0xFF594000),
    onSecondaryContainer = Color(0xFFFFE4A7),
    tertiary = Color(0xFFC8B6FF),
    onTertiary = Color(0xFF30185F),
    tertiaryContainer = Color(0xFF4A3279),
    onTertiaryContainer = Color(0xFFE7DDFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10090D),
    onBackground = Color(0xFFF8E8EE),
    surface = Color(0xFF191016),
    onSurface = Color(0xFFF8E8EE),
    surfaceDim = Color(0xFF10090D),
    surfaceBright = Color(0xFF46323A),
    surfaceContainerLowest = Color(0xFF090407),
    surfaceContainerLow = Color(0xFF21151C),
    surfaceContainer = Color(0xFF2A1A22),
    surfaceContainerHigh = Color(0xFF35232C),
    surfaceContainerHighest = Color(0xFF412D36),
    surfaceVariant = Color(0xFF5A3945),
    onSurfaceVariant = Color(0xFFDDC0CA),
    outline = Color(0xFFC47A8D),
    outlineVariant = Color(0xFF6E4050),
    inverseSurface = Color(0xFFF8E8EE),
    inverseOnSurface = Color(0xFF35232C),
    inversePrimary = Color(0xFFA1304A),
    scrim = Color(0xFF000000),
)

private val PanelPopUnifiedReaderColorScheme = lightColorScheme(
    primary = Color(0xFF006E8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F1FF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFFC8176B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8E8),
    onSecondaryContainer = Color(0xFF3F001C),
    tertiary = Color(0xFF8A6500),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE27A),
    onTertiaryContainer = Color(0xFF2A2000),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFDF6),
    onBackground = Color(0xFF15171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15171C),
    surfaceDim = Color(0xFFD7DFEA),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF9FF),
    surfaceContainer = Color(0xFFFFF1F6),
    surfaceContainerHigh = Color(0xFFFFF4BF),
    surfaceContainerHighest = Color(0xFFE1F1FF),
    surfaceVariant = Color(0xFFDEE7F5),
    onSurfaceVariant = Color(0xFF344252),
    outline = Color(0xFF3B4758),
    outlineVariant = Color(0xFFA9B7C9),
    inverseSurface = Color(0xFF20232A),
    inverseOnSurface = Color(0xFFF4F7FF),
    inversePrimary = Color(0xFF7AD9FF),
    scrim = Color(0xFF000000),
)

private val BlueprintUnifiedReaderColorScheme = darkColorScheme(
    primary = Color(0xFF9BE7FF),
    onPrimary = Color(0xFF003544),
    primaryContainer = Color(0xFF0B4F65),
    onPrimaryContainer = Color(0xFFC9F3FF),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF3E2D00),
    secondaryContainer = Color(0xFF5E4300),
    onSecondaryContainer = Color(0xFFFFE3A1),
    tertiary = Color(0xFFB8D8FF),
    onTertiary = Color(0xFF173351),
    tertiaryContainer = Color(0xFF2F4A69),
    onTertiaryContainer = Color(0xFFD7E9FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF06182A),
    onBackground = Color(0xFFE5F3FF),
    surface = Color(0xFF092139),
    onSurface = Color(0xFFE5F3FF),
    surfaceDim = Color(0xFF06182A),
    surfaceBright = Color(0xFF2D435B),
    surfaceContainerLowest = Color(0xFF03111F),
    surfaceContainerLow = Color(0xFF0D2740),
    surfaceContainer = Color(0xFF13304B),
    surfaceContainerHigh = Color(0xFF1A3A57),
    surfaceContainerHighest = Color(0xFF254965),
    surfaceVariant = Color(0xFF324A64),
    onSurfaceVariant = Color(0xFFC4D6E8),
    outline = Color(0xFF8BCBE0),
    outlineVariant = Color(0xFF3D5B73),
    inverseSurface = Color(0xFFE5F3FF),
    inverseOnSurface = Color(0xFF173047),
    inversePrimary = Color(0xFF00687F),
    scrim = Color(0xFF000000),
)

private val VhsNightUnifiedReaderColorScheme = darkColorScheme(
    primary = Color(0xFF86CAD5),
    onPrimary = Color(0xFF082F35),
    primaryContainer = Color(0xFF29474D),
    onPrimaryContainer = Color(0xFFC5F3FA),
    secondary = Color(0xFFFF5C7A),
    onSecondary = Color(0xFF570014),
    secondaryContainer = Color(0xFF812238),
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = Color(0xFFE1D7BA),
    onTertiary = Color(0xFF363017),
    tertiaryContainer = Color(0xFF4F472C),
    onTertiaryContainer = Color(0xFFFEEFCB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101018),
    onBackground = Color(0xFFECEAF4),
    surface = Color(0xFF171720),
    onSurface = Color(0xFFECEAF4),
    surfaceDim = Color(0xFF101018),
    surfaceBright = Color(0xFF3A3945),
    surfaceContainerLowest = Color(0xFF0B0B11),
    surfaceContainerLow = Color(0xFF1D1C26),
    surfaceContainer = Color(0xFF25242F),
    surfaceContainerHigh = Color(0xFF302F3B),
    surfaceContainerHighest = Color(0xFF3B3948),
    surfaceVariant = Color(0xFF4C4856),
    onSurfaceVariant = Color(0xFFD0C9D9),
    outline = Color(0xFF9B94A2),
    outlineVariant = Color(0xFF544F60),
    inverseSurface = Color(0xFFECEAF4),
    inverseOnSurface = Color(0xFF2C2C39),
    inversePrimary = Color(0xFF006874),
    scrim = Color(0xFF000000),
)

private val DetectiveFileUnifiedReaderColorScheme = lightColorScheme(
    primary = Color(0xFF233D5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3F7),
    onPrimaryContainer = Color(0xFF061B31),
    secondary = Color(0xFFB3362D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF410002),
    tertiary = Color(0xFF8A6B2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA0),
    onTertiaryContainer = Color(0xFF2C2000),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFEBD8AE),
    onBackground = Color(0xFF221A0D),
    surface = Color(0xFFF3E4C2),
    onSurface = Color(0xFF221A0D),
    surfaceDim = Color(0xFFD7C498),
    surfaceBright = Color(0xFFFFF2D2),
    surfaceContainerLowest = Color(0xFFFFF6DA),
    surfaceContainerLow = Color(0xFFF9E8C4),
    surfaceContainer = Color(0xFFEFDDB7),
    surfaceContainerHigh = Color(0xFFE4D0A8),
    surfaceContainerHighest = Color(0xFFD8C49A),
    surfaceVariant = Color(0xFFE2D0AF),
    onSurfaceVariant = Color(0xFF504633),
    outline = Color(0xFF786B52),
    outlineVariant = Color(0xFFC9B897),
    inverseSurface = Color(0xFF382F20),
    inverseOnSurface = Color(0xFFFCECC7),
    inversePrimary = Color(0xFFA8C8EA),
    scrim = Color(0xFF1D160F),
)

private data class UnifiedReaderResolvedTheme(
    val colorScheme: ColorScheme,
    val semanticColors: UnifiedReaderSemanticColors,
    val presentationTokens: UnifiedReaderPresentationTokens,
    val personalityTokens: UnifiedReaderPersonalityTokens,
    val componentTokens: UnifiedReaderComponentTokens,
    val typography: Typography,
    val shapes: Shapes,
    val isDark: Boolean,
)

@Composable
fun UnifiedReaderTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val systemDarkTheme = isSystemInDarkTheme()
    val resolvedTheme = resolveUnifiedReaderTheme(
        context = context,
        themeMode = themeMode,
        systemDarkTheme = systemDarkTheme,
    )
    val activity = context.findActivity()

    if (!view.isInEditMode && activity != null) {
        SideEffect {
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !resolvedTheme.isDark
                isAppearanceLightNavigationBars = !resolvedTheme.isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalUnifiedReaderSemanticColors provides resolvedTheme.semanticColors,
        LocalUnifiedReaderPresentationTokens provides resolvedTheme.presentationTokens,
        LocalUnifiedReaderPersonalityTokens provides resolvedTheme.personalityTokens,
        LocalUnifiedReaderComponentTokens provides resolvedTheme.componentTokens,
    ) {
        MaterialTheme(
            colorScheme = resolvedTheme.colorScheme,
            typography = resolvedTheme.typography,
            shapes = resolvedTheme.shapes,
            content = content,
        )
    }
}

@Composable
private fun resolveUnifiedReaderTheme(
    context: Context,
    themeMode: ThemeMode,
    systemDarkTheme: Boolean,
): UnifiedReaderResolvedTheme {
    val colorScheme = when {
        themeMode.usesDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (systemDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == ThemeMode.SystemDynamic && systemDarkTheme -> DarkUnifiedReaderColorScheme
        themeMode == ThemeMode.SystemDynamic -> LightUnifiedReaderColorScheme
        themeMode == ThemeMode.Paper || themeMode == ThemeMode.Light -> LightUnifiedReaderColorScheme
        themeMode == ThemeMode.Graphite || themeMode == ThemeMode.Dark -> GraphiteUnifiedReaderColorScheme
        themeMode == ThemeMode.HighContrast -> HighContrastUnifiedReaderColorScheme
        themeMode == ThemeMode.OledReader -> OledReaderUnifiedReaderColorScheme
        themeMode == ThemeMode.SepiaReading -> SepiaReadingUnifiedReaderColorScheme
        themeMode == ThemeMode.Terminal95 -> Terminal95UnifiedReaderColorScheme
        themeMode == ThemeMode.CrimsonCourt -> CrimsonCourtUnifiedReaderColorScheme
        themeMode == ThemeMode.PanelPop -> PanelPopUnifiedReaderColorScheme
        themeMode == ThemeMode.Blueprint -> BlueprintUnifiedReaderColorScheme
        themeMode == ThemeMode.VhsNight -> VhsNightUnifiedReaderColorScheme
        themeMode == ThemeMode.DetectiveFile -> DetectiveFileUnifiedReaderColorScheme
        else -> if (themeMode.isDark) GraphiteUnifiedReaderColorScheme else LightUnifiedReaderColorScheme
    }
    val isDark = if (themeMode == ThemeMode.SystemDynamic) systemDarkTheme else themeMode.isDark
    return UnifiedReaderResolvedTheme(
        colorScheme = colorScheme,
        semanticColors = colorScheme.toSemanticColors(isDark = isDark),
        presentationTokens = themeMode.toPresentationTokens(),
        personalityTokens = themeMode.toPersonalityTokens(),
        componentTokens = themeMode.toComponentTokens(),
        typography = themeMode.toTypography(),
        shapes = themeMode.toShapes(),
        isDark = isDark,
    )
}

private fun ColorScheme.toSemanticColors(isDark: Boolean): UnifiedReaderSemanticColors {
    val readerChromeAlpha = if (isDark) 0.94f else 0.88f
    val readerChromeElevatedAlpha = if (isDark) 0.98f else 0.94f
    return UnifiedReaderSemanticColors(
        appBackground = background,
        surfaceBase = surface,
        surfaceRaised = surfaceContainer,
        surfaceOverlay = surfaceContainerHigh,
        surfaceSelected = primaryContainer,
        onSurfaceSelected = onPrimaryContainer,
        readerChrome = scrim.copy(alpha = readerChromeAlpha),
        readerChromeElevated = surfaceContainerHighest.copy(alpha = readerChromeElevatedAlpha),
        readerScrim = scrim.copy(alpha = if (isDark) 0.74f else 0.46f),
        readerAccent = primary,
        onReaderAccent = onPrimary,
        readerAccentContainer = primaryContainer,
        onReaderAccentContainer = onPrimaryContainer,
        syncAccent = secondary,
        onSyncAccent = onSecondary,
        syncAccentContainer = secondaryContainer,
        onSyncAccentContainer = onSecondaryContainer,
        sourceAccent = tertiary,
        onSourceAccent = onTertiary,
        sourceAccentContainer = tertiaryContainer,
        onSourceAccentContainer = onTertiaryContainer,
    )
}

private fun ThemeMode.toTypography(): Typography = when (this) {
    ThemeMode.Terminal95 -> Terminal95UnifiedReaderTypography
    ThemeMode.CrimsonCourt -> CrimsonCourtUnifiedReaderTypography
    ThemeMode.PanelPop -> PanelPopUnifiedReaderTypography
    ThemeMode.Blueprint -> BlueprintUnifiedReaderTypography
    ThemeMode.VhsNight -> VhsNightUnifiedReaderTypography
    ThemeMode.DetectiveFile -> DetectiveFileUnifiedReaderTypography
    else -> UnifiedReaderTypography
}

private fun ThemeMode.toShapes(): Shapes = when (this) {
    ThemeMode.Terminal95 -> Terminal95UnifiedReaderShapes
    ThemeMode.CrimsonCourt -> CrimsonCourtUnifiedReaderShapes
    ThemeMode.PanelPop -> PanelPopUnifiedReaderShapes
    ThemeMode.Blueprint -> BlueprintUnifiedReaderShapes
    ThemeMode.VhsNight -> VhsNightUnifiedReaderShapes
    ThemeMode.DetectiveFile -> DetectiveFileUnifiedReaderShapes
    else -> UnifiedReaderShapes
}

private fun ThemeMode.toPresentationTokens(): UnifiedReaderPresentationTokens = when (this) {
    ThemeMode.Terminal95 -> UnifiedReaderPresentationTokens(
        cardCornerRadius = 6.dp,
        coverCornerRadius = 4.dp,
        controlCornerRadius = 4.dp,
        chipCornerRadius = 2.dp,
        shelfCardHorizontalPadding = 10.dp,
        shelfCardVerticalPadding = 9.dp,
        shelfItemSpacing = 8.dp,
        searchHeight = 36.dp,
        accentRailWidth = 5.dp,
        alphabetRailWidth = 30.dp,
    )
    ThemeMode.CrimsonCourt -> UnifiedReaderPresentationTokens(
        cardCornerRadius = 22.dp,
        coverCornerRadius = 10.dp,
        controlCornerRadius = 14.dp,
        chipCornerRadius = 10.dp,
        shelfCardHorizontalPadding = 16.dp,
        shelfCardVerticalPadding = 14.dp,
        shelfItemSpacing = 14.dp,
        searchHeight = 42.dp,
        accentRailWidth = 5.dp,
        alphabetRailWidth = 30.dp,
    )
    ThemeMode.PanelPop -> UnifiedReaderPresentationTokens(
        cardCornerRadius = 8.dp,
        coverCornerRadius = 6.dp,
        controlCornerRadius = 8.dp,
        chipCornerRadius = 4.dp,
        shelfCardHorizontalPadding = 12.dp,
        shelfCardVerticalPadding = 10.dp,
        shelfItemSpacing = 10.dp,
        searchHeight = 38.dp,
        accentRailWidth = 6.dp,
        alphabetRailWidth = 30.dp,
    )
    ThemeMode.Blueprint -> UnifiedReaderPresentationTokens(
        cardCornerRadius = 6.dp,
        coverCornerRadius = 2.dp,
        controlCornerRadius = 4.dp,
        chipCornerRadius = 2.dp,
        shelfCardHorizontalPadding = 12.dp,
        shelfCardVerticalPadding = 10.dp,
        shelfItemSpacing = 9.dp,
        searchHeight = 38.dp,
        accentRailWidth = 3.dp,
        alphabetRailWidth = 32.dp,
    )
    ThemeMode.VhsNight -> UnifiedReaderPresentationTokens(
        cardCornerRadius = 16.dp,
        coverCornerRadius = 8.dp,
        controlCornerRadius = 12.dp,
        chipCornerRadius = 8.dp,
        shelfCardHorizontalPadding = 14.dp,
        shelfCardVerticalPadding = 12.dp,
        shelfItemSpacing = 12.dp,
        searchHeight = 40.dp,
        accentRailWidth = 5.dp,
        alphabetRailWidth = 30.dp,
    )
    ThemeMode.DetectiveFile -> UnifiedReaderPresentationTokens(
        cardCornerRadius = 8.dp,
        coverCornerRadius = 3.dp,
        controlCornerRadius = 6.dp,
        chipCornerRadius = 3.dp,
        shelfCardHorizontalPadding = 14.dp,
        shelfCardVerticalPadding = 11.dp,
        shelfItemSpacing = 10.dp,
        searchHeight = 38.dp,
        accentRailWidth = 4.dp,
        alphabetRailWidth = 32.dp,
    )
    else -> UnifiedReaderPresentationTokens(
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

private fun ThemeMode.toPersonalityTokens(): UnifiedReaderPersonalityTokens = when (this) {
    ThemeMode.Terminal95 -> UnifiedReaderPersonalityTokens(
        surfaceTreatment = TerminalGrid,
        progressTreatment = Segmented,
        borderTreatment = Single,
    )
    ThemeMode.CrimsonCourt -> UnifiedReaderPersonalityTokens(
        surfaceTreatment = GothicVignette,
        progressTreatment = Solid,
        borderTreatment = Double,
    )
    ThemeMode.PanelPop -> UnifiedReaderPersonalityTokens(
        surfaceTreatment = ComicHalftone,
        progressTreatment = Segmented,
        borderTreatment = Ink,
    )
    ThemeMode.Blueprint -> UnifiedReaderPersonalityTokens(
        surfaceTreatment = BlueprintGrid,
        progressTreatment = DashedProgress,
        borderTreatment = Dashed,
    )
    ThemeMode.VhsNight -> UnifiedReaderPersonalityTokens(
        surfaceTreatment = VhsScanlines,
        progressTreatment = TapeCounter,
        borderTreatment = Single,
    )
    ThemeMode.DetectiveFile -> UnifiedReaderPersonalityTokens(
        surfaceTreatment = DossierPaper,
        progressTreatment = Segmented,
        borderTreatment = Dashed,
    )
    else -> UnifiedReaderPersonalityTokens(
        surfaceTreatment = Plain,
        progressTreatment = Solid,
        borderTreatment = Single,
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
