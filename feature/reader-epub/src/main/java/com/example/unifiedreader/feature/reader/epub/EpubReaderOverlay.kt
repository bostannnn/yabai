package com.example.unifiedreader.feature.reader.epub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.reader.core.CompactReaderOverlayFrame
import com.example.unifiedreader.feature.reader.core.ReaderOverlayBackButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlayIconButton
import com.example.unifiedreader.feature.reader.core.readerOverlayTokens

@Composable
internal fun EpubReaderOverlay(
    itemId: String,
    title: String,
    progressLabel: String,
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
    overlayVisible: Boolean,
    onBack: () -> Unit,
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
    val tokens = readerOverlayTokens()
    var isPreferencesSheetOpen by rememberSaveable(itemId) { mutableStateOf(false) }
    LaunchedEffect(overlayVisible) {
        if (!overlayVisible) {
            isPreferencesSheetOpen = false
        }
    }
    CompactReaderOverlayFrame(
        title = title,
        progressLabel = progressLabel,
        modifier = modifier,
        leadingActions = {
            ReaderOverlayBackButton(onClick = onBack)
        },
        trailingActions = {
            ReaderOverlayIconButton(
                isActive = isPreferencesSheetOpen,
                onClick = {
                    isPreferencesSheetOpen = true
                },
            ) {
                Icon(
                    imageVector = Icons.Default.FormatSize,
                    contentDescription = "Reader preferences",
                    tint = tokens.colors.primaryText,
                )
            }
        },
    )
    if (isPreferencesSheetOpen) {
        EpubPreferencesSheet(
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
            onDismissRequest = { isPreferencesSheetOpen = false },
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
