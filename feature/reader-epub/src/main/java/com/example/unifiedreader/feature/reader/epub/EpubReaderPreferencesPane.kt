package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.feature.reader.core.ReaderPageNumberChoiceRow
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppChoiceRow
import com.example.unifiedreader.ui.components.AppFormRow
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSliderRow
import com.example.unifiedreader.ui.components.AppStepperRow
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.ReaderPageColor

/**
 * The reading settings: Text, Page and Layout. On a wide screen the panel is tall, so the
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
    theme: ReaderPageColor,
    layoutMode: EpubReaderLayoutMode,
    useOrientationLayoutDefault: Boolean,
    pageTurnStyle: PageTurnStyle,
    pageNumbersVisible: Boolean,
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
    onThemeChanged: (ReaderPageColor) -> Unit,
    onLayoutModeChanged: (EpubReaderLayoutMode) -> Unit,
    onUseOrientationLayoutDefault: () -> Unit,
    onPageTurnStyleChanged: (PageTurnStyle) -> Unit,
    onPageNumbersVisibleChanged: (Boolean) -> Unit,
) {
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
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
            EpubPreferencesTab.entries.forEach { tab ->
                AppSegment(
                    selected = tab == selectedTab,
                    onClick = { onSelectedTabChanged(tab) },
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
            if (!compact || selectedTab == EpubPreferencesTab.Page) {
                if (!compact) EpubSettingsSectionLabel("Page")
                AppFormRow(label = "Page colour", stacked = true) { controlModifier ->
                    EpubPageColorSwatches(
                        selected = theme,
                        // "Aa" in the font the book is read in; the book's own font is a serif.
                        fontFamily = rememberEpubFontPreviewFamily(fontFamily)
                            .takeIf { typographyMode == EpubTypographyMode.Custom } ?: FontFamily.Serif,
                        onSelected = onThemeChanged,
                        modifier = controlModifier,
                    )
                }
                ReaderPageNumberChoiceRow(
                    visible = pageNumbersVisible,
                    onVisibleChanged = onPageNumbersVisibleChanged,
                    label = "Page number and chapter",
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
                    pageTurnStyle = pageTurnStyle,
                    onPageMarginsChanged = onPageMarginsChanged,
                    onParagraphSpacingChanged = onParagraphSpacingChanged,
                    onParagraphIndentChanged = onParagraphIndentChanged,
                    onLayoutModeChanged = onLayoutModeChanged,
                    onUseOrientationLayoutDefault = onUseOrientationLayoutDefault,
                    onPageTurnStyleChanged = onPageTurnStyleChanged,
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
    )
    AppSliderRow(
        label = "Line height",
        value = lineHeight.toFloat(),
        onValueChange = { onLineHeightChanged(snapEpubLineHeight(it.toDouble())) },
        valueLabel = formatEpubLineHeight(lineHeight),
        valueRange = EpubLineHeightMin.toFloat()..EpubLineHeightMax.toFloat(),
        // Tenths: 1.1× to 2.2× is twelve stops, ten between the ends.
        steps = 10,
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
    pageTurnStyle: PageTurnStyle,
    onPageMarginsChanged: (Double) -> Unit,
    onParagraphSpacingChanged: (Double) -> Unit,
    onParagraphIndentChanged: (Double) -> Unit,
    onLayoutModeChanged: (EpubReaderLayoutMode) -> Unit,
    onUseOrientationLayoutDefault: () -> Unit,
    onPageTurnStyleChanged: (PageTurnStyle) -> Unit,
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
    AppChoiceRow(
        label = "Page turn",
        options = PageTurnStyle.entries,
        selected = pageTurnStyle,
        onSelected = onPageTurnStyleChanged,
        optionLabel = { it.label },
        enabled = layoutMode != EpubReaderLayoutMode.Scroll,
    )
    if (typographyMode == EpubTypographyMode.Original) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            EpubPublisherSpacingSummary()
        }
        return
    }
    EpubStepRow(label = "Margins", steps = EpubMarginSteps, value = pageMargins, onSelected = onPageMarginsChanged)
    EpubStepRow(label = "Paragraph spacing", steps = EpubParagraphSpacingSteps, value = paragraphSpacing, onSelected = onParagraphSpacingChanged)
    EpubStepRow(label = "Paragraph indent", steps = EpubParagraphIndentSteps, value = paragraphIndent, onSelected = onParagraphIndentChanged)
}

/** A spacing setting as named steps under its label; a value between steps shows as the nearest. */
@Composable
private fun EpubStepRow(
    label: String,
    steps: List<EpubStep>,
    value: Double,
    onSelected: (Double) -> Unit,
) {
    AppChoiceRow(
        label = label,
        options = steps,
        selected = steps.nearest(value),
        onSelected = { step -> onSelected(step.value) },
        optionLabel = { step -> step.label },
        stacked = true,
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

/** The page colours as tiles of the page with "Aa" in the reading [fontFamily], the name under each. */
@Composable
private fun EpubPageColorSwatches(
    selected: ReaderPageColor,
    fontFamily: FontFamily,
    onSelected: (ReaderPageColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReaderPageColor.entries.forEach { color ->
            EpubPageColorSwatch(
                color = color,
                fontFamily = fontFamily,
                selected = color == selected,
                onClick = { onSelected(color) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EpubPageColorSwatch(
    color: ReaderPageColor,
    fontFamily: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = readerOverlayTokens()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .then(
                    // The chosen one is ringed in the accent, 2 dp clear of the tile.
                    if (selected) {
                        Modifier
                            .border(2.dp, tokens.colors.accent, RoundedCornerShape(16.dp))
                            .padding(4.dp)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(color.colors.page)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Aa",
                fontFamily = fontFamily,
                fontSize = 20.sp,
                color = color.colors.text,
            )
        }
        Text(
            text = color.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) tokens.colors.primaryText else tokens.colors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EpubSettingsSectionLabel(text: String) {
    // Sentence case, as the spec has every section title.
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = readerOverlayTokens().colors.secondaryText,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
    )
}

internal const val EpubPreferencesSheetTag = "epub-preferences-sheet"

internal enum class EpubPreferencesTab(
    override val label: String,
) : EpubLabeledOption {
    Text("Text"),
    Page("Page"),
    Layout("Layout"),
}
