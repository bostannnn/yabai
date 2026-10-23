package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A standard confirm/dismiss dialog with a title and body. */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** The surface a dialog body sits on, for custom dialogs built from [AppDialogHost] + [AppPanel]. */
object AppDialogDefaults {
    val shape: Shape
        @Composable get() = AlertDialogDefaults.shape

    val containerColor: Color
        @Composable get() = AlertDialogDefaults.containerColor
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
 * A dialog in the second-pass style (redesign step 8.2): up to 380 dp wide on
 * `surfaceContainerHigh`, 28 dp corners and 24 dp padding; an optional 52 dp [leading] image
 * before a bold 24 sp [title] and its [subtitle]; [content] below, scrolling when tall; then the
 * pill [actions] at the end, a filled [AppButton] for the main one and [AppTonalButton]s for
 * the rest. [AppDialog] moves onto this layout with the 8.2 kit pass.
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
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        AppPanel(
            modifier = modifier
                .padding(AppDialogCardDefaults.windowMargin)
                .widthIn(max = AppDialogCardDefaults.maxWidth)
                .fillMaxWidth(),
            shape = RoundedCornerShape(AppDialogCardDefaults.cornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(AppDialogCardDefaults.padding)) {
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 24.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Bold,
                            ),
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

object AppDialogCardDefaults {
    val maxWidth: Dp = 380.dp
    val cornerRadius: Dp = 28.dp
    val padding: Dp = 24.dp
    val leadingSize: Dp = 52.dp
    val buttonHeight: Dp = 42.dp

    /** Space kept between the dialog and the window edges. */
    val windowMargin: Dp = 24.dp
}
