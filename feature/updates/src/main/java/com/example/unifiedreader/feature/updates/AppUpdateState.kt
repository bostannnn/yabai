package com.example.unifiedreader.feature.updates

import androidx.compose.runtime.Immutable

/** Why an update step failed. */
sealed interface AppUpdateFailure {
    /** A check the user asked for could not reach GitHub. */
    data object CheckFailed : AppUpdateFailure

    data class DownloadFailed(val downloadedBytes: Long, val totalBytes: Long) : AppUpdateFailure

    /** The file does not match GitHub's checksum or is not a readable APK. */
    data object Damaged : AppUpdateFailure

    /** Signed with another key than the installed app (a debug build, for one). */
    data object DifferentKey : AppUpdateFailure

    data object NotThisApp : AppUpdateFailure

    /** Not a higher version code than the installed app; Android can't go back. */
    data object NotNewer : AppUpdateFailure

    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : AppUpdateFailure

    /** Android's installer was closed before it finished. The download is kept. */
    data object InstallCancelled : AppUpdateFailure

    data class InstallFailed(val message: String?) : AppUpdateFailure
}

/** Where the update for this app stands. */
sealed interface AppUpdateStatus {
    /** Nothing new known; the last check (if any) found nothing, or none ran yet. */
    data object Idle : AppUpdateStatus

    data object Checking : AppUpdateStatus

    /** A check the user asked for found nothing newer. */
    data object UpToDate : AppUpdateStatus

    data class Available(val release: AppRelease) : AppUpdateStatus

    data class Downloading(
        val release: AppRelease,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : AppUpdateStatus {
        val fraction: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    /** Downloaded and checked; ready for Android's installer. */
    data class Downloaded(val release: AppRelease) : AppUpdateStatus

    /** Handed to Android's installer, waiting for its answer. */
    data class Installing(val release: AppRelease) : AppUpdateStatus

    data class Failed(val failure: AppUpdateFailure, val release: AppRelease?) : AppUpdateStatus
}

/** Which updater dialog is open. The app shows them outside the readers only. */
enum class AppUpdateDialog {
    None,

    /** The offer, then the download, install and failure steps for [AppUpdateState.status]. */
    Update,

    /** Explains Android's one-time "Install unknown apps" switch before opening it. */
    InstallPermission,

    /** The current version's release notes, from the "Updated to" notice. */
    WhatsNew,
}

@Immutable
data class AppUpdateState(
    val currentVersion: String = "",
    /** Debug builds are signed with another key and never check by themselves. */
    val isDebugBuild: Boolean = false,
    val status: AppUpdateStatus = AppUpdateStatus.Idle,
    val autoCheck: Boolean = true,
    val lastCheckAtMillis: Long? = null,
    val dialog: AppUpdateDialog = AppUpdateDialog.None,
    /** A found update waiting for the library to offer it (once per version and launch). */
    val pendingOffer: AppRelease? = null,
    /** The version this launch was updated to, shown once as "Updated to …". */
    val updatedToVersion: String? = null,
    /** The release notes of the running version, when the last check kept them. */
    val currentRelease: AppRelease? = null,
) {
    /** The newer release in play, whatever step it is at. */
    val newerRelease: AppRelease?
        get() = when (val status = status) {
            is AppUpdateStatus.Available -> status.release
            is AppUpdateStatus.Downloading -> status.release
            is AppUpdateStatus.Downloaded -> status.release
            is AppUpdateStatus.Installing -> status.release
            is AppUpdateStatus.Failed -> status.release
            AppUpdateStatus.Idle, AppUpdateStatus.Checking, AppUpdateStatus.UpToDate -> null
        }
}

/** What the settings rows, dialogs and notice can ask the updater to do. */
@Immutable
data class AppUpdateActions(
    val onCheck: () -> Unit = {},
    val onOpenUpdate: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onCancelDownload: () -> Unit = {},
    val onInstall: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onCloseDialog: () -> Unit = {},
    val onAutoCheckChanged: (Boolean) -> Unit = {},
    val onOpenReleasePage: (String) -> Unit = {},
    val onOpenInstallSettings: () -> Unit = {},
    val onShowWhatsNew: () -> Unit = {},
    val onDismissUpdatedNotice: () -> Unit = {},
)
