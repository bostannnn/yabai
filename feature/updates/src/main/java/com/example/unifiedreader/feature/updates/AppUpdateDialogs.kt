package com.example.unifiedreader.feature.updates

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.unifiedreader.ui.components.AppCircularProgress
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogCardDefaults
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.appProgressFill
import kotlin.math.roundToInt

/** The open updater dialog, if any. The app keeps it out of the readers. */
@Composable
fun AppUpdateDialogs(
    state: AppUpdateState,
    actions: AppUpdateActions,
) {
    when (state.dialog) {
        AppUpdateDialog.None -> Unit
        AppUpdateDialog.Update -> UpdateDialog(state = state, actions = actions)
        AppUpdateDialog.InstallPermission -> UpdateDialogCard(
            title = "Allow yabai to install updates",
            onDismissRequest = actions.onCloseDialog,
            dismiss = AppUpdateButton("Not now", AppUpdateAction.Close),
            confirm = AppUpdateButton("Open settings", AppUpdateAction.Install, filled = true),
            onAction = { action ->
                if (action == AppUpdateAction.Install) actions.onOpenInstallSettings() else actions.onCloseDialog()
            },
        ) {
            DialogBody(
                buildAnnotatedString {
                    append("Android asks once. On the next screen, turn on ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                        append("Allow from this source")
                    }
                    append(", then come back with Back.")
                },
            )
        }
        AppUpdateDialog.WhatsNew -> {
            val release = state.currentRelease ?: return
            UpdateDialogCard(
                title = "What's new in ${release.version}",
                onDismissRequest = actions.onCloseDialog,
                dismiss = AppUpdateButton("Close", AppUpdateAction.Close),
                confirm = null,
                onAction = { actions.onCloseDialog() },
            ) {
                ReleaseNotes(
                    release = release,
                    lines = release.notes.size,
                    onOpenReleasePage = actions.onOpenReleasePage,
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    state: AppUpdateState,
    actions: AppUpdateActions,
) {
    val onAction: (AppUpdateAction) -> Unit = { action -> actions.perform(action, state) }
    when (val status = state.status) {
        is AppUpdateStatus.Available -> UpdateDialogCard(
            title = "yabai ${status.release.version} is ready",
            subtitle = "You have ${state.currentVersion} · ${formatUpdateSize(status.release.apk.sizeBytes)}",
            // Tapping outside is Later: the offer waits for the next version, Settings keeps it.
            onDismissRequest = actions.onLater,
            dismiss = AppUpdateButton("Later", AppUpdateAction.Later),
            confirm = AppUpdateButton("Update", AppUpdateAction.Download, filled = true),
            onAction = onAction,
        ) {
            ReleaseNotes(
                release = status.release,
                lines = OFFER_NOTE_LINES,
                onOpenReleasePage = actions.onOpenReleasePage,
            )
        }
        is AppUpdateStatus.Downloading -> UpdateDialogCard(
            title = "Downloading ${status.release.version}",
            subtitle = "From GitHub · ${formatUpdateSize(status.totalBytes)}",
            onDismissRequest = actions.onCloseDialog,
            dismiss = AppUpdateButton("Cancel", AppUpdateAction.CancelDownload),
            secondDismiss = AppUpdateButton("Hide", AppUpdateAction.Close),
            confirm = null,
            onAction = onAction,
        ) {
            DownloadProgress(status)
        }
        is AppUpdateStatus.Downloaded -> UpdateDialogCard(
            title = "${status.release.version} is downloaded",
            subtitle = "Ready to install · ${formatUpdateSize(status.release.apk.sizeBytes)}",
            onDismissRequest = actions.onCloseDialog,
            dismiss = AppUpdateButton("Later", AppUpdateAction.Close),
            confirm = AppUpdateButton("Install", AppUpdateAction.Install, filled = true),
            onAction = onAction,
        ) {
            DialogBody("Android closes yabai while it installs. Your library, journal and reading positions stay.")
        }
        is AppUpdateStatus.Installing -> UpdateDialogCard(
            title = "Installing ${status.release.version}",
            onDismissRequest = actions.onCloseDialog,
            dismiss = AppUpdateButton("Close", AppUpdateAction.Close),
            confirm = null,
            onAction = onAction,
        ) {
            DialogBody("Android's installer takes it from here. yabai closes while the update goes in.")
        }
        is AppUpdateStatus.Failed -> {
            val copy = failureCopy(status.failure, state.currentVersion)
            UpdateDialogCard(
                title = copy.title,
                onDismissRequest = actions.onCloseDialog,
                dismiss = copy.dismiss,
                confirm = copy.confirm,
                onAction = onAction,
            ) {
                DialogBody(copy.body)
            }
        }
        AppUpdateStatus.Idle, AppUpdateStatus.Checking, AppUpdateStatus.UpToDate -> Unit
    }
}

@Composable
private fun UpdateDialogCard(
    title: String,
    onDismissRequest: () -> Unit,
    dismiss: AppUpdateButton,
    confirm: AppUpdateButton?,
    onAction: (AppUpdateAction) -> Unit,
    subtitle: String? = null,
    secondDismiss: AppUpdateButton? = null,
    content: @Composable () -> Unit,
) {
    AppDialogCard(
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitle,
        leading = { AppLauncherIcon() },
        actions = {
            DialogButton(dismiss, onAction)
            secondDismiss?.let { DialogButton(it, onAction) }
            confirm?.let { DialogButton(it, onAction) }
        },
    ) {
        content()
    }
}

@Composable
private fun RowScope.DialogButton(
    button: AppUpdateButton,
    onAction: (AppUpdateAction) -> Unit,
) {
    if (button.filled) {
        AppDialogConfirmButton(text = button.label, onClick = { onAction(button.action) })
    } else {
        AppDialogDismissButton(text = button.label, onClick = { onAction(button.action) })
    }
}

@Composable
private fun DialogBody(text: String) {
    DialogBody(buildAnnotatedString { append(text) })
}

@Composable
private fun DialogBody(text: AnnotatedString) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

/** The first [lines] notes as bullets, then a link to the rest on GitHub. */
@Composable
private fun ReleaseNotes(
    release: AppRelease,
    lines: Int,
    onOpenReleasePage: (String) -> Unit,
) {
    val shown = release.notes.take(lines)
    if (shown.isNotEmpty()) {
        Text(
            text = "What's new",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(4.dp)
                            .background(MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    val remaining = release.notes.size - shown.size
    val linkLabel = when {
        remaining > 0 -> "$remaining more on GitHub"
        shown.isEmpty() -> "Release notes on GitHub"
        else -> null
    }
    if (linkLabel != null && release.pageUrl.isNotBlank()) {
        Row(
            modifier = Modifier
                .padding(top = if (shown.isEmpty()) 16.dp else 10.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { onOpenReleasePage(release.pageUrl) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = linkLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = AppIcons.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DownloadProgress(status: AppUpdateStatus.Downloading) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        UpdateProgressBar(fraction = status.fraction, height = 6)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val metaStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${formatUpdateSize(status.downloadedBytes)} of ${formatUpdateSize(status.totalBytes)}",
                style = metaStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${(status.fraction * 100).roundToInt()}%",
                style = metaStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A pill progress bar in the accent on the highest container colour. */
@Composable
internal fun UpdateProgressBar(
    fraction: Float,
    height: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .appProgressFill(
                fraction = fraction,
                progressColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    )
}

/** The launcher icon, as Android draws it (with the device's icon shape). */
@Composable
internal fun AppLauncherIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { AppDialogCardDefaults.leadingSize.roundToPx() }
    val icon = remember(context, sizePx) {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName).toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull()
    }
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier.size(AppDialogCardDefaults.leadingSize),
        )
    }
}

/** A small spinner for busy pill buttons. */
@Composable
internal fun ButtonSpinner() {
    AppCircularProgress(
        modifier = Modifier.size(16.dp),
        color = MaterialTheme.colorScheme.onSurface,
        strokeWidth = 2.dp,
    )
}

/** Runs a dialog or row button. */
internal fun AppUpdateActions.perform(action: AppUpdateAction, state: AppUpdateState) {
    when (action) {
        AppUpdateAction.Check -> onCheck()
        AppUpdateAction.OpenUpdate -> onOpenUpdate()
        AppUpdateAction.Download -> onDownload()
        AppUpdateAction.CancelDownload -> onCancelDownload()
        AppUpdateAction.Install -> onInstall()
        AppUpdateAction.Retry -> onRetry()
        AppUpdateAction.OpenReleasePage -> {
            val pageUrl = state.newerRelease?.pageUrl ?: state.currentRelease?.pageUrl
            onOpenReleasePage(pageUrl?.takeIf { it.isNotBlank() } ?: RELEASES_PAGE_URL)
            onCloseDialog()
        }
        AppUpdateAction.Later -> onLater()
        AppUpdateAction.Close -> onCloseDialog()
    }
}

internal const val RELEASES_PAGE_URL = "https://github.com/${GitHubReleaseSource.DEFAULT_REPOSITORY}/releases"
