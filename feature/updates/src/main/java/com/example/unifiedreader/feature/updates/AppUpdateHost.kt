package com.example.unifiedreader.feature.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.unifiedreader.ui.components.AppSnackbar
import com.example.unifiedreader.ui.components.AppTextButton
import kotlinx.coroutines.delay

/** The updater's actions, bound to [controller] and to the activity for the screens it opens. */
@Composable
fun rememberAppUpdateActions(controller: AppUpdateController): AppUpdateActions {
    val context = LocalContext.current
    return remember(controller, context) {
        AppUpdateActions(
            onCheck = controller::checkNow,
            onOpenUpdate = controller::openUpdate,
            onDownload = controller::startDownload,
            onCancelDownload = controller::cancelDownload,
            onInstall = controller::install,
            onRetry = controller::retry,
            onLater = controller::dismissOffer,
            onCloseDialog = controller::closeDialog,
            onAutoCheckChanged = controller::setAutoCheck,
            onOpenReleasePage = { url -> context.startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            onOpenInstallSettings = {
                if (context.startActivitySafely(controller.installPermissionSettingsIntent())) {
                    controller.onInstallPermissionSettingsOpened()
                } else {
                    controller.closeDialog()
                }
            },
            onShowWhatsNew = controller::showWhatsNew,
            onDismissUpdatedNotice = controller::dismissUpdatedNotice,
        )
    }
}

/**
 * Runs the updater for the app: starts it, checks once the library has been on screen for a
 * moment, offers a found update there, starts Android's install screens, and shows the
 * dialogs and the "Updated to" notice everywhere except in a reader.
 */
@Composable
fun BoxScope.AppUpdateHost(
    controller: AppUpdateController,
    state: AppUpdateState,
    actions: AppUpdateActions,
    isReaderOpen: Boolean,
    isLibraryVisible: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(controller) {
        controller.start()
    }
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(controller, context) {
        controller.installConfirmations.collect { confirmation -> context.startActivitySafely(confirmation) }
    }
    LaunchedEffect(isLibraryVisible) {
        if (isLibraryVisible) {
            // After the library has presented, so the request never competes with cold start.
            delay(LIBRARY_SETTLE_MILLIS)
            controller.onLibraryShown()
        }
    }
    LaunchedEffect(state.pendingOffer, state.dialog, isLibraryVisible) {
        if (state.pendingOffer != null && state.dialog == AppUpdateDialog.None && isLibraryVisible) {
            controller.presentOffer()
        }
    }
    if (isReaderOpen) return

    AppUpdateDialogs(state = state, actions = actions)

    val updatedTo = state.updatedToVersion ?: return
    LaunchedEffect(updatedTo) {
        delay(UPDATED_NOTICE_MILLIS)
        actions.onDismissUpdatedNotice()
    }
    AppSnackbar(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp)
            .widthIn(max = 560.dp),
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        action = if (state.currentRelease != null) {
            {
                AppTextButton(
                    onClick = actions.onShowWhatsNew,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary),
                ) {
                    Text(text = "What's new")
                }
            }
        } else {
            null
        },
    ) {
        Text(text = "Updated to $updatedTo")
    }
}

private fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

private const val LIBRARY_SETTLE_MILLIS = 2_000L
private const val UPDATED_NOTICE_MILLIS = 6_000L
