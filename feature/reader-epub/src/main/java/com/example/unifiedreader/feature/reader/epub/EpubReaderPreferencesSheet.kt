package com.example.unifiedreader.feature.reader.epub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens
import com.example.unifiedreader.ui.components.AppFloatingPanel
import com.example.unifiedreader.ui.components.AppFloatingPanelPlacement

/** The reading settings as an end-edge panel (a bottom sheet on phones), a sibling of the reader's bars. */
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
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(EpubPreferencesTab.Text) }
    var isFontPickerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isOpen, typographyMode) {
        if (!isOpen || typographyMode == EpubTypographyMode.Original) {
            isFontPickerOpen = false
        }
    }
    AppFloatingPanel(
        visible = isOpen,
        placement = AppFloatingPanelPlacement.End,
        onDismissRequest = onDismissRequest,
        containerColor = readerOverlayTokens().colors.container,
        testTag = EpubPreferencesSheetTag,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeGestures),
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
