package com.example.unifiedreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The colours picked from a cover (ARGB): the two most common ([ambient], [ambientSecond]) and
 * the one to accent with, if any is vivid enough to carry it.
 */
@Immutable
data class AppCoverSwatches(
    val ambient: Int,
    val ambientSecond: Int,
    val accent: Int?,
)

/**
 * A cover's colours as the reading card, the book page and the reader use them: the two tints
 * of the background gradient, the text and the faint track on it, and the accent with its text
 * and a quiet container for selected states.
 */
@Immutable
data class AppCoverColors(
    val tintStart: Color,
    val tintEnd: Color,
    val content: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
)

/**
 * The colours [swatches] give in a dark or light theme. Dark: the tints darkened to about
 * L 0.25-0.30 under light text, the accent lightened to about L 0.75 with dark text on it.
 * Light: pale tints (about L 0.90) under dark text, the accent darkened to about L 0.35 with
 * white text. A cover without a usable accent, or whose accent text would fall under 4.5:1,
 * keeps the theme's [fallbackAccent] and [fallbackOnAccent].
 */
fun appCoverColors(
    swatches: AppCoverSwatches,
    dark: Boolean,
    fallbackAccent: Color,
    fallbackOnAccent: Color,
): AppCoverColors {
    val tintStart = Hsl.of(swatches.ambient).let { hsl ->
        hsl.copy(s = min(hsl.s, TintMaxSaturation), l = if (dark) DarkTintStartL else LightTintStartL)
    }
    val tintEnd = Hsl.of(swatches.ambientSecond).let { hsl ->
        hsl.copy(s = min(hsl.s, TintMaxSaturation), l = if (dark) DarkTintEndL else LightTintEndL)
    }
    val accentHsl = swatches.accent?.let(Hsl::of)?.let { hsl ->
        hsl.copy(s = min(hsl.s, AccentMaxSaturation), l = if (dark) DarkAccentL else LightAccentL)
    }
    val onAccent = accentHsl?.let { hsl ->
        if (dark) hsl.copy(l = DarkOnAccentL).toArgb() else WhiteArgb
    }
    val coverAccent = accentHsl?.toArgb()
        ?.takeIf { accent -> onAccent != null && contrastRatio(accent, onAccent) >= MinTextContrast }
    val accent = coverAccent ?: fallbackAccent.toArgb()
    val accentOn = if (coverAccent != null) onAccent!! else fallbackOnAccent.toArgb()
    // On a light theme the container must still stand off a cream segmented track (L ~0.95), so
    // it is deeper and a little more saturated than the tints.
    val containerHsl = Hsl.of(accent).let { hsl ->
        if (dark) {
            hsl.copy(s = min(hsl.s, ContainerMaxSaturation), l = DarkContainerL)
        } else {
            hsl.copy(s = min(hsl.s, LightContainerMaxSaturation), l = LightContainerL)
        }
    }
    return AppCoverColors(
        tintStart = Color(tintStart.toArgb()),
        tintEnd = Color(tintEnd.toArgb()),
        content = if (dark) DarkContent else LightContent,
        track = if (dark) Color.White.copy(alpha = 0.18f) else LightContent.copy(alpha = 0.14f),
        accent = Color(accent),
        onAccent = Color(accentOn),
        accentContainer = Color(containerHsl.toArgb()),
        onAccentContainer = Color(containerHsl.copy(l = if (dark) DarkOnContainerL else LightOnContainerL).toArgb()),
    )
}

/** Whether the current theme is a dark one, by its background. */
val isAppThemeDark: Boolean
    @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Gives [content] the cover's accent in place of the theme's: Material's primary and primary
 * container, and the app's selected and reader accent roles. Without [colors] the theme's own
 * stay. The content is composed the same way either way, so a palette that arrives late
 * recolours it without rebuilding it (a reader keeps its page).
 */
@Composable
fun AppCoverAccentTheme(
    colors: AppCoverColors?,
    content: @Composable () -> Unit,
) {
    val current = MaterialTheme.colorScheme
    val currentSemantic = UnifiedReaderThemeTokens.colors
    val scheme = if (colors == null) {
        current
    } else {
        current.copy(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentContainer,
            onPrimaryContainer = colors.onAccentContainer,
        )
    }
    val semantic = if (colors == null) {
        currentSemantic
    } else {
        currentSemantic.copy(
            surfaceSelected = colors.accentContainer,
            onSurfaceSelected = colors.onAccentContainer,
            readerAccent = colors.accent,
            onReaderAccent = colors.onAccent,
            readerAccentContainer = colors.accentContainer,
            onReaderAccentContainer = colors.onAccentContainer,
        )
    }
    CompositionLocalProvider(LocalUnifiedReaderSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/** WCAG contrast between two opaque colours. */
fun contrastRatio(first: Int, second: Int): Double {
    val lighter = max(relativeLuminance(first), relativeLuminance(second))
    val darker = min(relativeLuminance(first), relativeLuminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(argb: Int): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((argb shr 16) and 0xFF) +
        0.7152 * channel((argb shr 8) and 0xFF) +
        0.0722 * channel(argb and 0xFF)
}

/** Hue (degrees), saturation and lightness (0-1). */
data class Hsl(val h: Float, val s: Float, val l: Float) {
    fun toArgb(): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        fun channel(value: Float) = ((value + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }

    companion object {
        fun of(argb: Int): Hsl {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val l = (maxC + minC) / 2f
            val d = maxC - minC
            if (d == 0f) return Hsl(0f, 0f, l)
            val s = d / (1f - abs(2f * l - 1f))
            val h = when (maxC) {
                r -> 60f * (((g - b) / d) % 6f)
                g -> 60f * ((b - r) / d + 2f)
                else -> 60f * ((r - g) / d + 4f)
            }
            return Hsl((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
        }
    }
}

private val DarkContent = Color(0xFFF6EEF1)
private val LightContent = Color(0xFF2A2233)
private const val WhiteArgb = 0xFFFFFFFF.toInt()
private const val MinTextContrast = 4.5
private const val TintMaxSaturation = 0.45f
private const val AccentMaxSaturation = 0.85f
private const val ContainerMaxSaturation = 0.4f
private const val LightContainerMaxSaturation = 0.5f
private const val DarkTintStartL = 0.25f
private const val DarkTintEndL = 0.30f
private const val LightTintStartL = 0.90f
private const val LightTintEndL = 0.88f
private const val DarkAccentL = 0.75f
private const val DarkOnAccentL = 0.14f
private const val LightAccentL = 0.35f
private const val DarkContainerL = 0.30f
private const val DarkOnContainerL = 0.92f
private const val LightContainerL = 0.80f
private const val LightOnContainerL = 0.20f
