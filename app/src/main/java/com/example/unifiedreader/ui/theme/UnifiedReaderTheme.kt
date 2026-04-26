package com.example.unifiedreader.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
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
import androidx.core.view.WindowCompat
import com.example.unifiedreader.feature.settings.ThemeMode

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

private data class UnifiedReaderResolvedTheme(
    val colorScheme: ColorScheme,
    val semanticColors: UnifiedReaderSemanticColors,
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

    CompositionLocalProvider(LocalUnifiedReaderSemanticColors provides resolvedTheme.semanticColors) {
        MaterialTheme(
            colorScheme = resolvedTheme.colorScheme,
            typography = UnifiedReaderTypography,
            shapes = UnifiedReaderShapes,
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
        else -> if (themeMode.isDark) GraphiteUnifiedReaderColorScheme else LightUnifiedReaderColorScheme
    }
    val isDark = if (themeMode == ThemeMode.SystemDynamic) systemDarkTheme else themeMode.isDark
    return UnifiedReaderResolvedTheme(
        colorScheme = colorScheme,
        semanticColors = colorScheme.toSemanticColors(isDark = isDark),
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
        readerChrome = scrim.copy(alpha = readerChromeAlpha),
        readerChromeElevated = surfaceContainerHighest.copy(alpha = readerChromeElevatedAlpha),
        readerScrim = scrim.copy(alpha = if (isDark) 0.74f else 0.46f),
        readerAccent = primary,
        syncAccent = secondary,
        sourceAccent = tertiary,
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
