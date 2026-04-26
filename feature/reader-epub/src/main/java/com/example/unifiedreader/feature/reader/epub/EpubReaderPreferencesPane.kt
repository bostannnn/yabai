package com.example.unifiedreader.feature.reader.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.ReaderAttachedPane
import com.example.unifiedreader.feature.reader.core.ReaderAttachedPaneAnchor

@Composable
internal fun EpubPreferencesSheet(
    isOpen: Boolean,
    maxSheetHeight: Dp,
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
    var selectedTab by rememberSaveable { mutableStateOf(EpubPreferencesTab.Text) }
    var isFontPickerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selectedTab, typographyMode) {
        if (selectedTab != EpubPreferencesTab.Text || typographyMode == EpubTypographyMode.Original) {
            isFontPickerOpen = false
        }
    }
    val sheetMaxHeight = (maxSheetHeight * 0.64f).coerceAtMost(EpubPreferencesSheetMaxHeight)
    ReaderAttachedPane(
        visible = isOpen,
        anchor = ReaderAttachedPaneAnchor.Top,
        maxPaneHeight = sheetMaxHeight,
        maxPaneWidth = EpubPreferencesSheetMaxWidth,
        horizontalAlignment = Alignment.End,
        horizontalPadding = 10.dp,
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
            selectedTab = selectedTab,
            isFontPickerOpen = isFontPickerOpen,
            maxSheetHeight = sheetMaxHeight,
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
        )
    }
}

private val EpubPreferencesSheetMaxHeight = 640.dp
private val EpubPreferencesSheetMaxWidth = 720.dp
