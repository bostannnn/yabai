package com.example.unifiedreader.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * A transient notice at the bottom of the screen, on the control radius. Callers place it
 * (and keep it clear of the navigation bar); it does not need a `SnackbarHost`.
 */
@Composable
fun AppSnackbar(
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = SnackbarDefaults.color,
    contentColor: Color = SnackbarDefaults.contentColor,
    content: @Composable () -> Unit,
) {
    Snackbar(
        modifier = modifier,
        action = action,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content,
    )
}
