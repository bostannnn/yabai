package com.example.unifiedreader.feature.updates

import kotlin.math.roundToLong

/** What a row or dialog button does. */
internal enum class AppUpdateAction {
    Check,
    OpenUpdate,
    Download,
    CancelDownload,
    Install,
    Retry,
    OpenReleasePage,
    Later,
    Close,
}

internal enum class AppUpdateTone { Normal, Accent, Error }

internal data class AppUpdateButton(
    val label: String,
    val action: AppUpdateAction,
    val filled: Boolean = false,
    val busy: Boolean = false,
)

/** The Version row in Settings › About for one updater state. */
internal data class VersionRowCopy(
    val supporting: String,
    val tone: AppUpdateTone = AppUpdateTone.Normal,
    val showsCheck: Boolean = false,
    val progress: Float? = null,
    val button: AppUpdateButton?,
)

internal fun versionRowCopy(
    versionName: String,
    state: AppUpdateState,
    lastCheckedLabel: String?,
): VersionRowCopy = when (val status = state.status) {
    AppUpdateStatus.Idle -> VersionRowCopy(
        supporting = listOfNotNull(versionName, lastCheckedLabel?.let { "checked $it" }).joinToString(" · "),
        button = AppUpdateButton("Check for updates", AppUpdateAction.Check),
    )
    AppUpdateStatus.Checking -> VersionRowCopy(
        supporting = "$versionName · asking GitHub…",
        button = AppUpdateButton("Checking", AppUpdateAction.Check, busy = true),
    )
    AppUpdateStatus.UpToDate -> VersionRowCopy(
        supporting = "$versionName is the latest version",
        showsCheck = true,
        button = AppUpdateButton("Check for updates", AppUpdateAction.Check),
    )
    is AppUpdateStatus.Available -> VersionRowCopy(
        supporting = "${status.release.version} is available · ${formatUpdateSize(status.release.apk.sizeBytes)}",
        tone = AppUpdateTone.Accent,
        button = AppUpdateButton("Update", AppUpdateAction.OpenUpdate, filled = true),
    )
    is AppUpdateStatus.Downloading -> VersionRowCopy(
        supporting = "Downloading ${status.release.version} · " +
            "${formatUpdateSize(status.downloadedBytes)} of ${formatUpdateSize(status.totalBytes)}",
        progress = status.fraction,
        button = AppUpdateButton("Cancel", AppUpdateAction.CancelDownload),
    )
    is AppUpdateStatus.Downloaded -> VersionRowCopy(
        supporting = "${status.release.version} is downloaded",
        tone = AppUpdateTone.Accent,
        button = AppUpdateButton("Install", AppUpdateAction.Install, filled = true),
    )
    is AppUpdateStatus.Installing -> VersionRowCopy(
        supporting = "Installing ${status.release.version} with Android's installer",
        tone = AppUpdateTone.Accent,
        button = AppUpdateButton("Install", AppUpdateAction.Install),
    )
    is AppUpdateStatus.Failed -> when (status.failure) {
        AppUpdateFailure.CheckFailed -> VersionRowCopy(
            supporting = "Couldn't reach GitHub. Check the connection.",
            tone = AppUpdateTone.Error,
            button = AppUpdateButton("Try again", AppUpdateAction.Retry),
        )
        else -> {
            val copy = failureCopy(status.failure, versionName)
            VersionRowCopy(
                supporting = copy.title,
                tone = AppUpdateTone.Error,
                button = copy.confirm,
            )
        }
    }
}

/** A dialog's words and buttons. [dismiss] is the tonal one, [confirm] the filled one. */
internal data class DialogCopy(
    val title: String,
    val body: String,
    val dismiss: AppUpdateButton,
    val confirm: AppUpdateButton?,
)

internal fun failureCopy(failure: AppUpdateFailure, installedVersion: String): DialogCopy {
    val close = AppUpdateButton("Close", AppUpdateAction.Close)
    val tryAgain = AppUpdateButton("Try again", AppUpdateAction.Retry, filled = true)
    val openOnGitHub = AppUpdateButton("Open on GitHub", AppUpdateAction.OpenReleasePage, filled = true)
    return when (failure) {
        AppUpdateFailure.CheckFailed -> DialogCopy(
            title = "Couldn't reach GitHub",
            body = "Check the connection and try again.",
            dismiss = close,
            confirm = tryAgain,
        )
        is AppUpdateFailure.DownloadFailed -> DialogCopy(
            title = "Couldn't download the update",
            body = if (failure.downloadedBytes > 0) {
                "The connection to GitHub dropped at ${formatUpdateSize(failure.downloadedBytes)} of " +
                    "${formatUpdateSize(failure.totalBytes)}. Check the connection and try again."
            } else {
                "The download from GitHub didn't start. Check the connection and try again."
            },
            dismiss = close,
            confirm = tryAgain,
        )
        AppUpdateFailure.Damaged -> DialogCopy(
            title = "The download was damaged",
            body = "The file doesn't match the checksum GitHub published for it, so yabai deleted it.",
            dismiss = close,
            confirm = tryAgain,
        )
        AppUpdateFailure.DifferentKey -> DialogCopy(
            title = "This copy can't update itself",
            body = "The update is signed with the release key and this copy of yabai $installedVersion isn't " +
                "(a debug build, for one). Android would refuse it, so nothing was installed.",
            dismiss = close,
            confirm = openOnGitHub,
        )
        AppUpdateFailure.NotThisApp -> DialogCopy(
            title = "This release isn't yabai",
            body = "The file attached to the release is a different app, so yabai deleted it.",
            dismiss = close,
            confirm = openOnGitHub,
        )
        AppUpdateFailure.NotNewer -> DialogCopy(
            title = "Not a newer version",
            body = "The file attached to the release isn't newer than the installed app, and Android " +
                "can't go back a version. Nothing was installed.",
            dismiss = close,
            confirm = openOnGitHub,
        )
        is AppUpdateFailure.NotEnoughSpace -> DialogCopy(
            title = "Not enough space",
            body = "The update needs ${formatUpdateSize(failure.neededBytes)} free while it installs, and " +
                "this device has ${formatUpdateSize(failure.freeBytes)}. Free some space and try again.",
            dismiss = close,
            confirm = tryAgain,
        )
        AppUpdateFailure.InstallCancelled -> DialogCopy(
            title = "Update not installed",
            body = "Android's installer was closed before it finished. The download is kept, so " +
                "installing again is quick.",
            dismiss = AppUpdateButton("Later", AppUpdateAction.Close),
            confirm = AppUpdateButton("Install", AppUpdateAction.Retry, filled = true),
        )
        is AppUpdateFailure.InstallFailed -> DialogCopy(
            title = "Update not installed",
            body = buildString {
                append("Android's installer stopped")
                failure.message?.takeIf { it.isNotBlank() }?.let { append(" (").append(it.trim()).append(')') }
                append(". Try again, or install it from the release page on GitHub.")
            },
            dismiss = close,
            confirm = tryAgain,
        )
    }
}

/** Sizes the way Android's own settings show them: decimal megabytes, "61 MB" or "9.4 MB". */
internal fun formatUpdateSize(bytes: Long): String {
    val megabytes = bytes / 1_000_000.0
    return when {
        bytes < 0 -> "0 MB"
        megabytes >= 10 -> "${megabytes.roundToLong()} MB"
        megabytes >= 1 -> "${(megabytes * 10).roundToLong() / 10.0} MB"
        else -> "${(bytes / 1_000.0).roundToLong()} KB"
    }
}

/** The release notes the offer shows before "N more on GitHub". */
internal const val OFFER_NOTE_LINES = 4
