package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens

@Composable
internal fun EpubPreferencesPaneContent(
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
    maxSheetHeight: Dp,
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
    val tokens = readerOverlayTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (isFontPickerOpen) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                EpubFontPicker(
                    selected = fontFamily,
                    onSelected = { selectedFontFamily ->
                        onFontFamilyChanged(selectedFontFamily)
                        onFontPickerOpenChanged(false)
                    },
                    onBack = { onFontPickerOpenChanged(false) },
                )
            }
        } else {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.primaryText,
            )
            EpubSegmentedSection(
                selected = selectedTab,
                entries = EpubPreferencesTab.entries,
                onSelected = onSelectedTabChanged,
            )
            key(selectedTab) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (selectedTab) {
                        EpubPreferencesTab.Text -> EpubTextPreferencesTab(
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

                        EpubPreferencesTab.Theme -> EpubThemePreferencesTab(
                            theme = theme,
                            useAppThemeDefault = useAppThemeDefault,
                            onThemeChanged = onThemeChanged,
                            onUseAppThemeDefault = onUseAppThemeDefault,
                        )

                        EpubPreferencesTab.Layout -> EpubLayoutPreferencesTab(
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
    }
}

@Composable
private fun EpubTextPreferencesTab(
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
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        EpubSegmentedSection(
            selected = typographyMode,
            entries = EpubTypographyMode.entries,
            onSelected = onTypographyModeChanged,
        )
        if (typographyMode == EpubTypographyMode.Original) {
            EpubPublisherTypographySummary()
        } else {
            EpubFontSummarySection(
                selected = fontFamily,
                onClick = onFontPickerRequested,
            )
            EpubTextSizeStepper(
                value = fontSize,
                onValueChanged = onFontSizeChanged,
            )
            EpubSliderSection(
                label = "Line height",
                valueLabel = formatEpubLineHeightValue(lineHeight),
                value = lineHeight,
                valueRange = 1.1f..2.2f,
                onValueChanged = onLineHeightChanged,
            )
            EpubSegmentedSection(
                label = "Alignment",
                selected = textAlign,
                entries = EpubReaderTextAlign.entries,
                onSelected = onTextAlignChanged,
            )
        }
    }
}

@Composable
private fun EpubThemePreferencesTab(
    theme: EpubReaderTheme,
    useAppThemeDefault: Boolean,
    onThemeChanged: (EpubReaderTheme) -> Unit,
    onUseAppThemeDefault: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EpubSectionLabel("Theme")
        EpubSelectableRowGroup {
            EpubSelectableRow(
                label = "Follow app",
                value = "Default",
                selected = useAppThemeDefault,
                onClick = onUseAppThemeDefault,
            )
            EpubReaderTheme.entries.forEach { entry ->
                EpubSelectableRow(
                    label = entry.label,
                    selected = !useAppThemeDefault && entry == theme,
                    onClick = { onThemeChanged(entry) },
                )
            }
        }
    }
}

@Composable
private fun EpubLayoutPreferencesTab(
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
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EpubSectionLabel("Page layout")
            EpubSelectableRowGroup {
                EpubSelectableRow(
                    label = "Auto",
                    supportingText = "Portrait page, landscape spread",
                    selected = useOrientationLayoutDefault,
                    onClick = onUseOrientationLayoutDefault,
                )
                EpubReaderLayoutMode.entries.forEach { entry ->
                    EpubSelectableRow(
                        label = entry.label,
                        selected = !useOrientationLayoutDefault && entry == layoutMode,
                        onClick = { onLayoutModeChanged(entry) },
                    )
                }
            }
        }
        if (typographyMode == EpubTypographyMode.Original) {
            EpubPublisherSpacingSummary()
        } else {
            EpubSliderSection(
                label = "Margins",
                valueLabel = formatEpubMultiplierValue(pageMargins),
                value = pageMargins,
                valueRange = 0.5f..2.0f,
                onValueChanged = onPageMarginsChanged,
            )
            EpubSliderSection(
                label = "Paragraph spacing",
                valueLabel = formatEpubRemValue(paragraphSpacing),
                value = paragraphSpacing,
                valueRange = 0.0f..2.0f,
                onValueChanged = onParagraphSpacingChanged,
            )
            EpubSliderSection(
                label = "Paragraph indent",
                valueLabel = formatEpubRemValue(paragraphIndent),
                value = paragraphIndent,
                valueRange = 0.0f..3.0f,
                onValueChanged = onParagraphIndentChanged,
            )
        }
    }
}

internal const val EpubPreferencesSheetTag = "epub-preferences-sheet"

internal enum class EpubPreferencesTab(
    override val label: String,
) : EpubLabeledOption {
    Text("Text"),
    Theme("Theme"),
    Layout("Layout"),
}
