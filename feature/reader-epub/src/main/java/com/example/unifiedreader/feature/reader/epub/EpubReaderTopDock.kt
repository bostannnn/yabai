package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EpubReaderTopDock(
    title: String,
    progressLabel: String,
    isPreferencesOpen: Boolean,
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
    onBack: () -> Unit,
    onPreferencesClick: () -> Unit,
    onDismissPreferences: () -> Unit,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeGestures.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            ),
    ) {
        EpubReaderOverlay(
            title = title,
            progressLabel = progressLabel,
            isPreferencesPaneOpen = isPreferencesOpen,
            onBack = onBack,
            onPreferencesClick = onPreferencesClick,
            applySafeGesturesPadding = false,
            bottomPadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isPreferencesOpen) {
            Spacer(modifier = Modifier.height(EpubTopDockPaneSeamHeight))
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            EpubPreferencesSheet(
                isOpen = isPreferencesOpen,
                maxSheetHeight = maxHeight,
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
                onDismissRequest = onDismissPreferences,
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
}

private val EpubTopDockPaneSeamHeight = 2.dp
