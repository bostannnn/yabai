package com.example.unifiedreader.feature.reader.epub

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.core.ReaderChromeDefaults
import com.example.unifiedreader.feature.reader.core.ReaderPanel
import com.example.unifiedreader.feature.reader.core.ReaderPanelSlot
import com.example.unifiedreader.ui.components.AppFloatingPanelPlacement

/** The reading settings as an end-edge panel between the bars (a bottom sheet on phones). */
@Composable
internal fun EpubPreferencesSheet(
    isOpen: Boolean,
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
    pageTurnStyle: PageTurnStyle,
    pageNumbersVisible: Boolean,
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
    onPageTurnStyleChanged: (PageTurnStyle) -> Unit,
    onPageNumbersVisibleChanged: (Boolean) -> Unit,
    slot: ReaderPanelSlot,
) {
    var selectedTab by rememberSaveable { mutableStateOf(EpubPreferencesTab.Text) }
    var isFontPickerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isOpen, typographyMode) {
        if (!isOpen || typographyMode == EpubTypographyMode.Original) {
            isFontPickerOpen = false
        }
    }
    ReaderPanel(
        visible = isOpen,
        placement = AppFloatingPanelPlacement.End,
        width = ReaderChromeDefaults.settingsWidth,
        slot = slot,
        onDismissRequest = onDismissRequest,
        testTag = EpubPreferencesSheetTag,
    ) {
        EpubPreferencesPaneContent(
            typographyMode = typographyMode,
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
            pageMargins = pageMargins,
            paragraphSpacing = paragraphSpacing,
            paragraphIndent = paragraphIndent,
            textAlign = textAlign,
            theme = theme,
            useAppThemeDefault = useAppThemeDefault,
            layoutMode = layoutMode,
            useOrientationLayoutDefault = useOrientationLayoutDefault,
            pageTurnStyle = pageTurnStyle,
            pageNumbersVisible = pageNumbersVisible,
            selectedTab = selectedTab,
            isFontPickerOpen = isFontPickerOpen,
            onDismissRequest = onDismissRequest,
            onSelectedTabChanged = { selectedTab = it },
            onFontPickerOpenChanged = { isFontPickerOpen = it },
            onTypographyModeChanged = onTypographyModeChanged,
            onFontFamilyChanged = onFontFamilyChanged,
            onFontSizeChanged = onFontSizeChanged,
            onLineHeightChanged = onLineHeightChanged,
            onPageMarginsChanged = onPageMarginsChanged,
            onParagraphSpacingChanged = onParagraphSpacingChanged,
            onParagraphIndentChanged = onParagraphIndentChanged,
            onTextAlignChanged = onTextAlignChanged,
            onThemeChanged = onThemeChanged,
            onUseAppThemeDefault = onUseAppThemeDefault,
            onLayoutModeChanged = onLayoutModeChanged,
            onUseOrientationLayoutDefault = onUseOrientationLayoutDefault,
            onPageTurnStyleChanged = onPageTurnStyleChanged,
            onPageNumbersVisibleChanged = onPageNumbersVisibleChanged,
        )
    }
    // Registered after the panel's own handler, so it runs first: back leaves the font page
    // before it closes the panel.
    BackHandler(enabled = isOpen && isFontPickerOpen) {
        isFontPickerOpen = false
    }
}
