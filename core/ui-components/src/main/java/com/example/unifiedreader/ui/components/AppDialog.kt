package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A confirm/dismiss dialog in the [AppDialogCard] layout: an optional 52 dp [icon] (a cover
 * thumbnail) before the bold [title], the [text] below it, then the [dismissButton] and
 * [confirmButton] at the end. Pass [AppDialogDismissButton] and [AppDialogConfirmButton]; a
 * dialog with neither button closes by tapping outside it.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    AppDialogLayout(
        onDismissRequest = onDismissRequest,
        properties = properties,
        modifier = modifier,
        leading = icon,
        heading = title?.let { title ->
            {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    ProvideTextStyle(AppDialogCardDefaults.titleStyle, title)
                }
            }
        },
        content = text?.let { text ->
            {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(AppDialogCardDefaults.bodyStyle) {
                        Box(modifier = Modifier.padding(top = if (title != null || icon != null) 16.dp else 0.dp)) {
                            text()
                        }
                    }
                }
            }
        },
        actions = if (confirmButton == null && dismissButton == null) {
            null
        } else {
            {
                dismissButton?.invoke()
                confirmButton?.invoke()
            }
        },
    )
}

/** The main action of a dialog: a 42 dp filled pill, in the error colors when [destructive]. */
@Composable
fun AppDialogConfirmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    AppButton(
        onClick = onClick,
        modifier = modifier.height(AppDialogCardDefaults.buttonHeight),
        enabled = enabled,
        colors = if (destructive) AppButtonDefaults.destructiveColors() else AppButtonDefaults.filledColors(),
        contentPadding = AppDialogCardDefaults.buttonPadding,
    ) {
        Text(text = text, style = AppDialogCardDefaults.buttonTextStyle)
    }
}

/** Cancel, Later, Close: a 42 dp tonal pill. */
@Composable
fun AppDialogDismissButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppTonalButton(
        onClick = onClick,
        modifier = modifier.height(AppDialogCardDefaults.buttonHeight),
        enabled = enabled,
        contentPadding = AppDialogCardDefaults.buttonPadding,
    ) {
        Text(text = text, style = AppDialogCardDefaults.buttonTextStyle)
    }
}

/** The surface a dialog body sits on, for custom dialogs built from [AppDialogHost] + [AppPanel]. */
object AppDialogDefaults {
    val shape: Shape
        @Composable get() = RoundedCornerShape(AppDialogCardDefaults.cornerRadius)

    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
}

/**
 * A dialog window with custom content. Put an [AppPanel] inside it for the body, on
 * [AppDialogDefaults.containerColor]; the host itself draws nothing.
 */

@Composable
fun AppDialogHost(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
        content = content,
    )
}

/**
 * The app's dialog: up to 380 dp wide on `surfaceContainerHigh`, 28 dp corners and 24 dp
 * padding; an optional 52 dp [leading] image before a bold 24 sp [title] and its [subtitle];
 * [content] below, scrolling when tall; then the pill [actions] at the end, an
 * [AppDialogConfirmButton] for the main one and [AppDialogDismissButton]s for the rest.
 */
@Composable
fun AppDialogCard(
    onDismissRequest: () -> Unit,
    title: String,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppDialogLayout(
        onDismissRequest = onDismissRequest,
        properties = properties,
        modifier = modifier,
        leading = leading,
        heading = {
            Text(
                text = title,
                style = AppDialogCardDefaults.titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        content = content,
        actions = actions,
    )
}

@Composable
private fun AppDialogLayout(
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    modifier: Modifier,
    leading: (@Composable () -> Unit)?,
    heading: (@Composable ColumnScope.() -> Unit)?,
    content: (@Composable ColumnScope.() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        AppPanel(
            modifier = modifier
                .padding(AppDialogCardDefaults.windowMargin)
                .widthIn(max = AppDialogCardDefaults.maxWidth)
                .fillMaxWidth(),
            shape = AppDialogDefaults.shape,
            color = AppDialogDefaults.containerColor,
        ) {
            Column(modifier = Modifier.padding(AppDialogCardDefaults.padding)) {
                if (leading != null || heading != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (leading != null) {
                            Box(
                                modifier = Modifier.size(AppDialogCardDefaults.leadingSize),
                                contentAlignment = Alignment.Center,
                            ) {
                                leading()
                            }
                        }
                        if (heading != null) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                content = heading,
                            )
                        }
                    }
                }
                if (content != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        content = content,
                    )
                }
                if (actions != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }
    }
}

object AppDialogCardDefaults {
    val maxWidth: Dp = 380.dp
    val cornerRadius: Dp = 28.dp
    val padding: Dp = 24.dp
    val leadingSize: Dp = 52.dp
    val buttonHeight: Dp = 42.dp
    val buttonPadding: PaddingValues = PaddingValues(horizontal = 20.dp)

    /** Space kept between the dialog and the window edges. */
    val windowMargin: Dp = 24.dp

    val titleStyle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
        )

    val bodyStyle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)

    val buttonTextStyle: TextStyle
        @Composable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
}
