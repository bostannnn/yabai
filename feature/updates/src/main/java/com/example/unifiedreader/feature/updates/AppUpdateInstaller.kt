package com.example.unifiedreader.feature.updates

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Hands a verified APK to Android's package installer. */
interface AppUpdateInstaller {
    /** Whether the user has let this app install packages ("Install unknown apps"). */
    fun canInstallPackages(): Boolean

    /** Android's per-app "Install unknown apps" screen for this app. */
    fun installPermissionSettingsIntent(): Intent

    /** Commits an install session; its outcome arrives on [AppUpdateInstallEvents]. */
    fun install(file: File)
}

/** What Android's installer reported for a committed session. */
sealed interface AppUpdateInstallEvent {
    /** Android needs the user to confirm; start [confirmation] from an activity. */
    data class NeedsConfirmation(val confirmation: Intent) : AppUpdateInstallEvent

    data object Installed : AppUpdateInstallEvent

    data object Cancelled : AppUpdateInstallEvent

    data class Failed(val message: String?) : AppUpdateInstallEvent
}

/**
 * The installer's results, delivered by [AppUpdateInstallReceiver]. A receiver declared in the
 * manifest is created by Android, so it reaches the controller through this process-wide flow.
 */
object AppUpdateInstallEvents {
    private val mutableEvents = MutableSharedFlow<AppUpdateInstallEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AppUpdateInstallEvent> = mutableEvents.asSharedFlow()

    internal fun emit(event: AppUpdateInstallEvent) {
        mutableEvents.tryEmit(event)
    }
}

class PackageInstallerAppUpdateInstaller(context: Context) : AppUpdateInstaller {
    private val appContext = context.applicationContext

    override fun canInstallPackages(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    override fun installPermissionSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))

    override fun install(file: File) {
        val packageInstaller = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            setSize(file.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Skips Android's prompt only once this app is the one that installed itself.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = packageInstaller.createSession(params)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite(SESSION_ENTRY_NAME, 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val statusIntent = Intent(appContext, AppUpdateInstallReceiver::class.java)
                    .setPackage(appContext.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    sessionId,
                    statusIntent,
                    // Mutable: the installer adds its status extras to it.
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pendingIntent.intentSender)
            }
        } catch (error: Throwable) {
            runCatching { packageInstaller.abandonSession(sessionId) }
            throw error
        }
    }

    private companion object {
        const val SESSION_ENTRY_NAME = "yabai.apk"
    }
}

/** Receives the install session's status and forwards it to [AppUpdateInstallEvents]. */
class AppUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val event = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let(AppUpdateInstallEvent::NeedsConfirmation)
                    ?: AppUpdateInstallEvent.Failed(null)
            PackageInstaller.STATUS_SUCCESS -> AppUpdateInstallEvent.Installed
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdateInstallEvent.Cancelled
            else -> AppUpdateInstallEvent.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
        AppUpdateInstallEvents.emit(event)
    }
}
