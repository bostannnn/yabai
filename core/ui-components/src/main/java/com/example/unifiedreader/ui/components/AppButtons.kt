package com.example.unifiedreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

// Every button shrinks a little while pressed. A button on the default pill shape also rounds
// toward a square; one given its own [Shape] keeps it.

/** Primary filled action. */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.appPressScale(interactionSource),
        enabled = enabled,
        shape = shape ?: rememberAppPressShape(interactionSource),
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Secondary action with an outline. */
@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.appPressScale(interactionSource),
        enabled = enabled,
        shape = shape ?: rememberAppPressShape(interactionSource),
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Low-emphasis text action, for dialogs and inline links. */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.appPressScale(interactionSource),
        enabled = enabled,
        shape = shape ?: rememberAppPressShape(interactionSource),
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Icon-only action without a container. */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.appPressScale(interactionSource),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Icon-only action on a tonal container. */
@Composable
fun AppTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.appPressScale(interactionSource),
        enabled = enabled,
        shape = shape ?: rememberAppPressShape(interactionSource),
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}
