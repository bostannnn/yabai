package com.example.unifiedreader.feature.reader.epub

import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens

@Composable
internal fun EpubTextSizeStepper(
    value: Double,
    onValueChanged: (Double) -> Unit,
) {
    val tokens = readerOverlayTokens()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Text size",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.secondaryText,
            )
            Text(
                text = formatEpubFontSizeValue(value),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.tertiaryText,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.toDouble()) },
            valueRange = 0.8f..2.4f,
            colors = epubSliderColors(),
        )
    }
}

@Composable
internal fun EpubSliderSection(
    label: String,
    valueLabel: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChanged: (Double) -> Unit,
) {
    val tokens = readerOverlayTokens()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.secondaryText,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.tertiaryText,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.toDouble()) },
            valueRange = valueRange,
            colors = epubSliderColors(),
        )
    }
}

@Composable
private fun epubSliderColors() = SliderDefaults.colors(
    thumbColor = readerOverlayTokens().colors.accent,
    activeTrackColor = readerOverlayTokens().colors.accent,
    inactiveTrackColor = readerOverlayTokens().colors.divider,
)

@Composable
internal fun EpubFontSection(
    selected: EpubReaderFontFamily,
    onSelected: (EpubReaderFontFamily) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EpubSectionLabel("Font")
        EpubSelectableRowGroup {
            EpubReaderFontFamily.entries.forEach { entry ->
                EpubSelectableRow(
                    label = entry.label,
                    selected = entry == selected,
                    onClick = { onSelected(entry) },
                )
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
                    imageVector = Icons.Default.Check,
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

@Composable
internal fun <T> EpubSegmentedSection(
    selected: T,
    entries: List<T>,
    onSelected: (T) -> Unit,
    label: String? = null,
) where T : Enum<T>, T : EpubLabeledOption {
    val tokens = readerOverlayTokens()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.secondaryText,
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = selected == entry,
                    onClick = { onSelected(entry) },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = tokens.colors.selectedContainer,
                        activeContentColor = tokens.colors.primaryText,
                        inactiveContainerColor = tokens.colors.elevatedContainer,
                        inactiveContentColor = tokens.colors.primaryText,
                    ),
                    border = BorderStroke(1.dp, tokens.colors.divider),
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = entries.size,
                    ),
                    label = { Text(text = entry.label) },
                )
            }
        }
    }
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
