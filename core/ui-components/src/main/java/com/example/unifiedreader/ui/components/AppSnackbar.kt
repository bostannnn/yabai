package com.example.unifiedreader.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * A transient notice at the bottom of the screen, on the control radius. Callers place it
 * (and keep it clear of the navigation bar); it does not need a `SnackbarHost`. An
 * [actionLabel] draws a text button in [actionContentColor] that calls [onAction].
 */
@Composable
fun AppSnackbar(
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = SnackbarDefaults.color,
    contentColor: Color = SnackbarDefaults.contentColor,
    actionContentColor: Color = SnackbarDefaults.actionContentColor,
    content: @Composable () -> Unit,
) {
    Snackbar(
        modifier = modifier,
        action = actionLabel?.let { label ->
            {
                AppTextButton(
                    onClick = onAction,
                    colors = AppButtonDefaults.textColors(contentColor = actionContentColor),
                ) {
                    Text(text = label)
                }
            }
        },
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content,
    )
}
