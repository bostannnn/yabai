package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class ReaderOverlayColors(
    val container: Color = Color(0xF2111418),
    val elevatedContainer: Color = Color(0xFA171A1F),
    val selectedContainer: Color = Color(0x29F5F7FA),
    val selectedBorder: Color = Color(0x24F5F7FA),
    val divider: Color = Color(0x33F5F7FA),
    val primaryText: Color = Color(0xFFF5F7FA),
    val secondaryText: Color = Color(0xFFD6E0E8),
    val tertiaryText: Color = Color(0xFFB7C4CE),
    val mutedText: Color = Color(0xFF8B9AAA),
    val track: Color = Color(0x55F5F7FA),
    val progress: Color = Color(0xFFE9EEF3),
    val accent: Color = Color(0xFF9DBBFF),
    val fallbackPage: Color = Color(0xFF05090C),
)

@Immutable
data class ReaderOverlayShapes(
    val button: RoundedCornerShape = RoundedCornerShape(8.dp),
    val controlBar: RoundedCornerShape = RoundedCornerShape(8.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(22.dp),
    val selectedRow: RoundedCornerShape = RoundedCornerShape(16.dp),
    val previewStrip: RoundedCornerShape = RoundedCornerShape(8.dp),
    val previewItem: RoundedCornerShape = RoundedCornerShape(4.dp),
)

@Immutable
data class ReaderOverlayTokens(
    val colors: ReaderOverlayColors = ReaderOverlayColors(),
    val shapes: ReaderOverlayShapes = ReaderOverlayShapes(),
)

@Composable
fun readerOverlayTokens(): ReaderOverlayTokens = ReaderOverlayTokens()
