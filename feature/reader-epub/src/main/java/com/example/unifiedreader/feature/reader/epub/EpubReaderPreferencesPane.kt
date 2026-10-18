package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppChoiceRow
import com.example.unifiedreader.ui.components.AppFloatingPanelDefaults
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSliderRow
import com.example.unifiedreader.ui.components.AppStepperRow

/**
 * The reading settings: Text, Theme and Layout. On a wide screen the panel is tall, so the
 * three are sections in one scrolling column; in the phone sheet they are tabs. The font
 * picker is a second page with back in the header.
 */
@Composable
internal fun ColumnScope.EpubPreferencesPaneContent(
    typographyMode: EpubTypographyMode,
    fontFamily: EpubReaderFontFamily,
    fontSize: Double,
    lineHeight: Double,
    pageMargins: Double,
    paragraphSpacing: Double,
    paragraphIndent: Double,
    textAlign: EpubReaderTextAlign,
    theme: EpubReaderTheme,
    useAppThemeDefault: Boolean,
    layoutMode: EpubReaderLayoutMode,
    useOrientationLayoutDefault: Boolean,
    selectedTab: EpubPreferencesTab,
    isFontPickerOpen: Boolean,
    onDismissRequest: () -> Unit,
    onSelectedTabChanged: (EpubPreferencesTab) -> Unit,
    onFontPickerOpenChanged: (Boolean) -> Unit,
    onTypographyModeChanged: (EpubTypographyMode) -> Unit,
    onFontFamilyChanged: (EpubReaderFontFamily) -> Unit,
    onFontSizeChanged: (Double) -> Unit,
    onLineHeightChanged: (Double) -> Unit,
    onPageMarginsChanged: (Double) -> Unit,
    onParagraphSpacingChanged: (Double) -> Unit,
    onParagraphIndentChanged: (Double) -> Unit,
    onTextAlignChanged: (EpubReaderTextAlign) -> Unit,
    onThemeChanged: (EpubReaderTheme) -> Unit,
    onUseAppThemeDefault: () -> Unit,
    onLayoutModeChanged: (EpubReaderLayoutMode) -> Unit,
    onUseOrientationLayoutDefault: () -> Unit,
) {
    val compact = LocalConfiguration.current.screenWidthDp < AppFloatingPanelDefaults.compactWidthDp
    if (isFontPickerOpen) {
        AppPanelHeader(
            title = "Font",
            onBack = { onFontPickerOpenChanged(false) },
            onClose = onDismissRequest,
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            EpubFontPicker(
                selected = fontFamily,
                onSelected = { selectedFontFamily ->
                    onFontFamilyChanged(selectedFontFamily)
                    onFontPickerOpenChanged(false)
                },
            )
        }
        return
    }

    AppPanelHeader(
        title = "Reading settings",
        onClose = onDismissRequest,
    )
    if (compact) {
        AppSegmentedRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
        ) {
            EpubPreferencesTab.entries.forEachIndexed { index, tab ->
                AppSegment(
                    selected = tab == selectedTab,
                    onClick = { onSelectedTabChanged(tab) },
                    index = index,
                    count = EpubPreferencesTab.entries.size,
                    icon = {},
                    label = { Text(text = tab.label) },
                )
            }
        }
    }
    key(if (compact) selectedTab else null) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            if (!compact || selectedTab == EpubPreferencesTab.Text) {
                if (!compact) EpubSettingsSectionLabel("Text")
                EpubTextSection(
                    typographyMode = typographyMode,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    textAlign = textAlign,
                    onTypographyModeChanged = onTypographyModeChanged,
                    onFontPickerRequested = { onFontPickerOpenChanged(true) },
                    onFontSizeChanged = onFontSizeChanged,
                    onLineHeightChanged = onLineHeightChanged,
                    onTextAlignChanged = onTextAlignChanged,
                )
            }
            if (!compact || selectedTab == EpubPreferencesTab.Theme) {
                if (!compact) EpubSettingsSectionLabel("Theme")
                EpubThemeSwatches(
                    theme = theme,
                    useAppThemeDefault = useAppThemeDefault,
                    onThemeChanged = onThemeChanged,
                    onUseAppThemeDefault = onUseAppThemeDefault,
                )
            }
            if (!compact || selectedTab == EpubPreferencesTab.Layout) {
                if (!compact) EpubSettingsSectionLabel("Layout")
                EpubLayoutSection(
                    typographyMode = typographyMode,
                    pageMargins = pageMargins,
                    paragraphSpacing = paragraphSpacing,
                    paragraphIndent = paragraphIndent,
                    layoutMode = layoutMode,
                    useOrientationLayoutDefault = useOrientationLayoutDefault,
                    onPageMarginsChanged = onPageMarginsChanged,
                    onParagraphSpacingChanged = onParagraphSpacingChanged,
                    onParagraphIndentChanged = onParagraphIndentChanged,
                    onLayoutModeChanged = onLayoutModeChanged,
                    onUseOrientationLayoutDefault = onUseOrientationLayoutDefault,
                )
            }
        }
    }
}

@Composable
private fun EpubTextSection(
    typographyMode: EpubTypographyMode,
    fontFamily: EpubReaderFontFamily,
    fontSize: Double,
    lineHeight: Double,
    textAlign: EpubReaderTextAlign,
    onTypographyModeChanged: (EpubTypographyMode) -> Unit,
    onFontPickerRequested: () -> Unit,
    onFontSizeChanged: (Double) -> Unit,
    onLineHeightChanged: (Double) -> Unit,
    onTextAlignChanged: (EpubReaderTextAlign) -> Unit,
) {
    AppChoiceRow(
        label = "Typography",
        options = EpubTypographyMode.entries,
        selected = typographyMode,
        onSelected = onTypographyModeChanged,
        optionLabel = ::epubTypographyModeLabel,
    )
    if (typographyMode == EpubTypographyMode.Original) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            EpubPublisherTypographySummary()
        }
        return
    }
    EpubFontRow(
        selected = fontFamily,
        onClick = onFontPickerRequested,
    )
    AppStepperRow(
        label = "Text size",
        valueLabel = formatEpubFontSizeValue(fontSize),
        onDecrement = { onFontSizeChanged(stepEpubFontSize(fontSize, -1)) },
        onIncrement = { onFontSizeChanged(stepEpubFontSize(fontSize, +1)) },
        canDecrement = canStepEpubFontSize(fontSize, -1),
        canIncrement = canStepEpubFontSize(fontSize, +1),
        decrementIcon = { Text(text = "A", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
        incrementIcon = { Text(text = "A", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
    )
    AppSliderRow(
        label = "Line height",
        value = lineHeight.toFloat(),
        onValueChange = { onLineHeightChanged(it.toDouble()) },
        valueLabel = formatEpubLineHeightValue(lineHeight),
        valueRange = 1.1f..2.2f,
    )
    AppChoiceRow(
        label = "Alignment",
        options = EpubReaderTextAlign.entries,
        selected = textAlign,
        onSelected = onTextAlignChanged,
        optionLabel = ::epubTextAlignLabel,
    )
}

@Composable
private fun EpubLayoutSection(
    typographyMode: EpubTypographyMode,
    pageMargins: Double,
    paragraphSpacing: Double,
    paragraphIndent: Double,
    layoutMode: EpubReaderLayoutMode,
    useOrientationLayoutDefault: Boolean,
    onPageMarginsChanged: (Double) -> Unit,
    onParagraphSpacingChanged: (Double) -> Unit,
    onParagraphIndentChanged: (Double) -> Unit,
    onLayoutModeChanged: (EpubReaderLayoutMode) -> Unit,
    onUseOrientationLayoutDefault: () -> Unit,
) {
    AppChoiceRow(
        label = "Pages",
        supportingText = "Auto: page in portrait, spread in landscape",
        options = EpubPagesChoice.entries,
        selected = epubPagesChoice(useOrientationLayoutDefault, layoutMode),
        onSelected = { choice ->
            val mode = choice.layoutMode
            if (mode == null) onUseOrientationLayoutDefault() else onLayoutModeChanged(mode)
        },
        optionLabel = { it.label },
        // Four segments do not fit beside a label; the control takes the full width below it.
        stacked = true,
    )
    if (typographyMode == EpubTypographyMode.Original) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            EpubPublisherSpacingSummary()
        }
        return
    }
    AppSliderRow(
        label = "Margins",
        value = pageMargins.toFloat(),
        onValueChange = { onPageMarginsChanged(it.toDouble()) },
        valueLabel = formatEpubMultiplierValue(pageMargins),
        valueRange = 0.5f..2.0f,
    )
    AppSliderRow(
        label = "Paragraph spacing",
        value = paragraphSpacing.toFloat(),
        onValueChange = { onParagraphSpacingChanged(it.toDouble()) },
        valueLabel = formatEpubRemValue(paragraphSpacing),
        valueRange = 0.0f..2.0f,
    )
    AppSliderRow(
        label = "Paragraph indent",
        value = paragraphIndent.toFloat(),
        onValueChange = { onParagraphIndentChanged(it.toDouble()) },
        valueLabel = formatEpubRemValue(paragraphIndent),
        valueRange = 0.0f..3.0f,
    )
}

/** "Font    Literata ›", with the value set in that typeface. */
@Composable
private fun EpubFontRow(
    selected: EpubReaderFontFamily,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Font",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tokens.colors.primaryText,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = selected.label,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = rememberEpubFontPreviewFamily(selected),
            color = tokens.colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = AppIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = tokens.colors.tertiaryText,
        )
    }
}

/** Follow app, Light, Dark as three page-colored tiles. */
@Composable
private fun EpubThemeSwatches(
    theme: EpubReaderTheme,
    useAppThemeDefault: Boolean,
    onThemeChanged: (EpubReaderTheme) -> Unit,
    onUseAppThemeDefault: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EpubThemeSwatch(
            label = "Follow app",
            page = colorScheme.background,
            ink = colorScheme.onBackground,
            selected = useAppThemeDefault,
            onClick = onUseAppThemeDefault,
            modifier = Modifier.weight(1f),
        )
        EpubReaderTheme.entries.forEach { entry ->
            EpubThemeSwatch(
                label = entry.label,
                page = epubThemeSwatchPage(entry),
                ink = epubThemeSwatchInk(entry),
                selected = !useAppThemeDefault && entry == theme,
                onClick = { onThemeChanged(entry) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EpubThemeSwatch(
    label: String,
    page: Color,
    ink: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = readerOverlayTokens()
    val edge = if (selected) tokens.colors.accent else MaterialTheme.colorScheme.outlineVariant
    AppPanel(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = tokens.colors.elevatedContainer,
        border = BorderStroke(if (selected) 2.dp else 1.dp, edge),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = page,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Aa",
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        color = ink,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = tokens.colors.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Preview colors for the fixed reader themes (the navigator applies its own).
private fun epubThemeSwatchPage(theme: EpubReaderTheme): Color = when (theme) {
    EpubReaderTheme.Light -> Color(0xFFFCFCFA)
    EpubReaderTheme.Dark -> Color(0xFF000000)
}

private fun epubThemeSwatchInk(theme: EpubReaderTheme): Color = when (theme) {
    EpubReaderTheme.Light -> Color(0xFF1C1B19)
    EpubReaderTheme.Dark -> Color(0xFFE6E2DA)
}

@Composable
private fun EpubSettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.sp,
        color = readerOverlayTokens().colors.secondaryText,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
    )
}

internal const val EpubPreferencesSheetTag = "epub-preferences-sheet"

internal enum class EpubPreferencesTab(
    override val label: String,
) : EpubLabeledOption {
    Text("Text"),
    Theme("Theme"),
    Layout("Layout"),
}
