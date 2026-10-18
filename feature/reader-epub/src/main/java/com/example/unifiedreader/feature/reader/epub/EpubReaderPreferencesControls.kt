package com.example.unifiedreader.feature.reader.epub

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSlider
import java.util.Locale




@Composable
internal fun EpubFontPicker(
    selected: EpubReaderFontFamily,
    onSelected: (EpubReaderFontFamily) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        EpubReaderFontCategory.entries.forEach { category ->
            val entries = EpubReaderFontFamily.entries.filter { it.category == category }
            if (entries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EpubSectionLabel(category.sectionLabel)
                    EpubSelectableRowGroup {
                        entries.forEach { entry ->
                            EpubSelectableRow(
                                label = entry.label,
                                selected = entry == selected,
                                onClick = { onSelected(entry) },
                                labelFontFamily = rememberEpubFontPreviewFamily(entry),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberEpubFontPreviewFamily(
    entry: EpubReaderFontFamily,
): FontFamily? {
    val context = LocalContext.current
    return remember(context, entry) {
        when (entry) {
            EpubReaderFontFamily.Serif -> FontFamily.Serif
            EpubReaderFontFamily.Sans -> FontFamily.SansSerif
            EpubReaderFontFamily.Monospace -> FontFamily.Monospace
            else -> entry.previewAssetPath
                ?.let { path ->
                    runCatching {
                        FontFamily(Typeface.createFromAsset(context.assets, path))
                    }.getOrNull()
                }
        }
    }
}

@Composable
internal fun EpubPublisherTypographySummary() {
    EpubSelectableRowGroup {
        EpubSummaryRow(label = "Font", value = "From book")
        EpubSummaryRow(label = "Text size", value = "From book")
        EpubSummaryRow(label = "Line height", value = "From book")
        EpubSummaryRow(label = "Alignment", value = "From book")
    }
}

@Composable
internal fun EpubPublisherSpacingSummary() {
    EpubSelectableRowGroup {
        EpubSummaryRow(label = "Margins", value = "From book")
        EpubSummaryRow(label = "Paragraph spacing", value = "From book")
        EpubSummaryRow(label = "Paragraph indent", value = "From book")
    }
}

@Composable
internal fun EpubSelectableRowGroup(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = readerOverlayTokens().colors.elevatedContainer,
                shape = readerOverlayTokens().shapes.controlBar,
            ),
    ) {
        content()
    }
}

@Composable
internal fun EpubSelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    value: String? = null,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    labelFontFamily: FontFamily? = null,
) {
    val tokens = readerOverlayTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) tokens.colors.selectedContainer else Color.Transparent,
                shape = tokens.shapes.controlBar,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = tokens.colors.primaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.primaryText,
                fontFamily = labelFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.tertiaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.tertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}

@Composable
private fun EpubSummaryRow(
    label: String,
    value: String,
) {
    val tokens = readerOverlayTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.primaryText,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.tertiaryText,
        )
    }
}

@Composable
internal fun EpubSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = readerOverlayTokens().colors.secondaryText,
    )
}


internal fun formatEpubFontSizeValue(
    value: Double,
): String = String.format(Locale.getDefault(), "%.0f%%", value * 100.0)

internal fun formatEpubLineHeightValue(
    value: Double,
): String = String.format(Locale.getDefault(), "%.2f", value)

internal fun formatEpubMultiplierValue(
    value: Double,
): String = String.format(Locale.getDefault(), "%.2fx", value)

internal fun formatEpubRemValue(
    value: Double,
): String = String.format(Locale.getDefault(), "%.1f rem", value)
