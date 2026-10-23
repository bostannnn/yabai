package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun ThemeSettingsPage(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    contentTopInset: Dp,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val options = if (themeMode.isAvailableInSettings) ThemeMode.settingsEntries else ThemeMode.settingsEntries + themeMode
    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        // What the chosen theme is for, above the tiles rather than at the end of the page.
        Text(
            text = themeMode.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        themeTileGroups(options).forEach { (label, groupOptions) ->
            ThemeTileGroup(
                label = label,
                options = groupOptions,
                selected = themeMode,
                onSelected = onThemeModeSelected,
            )
        }
    }
}

/**
 * The theme page's groups, each with its heading: the theme that follows the device first (it is
 * dark on a device in dark mode, so it can't sit under Light), then Light and Dark.
 */
internal fun themeTileGroups(options: List<ThemeMode>): List<Pair<String, List<ThemeMode>>> = listOf(
    "Device" to options.filter(ThemeMode::followsDevice),
    "Light" to options.filterNot { option -> option.isDark || option.followsDevice },
    "Dark" to options.filter(ThemeMode::isDark),
).filter { (_, groupOptions) -> groupOptions.isNotEmpty() }

@Composable
private fun ThemeTileGroup(
    label: String,
    options: List<ThemeMode>,
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 4.dp)
                .semantics { heading() },
        )
        BoxWithConstraints {
            val columns = (maxWidth / ThemeTileMinWidth).toInt().coerceIn(2, 4)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { option ->
                            ThemeTile(
                                option = option,
                                selected = option == selected,
                                onClick = { onSelected(option) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(
    option: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    AppPanel(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) semanticColors.surfaceBase else semanticColors.surfaceRaised,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        },
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemePreview(
                swatches = option.previewSwatches,
                isDark = option.isDark,
                cornerRadius = presentation.controlCornerRadius,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (selected) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** A miniature of the library in the theme: three covers in its colours on its background. */
@Composable
private fun ThemePreview(
    swatches: ThemePreviewSwatches,
    isDark: Boolean,
    cornerRadius: Dp,
) {
    val surface = Color(swatches.surface)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ThemePreviewHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(surface),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ThemeCovers(
            card = (if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)).compositeOver(surface),
            accent = Color(swatches.reader),
            third = Color(swatches.source),
            frame = Color.White.copy(alpha = 0.55f),
            coverSize = DpSize(30.dp, 45.dp),
            gap = 6.dp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

/**
 * Three small covers drawn in a theme's colours, the same on the hub's Theme row and on the
 * theme tiles: a sun over two stripes, a glow, a framed title page.
 */
@Composable
internal fun ThemeCovers(
    card: Color,
    accent: Color,
    third: Color,
    frame: Color,
    coverSize: DpSize,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    val corner = RoundedCornerShape(coverSize.width / 10)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Canvas(modifier = Modifier.size(coverSize).clip(corner).background(card)) {
            drawCircle(color = accent, radius = size.width * 0.3f, center = Offset(size.width / 2f, size.height * 0.42f))
            repeat(2) { index ->
                val y = size.height * (0.66f + index * 0.1f)
                drawLine(accent, Offset(size.width * 0.2f, y), Offset(size.width * 0.8f, y), strokeWidth = size.width * 0.06f)
            }
        }
        Canvas(modifier = Modifier.size(coverSize).clip(corner).background(accent)) {
            val center = Offset(size.width * 0.5f, size.height * 0.7f)
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(third, third.copy(alpha = 0f)), center = center, radius = size.width * 0.8f),
                radius = size.width * 0.8f,
                center = center,
            )
        }
        Canvas(modifier = Modifier.size(coverSize).clip(corner).background(third)) {
            val inset = size.width * 0.11f
            drawRect(
                color = frame,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = size.width * 0.045f),
            )
        }
    }
}

private val ThemeTileMinWidth: Dp = 200.dp
private val ThemePreviewHeight: Dp = 96.dp
