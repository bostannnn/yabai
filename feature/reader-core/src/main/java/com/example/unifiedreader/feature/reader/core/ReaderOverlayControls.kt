package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons

@Composable
fun ReaderOverlayIconButton(
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tokens = readerOverlayTokens()
    AppIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .background(
                if (isActive) tokens.colors.selectedContainer else Color.Transparent,
                tokens.shapes.button,
            )
            .sizeIn(minWidth = 44.dp, minHeight = 36.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

@Composable
fun ReaderOverlayBackButton(
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    ReaderOverlayIconButton(onClick = onClick) {
        Icon(
            imageVector = AppIcons.ArrowBack,
            contentDescription = "Back to library",
            tint = tokens.colors.primaryText,
        )
    }
}
