package com.example.unifiedreader.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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

/**
 * Secondary action on a quiet filled pill (`surfaceContainerHighest`): Cancel and Later in
 * dialogs, and row actions that sit next to a filled [AppButton].
 */
@Composable
fun AppTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = AppButtonDefaults.tonalColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    AppButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

object AppButtonDefaults {
    @Composable
    fun filledColors(): ButtonColors = ButtonDefaults.buttonColors()

    /** A pill in colours of its own, for buttons on a cover tint (the cover's accent, or its track). */
    @Composable
    fun filledColors(containerColor: Color, contentColor: Color): ButtonColors =
        ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)

    @Composable
    fun tonalColors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

    /** [tonalColors] on a cover tint: the tint's track under its text colour. */
    @Composable
    fun tonalColors(containerColor: Color, contentColor: Color): ButtonColors =
        ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)

    @Composable
    fun tonalIconColors(): IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

    /** [tonalIconColors] on a cover tint: a faint container of the tint's text colour. */
    @Composable
    fun tonalIconColors(containerColor: Color, contentColor: Color): IconButtonColors =
        IconButtonDefaults.filledTonalIconButtonColors(containerColor = containerColor, contentColor = contentColor)

    /** Remove, Delete: the error color, so the risky action reads as one. */
    @Composable
    fun destructiveColors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )

    @Composable
    fun textColors(): ButtonColors = ButtonDefaults.textButtonColors()

    /** An [AppTextButton] on a surface of its own (the selection bar, a snackbar). */
    @Composable
    fun textColors(contentColor: Color): ButtonColors = ButtonDefaults.textButtonColors(contentColor = contentColor)

    /** [destructiveColors] for an [AppTextButton]. */
    @Composable
    fun destructiveTextColors(): ButtonColors =
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
}

/** Low-emphasis text action, for dialogs and inline links. */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = AppButtonDefaults.textColors(),
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

/**
 * Icon-only action on a tonal container: the same neutral container as [AppTonalButton], not
 * Material's `secondaryContainer`, which no theme or cover accent keeps in step with the rest.
 */
@Composable
fun AppTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: IconButtonColors = AppButtonDefaults.tonalIconColors(),
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

/** The spinner a button shows while its action runs: 18 dp, in the button's own content colour. */
@Composable
fun AppButtonSpinner(modifier: Modifier = Modifier) {
    AppCircularProgress(
        modifier = modifier.size(18.dp),
        color = LocalContentColor.current,
        strokeWidth = 2.dp,
    )
}
