package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ReaderOverlayIconButton(
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = readerOverlayTokens()
    IconButton(
        onClick = onClick,
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
fun ReaderOverlayDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = readerOverlayTokens()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = tokens.shapes.controlBar,
        containerColor = tokens.colors.container,
    ) {
        content()
    }
}

@Composable
fun ReaderOverlayDropdownItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    DropdownMenuItem(
        text = {
            Text(
                text = if (selected) "• $label" else label,
                color = tokens.colors.primaryText,
            )
        },
        onClick = onClick,
    )
}

@Composable
fun ReaderOverlayBackButton(
    onClick: () -> Unit,
) {
    val tokens = readerOverlayTokens()
    ReaderOverlayIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to library",
            tint = tokens.colors.primaryText,
        )
    }
}

@Composable
fun ReaderOverlayMenuAction(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    menuModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        ReaderOverlayIconButton(
            isActive = expanded,
            onClick = { onExpandedChange(!expanded) },
        ) {
            icon()
        }
        ReaderOverlayDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = menuModifier,
        ) {
            content()
        }
    }
}

@Composable
fun <T> rememberReaderOverlayPanelState(
    key: Any?,
    overlayVisible: Boolean,
    hiddenPanel: T,
): MutableState<T> {
    val state = remember(key) { mutableStateOf(hiddenPanel) }
    LaunchedEffect(overlayVisible, hiddenPanel) {
        if (!overlayVisible) {
            state.value = hiddenPanel
        }
    }
    return state
}
