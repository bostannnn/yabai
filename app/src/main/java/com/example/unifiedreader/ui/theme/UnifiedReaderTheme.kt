package com.example.unifiedreader.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun UnifiedReaderTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val systemDarkTheme = isSystemInDarkTheme()
    val resolvedDarkTheme = when (themeMode) {
        ThemeMode.System -> systemDarkTheme
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (resolvedDarkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        resolvedDarkTheme -> DarkUnifiedReaderColorScheme
        else -> LightUnifiedReaderColorScheme
    }
    val activity = context.findActivity()

    if (!view.isInEditMode && activity != null) {
        SideEffect {
            val window = activity.window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !resolvedDarkTheme
                isAppearanceLightNavigationBars = !resolvedDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UnifiedReaderTypography,
        shapes = UnifiedReaderShapes,
        content = content,
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
