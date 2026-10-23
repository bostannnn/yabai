package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
        themeTileGroups(options).forEach { (label, groupOptions) ->
            ThemeTileGroup(
                label = label,
                options = groupOptions,
                selected = themeMode,
                onSelected = onThemeModeSelected,
            )
        }
        Text(
            text = themeMode.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
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
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
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

/** A miniature of the theme: its surface, a top-bar strip, two covers and three accent dots. */
@Composable
private fun ThemePreview(
    swatches: ThemePreviewSwatches,
    isDark: Boolean,
    cornerRadius: Dp,
) {
    val surface = Color(swatches.surface)
    val barColor = (if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
        .compositeOver(surface)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ThemePreviewHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(surface),
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(barColor),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(swatches.reader, swatches.sync).forEach { color ->
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 38.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(color)),
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(swatches.reader, swatches.sync, swatches.source).forEach { color ->
                SwatchDot(color = Color(color))
            }
        }
    }
}

/** The four palette dots shown next to the theme's name on the hub. */
@Composable
internal fun ThemeSwatchDots(swatches: ThemePreviewSwatches) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(swatches.surface, swatches.reader, swatches.sync, swatches.source).forEach { color ->
            SwatchDot(color = Color(color))
        }
    }
}

@Composable
private fun SwatchDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape),
    )
}

private val ThemeTileMinWidth: Dp = 200.dp
private val ThemePreviewHeight: Dp = 96.dp
