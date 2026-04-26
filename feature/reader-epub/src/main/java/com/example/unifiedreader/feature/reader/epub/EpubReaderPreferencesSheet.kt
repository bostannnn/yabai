package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EpubPreferencesSheet(
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
    onDismissRequest: () -> Unit,
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
    var selectedTab by rememberSaveable { mutableStateOf(EpubPreferencesTab.Text) }
    var isFontPickerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedTab, typographyMode) {
        if (selectedTab != EpubPreferencesTab.Text || typographyMode == EpubTypographyMode.Original) {
            isFontPickerOpen = false
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = tokens.colors.container,
        contentColor = tokens.colors.primaryText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = EpubPreferencesSheetMaxHeight)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
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
                            isFontPickerOpen = false
                        },
                        onBack = { isFontPickerOpen = false },
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
                    onSelected = { selectedTab = it },
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
                                onFontPickerRequested = { isFontPickerOpen = true },
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

private val EpubPreferencesSheetMaxHeight = 640.dp

private enum class EpubPreferencesTab(
    override val label: String,
) : EpubLabeledOption {
    Text("Text"),
    Theme("Theme"),
    Layout("Layout"),
}
