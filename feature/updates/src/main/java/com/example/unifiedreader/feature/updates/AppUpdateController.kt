package com.example.unifiedreader.feature.updates

import android.content.Intent
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's own updater: checks the latest GitHub release, offers it, downloads and checks the
 * APK, and hands it to Android's installer.
 *
 * Network work runs only from [onLibraryShown] (at most once per [AUTO_CHECK_INTERVAL_MILLIS]
 * after a success, [FAILED_CHECK_RETRY_MILLIS] after a failure, never on debug builds or the
 * first launch, and not when the user turned it off) or from a button. One check and one
 * download at a time; both stop with [scope].
 */
class AppUpdateController(
    private val releaseSource: AppReleaseSource,
    private val downloader: AppUpdateDownloader,
    private val packageInspector: AppPackageInspector,
    private val installer: AppUpdateInstaller,
    private val preferencesStore: AppUpdatePreferencesStore,
    private val downloadDirectory: () -> File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val freeSpaceBytes: (File) -> Long = File::getUsableSpace,
    private val installEvents: Flow<AppUpdateInstallEvent> = AppUpdateInstallEvents.events,
) {
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    private val mutableInstallConfirmations = MutableSharedFlow<Intent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Android's confirmation screens, to start from the activity. */
    val installConfirmations: SharedFlow<Intent> = mutableInstallConfirmations.asSharedFlow()

    private val ready = CompletableDeferred<Unit>()
    private var started = false
    private var installedApp: InstalledAppInfo? = null
    private var preferences = AppUpdatePreferences()
    private var isFirstRun = false
    private var offeredThisLaunch = false
    private var awaitingInstallPermission = false
    private var downloadedFile: File? = null
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /** Reads what the updater remembers and clears old downloads. Safe to call again. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val installed = withContext(ioDispatcher) { packageInspector.installedApp() }
            val stored = withContext(ioDispatcher) {
                runCatching { downloadDirectory().deleteRecursively() }
                preferencesStore.read()
            }
            installedApp = installed
            preferences = stored
            isFirstRun = installed.isFreshInstall && stored.lastRunVersion == null
            val current = AppVersion.parse(installed.versionName)
            val previous = AppVersion.parse(stored.lastRunVersion)
            if (stored.lastRunVersion != installed.versionName) {
                persist { it.copy(lastRunVersion = installed.versionName) }
            }
            val cached = stored.latestRelease
            val newer = cached?.takeIf { current != null && it.version > current }
            mutableState.update { state ->
                state.copy(
                    currentVersion = installed.versionName,
                    isDebugBuild = installed.isDebuggable,
                    autoCheck = stored.autoCheck,
                    lastCheckAtMillis = stored.lastCheckAtMillis,
                    status = newer?.let(AppUpdateStatus::Available) ?: AppUpdateStatus.Idle,
                    updatedToVersion = installed.versionName.takeIf {
                        current != null && previous != null && current > previous
                    },
                    currentRelease = cached?.takeIf { current != null && it.version == current },
                )
            }
            ready.complete(Unit)
        }
        scope.launch {
            installEvents.collect(::onInstallEvent)
        }
    }

    /**
     * The library is on screen: check if one is due, or offer what an earlier check found. Both
     * happen once per launch at most.
     */
    fun onLibraryShown() {
        scope.launch {
            ready.await()
            if (isAutoCheckDue()) {
                check(manual = false)
            } else {
                queueOffer(mutableState.value.newerRelease)
            }
        }
    }

    /** Shows the queued offer; the app calls this while the library is visible. */
    fun presentOffer() {
        mutableState.update { state ->
            if (state.pendingOffer == null || state.dialog != AppUpdateDialog.None) return@update state
            state.copy(pendingOffer = null, dialog = AppUpdateDialog.Update)
        }
    }

    fun checkNow() {
        scope.launch {
            ready.await()
            check(manual = true)
        }
    }

    /** Opens the update dialog for the release in play (from Settings' Update button). */
    fun openUpdate() {
        if (mutableState.value.newerRelease == null) return
        mutableState.update { it.copy(dialog = AppUpdateDialog.Update, pendingOffer = null) }
    }

    /** "Later" on the offer: no more offers for this version; Settings still shows it. */
    fun dismissOffer() {
        val release = mutableState.value.newerRelease
        mutableState.update { it.copy(dialog = AppUpdateDialog.None, pendingOffer = null) }
        if (release != null) {
            persist { it.copy(dismissedVersion = release.version.toString()) }
        }
    }

    /** Closes the dialog; a download keeps going. */
    fun closeDialog() {
        mutableState.update { it.copy(dialog = AppUpdateDialog.None) }
    }

    fun startDownload() {
        val release = mutableState.value.newerRelease ?: return
        if (downloadJob?.isActive == true) return
        val installed = installedApp ?: return
        mutableState.update { it.copy(dialog = AppUpdateDialog.Update) }
        downloadJob = scope.launch {
            val directory = withContext(ioDispatcher) { downloadDirectory().apply { mkdirs() } }
            val neededBytes = release.apk.sizeBytes * INSTALL_SPACE_FACTOR
            val freeBytes = withContext(ioDispatcher) { freeSpaceBytes(directory) }
            if (freeBytes < neededBytes) {
                fail(AppUpdateFailure.NotEnoughSpace(neededBytes, freeBytes), release)
                return@launch
            }
            val file = File(directory, release.apk.fileName())
            var lastReported = 0L
            setStatus(AppUpdateStatus.Downloading(release, 0, release.apk.sizeBytes))
            try {
                val sha256 = withContext(ioDispatcher) {
                    downloader.download(release.apk, file) { downloaded, total ->
                        // About one state update per percent.
                        if (downloaded == total || downloaded - lastReported >= maxOf(total / 100, 1)) {
                            lastReported = downloaded
                            // Only while still downloading: a read finishing after Cancel must
                            // not bring the progress back.
                            mutableState.update { state ->
                                if (state.status is AppUpdateStatus.Downloading) {
                                    state.copy(status = AppUpdateStatus.Downloading(release, downloaded, total))
                                } else {
                                    state
                                }
                            }
                        }
                    }
                }
                val refusal = withContext(ioDispatcher) {
                    refuseApk(
                        installed = installed,
                        apk = packageInspector.inspectApk(file),
                        expectedSha256 = release.apk.sha256,
                        actualSha256 = sha256,
                    )
                }
                if (refusal != null) {
                    withContext(ioDispatcher) { file.delete() }
                    fail(refusal, release)
                    return@launch
                }
                downloadedFile = file
                setStatus(AppUpdateStatus.Downloaded(release))
                if (mutableState.value.dialog == AppUpdateDialog.Update) {
                    install()
                } else {
                    // Hidden while downloading: ask again, outside the readers.
                    mutableState.update { it.copy(dialog = AppUpdateDialog.Update) }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable + ioDispatcher) { file.delete() }
                throw cancelled
            } catch (error: Throwable) {
                withContext(ioDispatcher) { file.delete() }
                val progress = mutableState.value.status as? AppUpdateStatus.Downloading
                fail(
                    AppUpdateFailure.DownloadFailed(
                        downloadedBytes = progress?.downloadedBytes ?: 0,
                        totalBytes = progress?.totalBytes ?: release.apk.sizeBytes,
                    ),
                    release,
                )
            }
        }
    }

    fun cancelDownload() {
        val release = mutableState.value.newerRelease ?: return
        downloadJob?.cancel()
        downloadJob = null
        mutableState.update { state ->
            state.copy(status = AppUpdateStatus.Available(release), dialog = AppUpdateDialog.None)
        }
    }

    /** Installs the downloaded file, asking for Android's install permission first if needed. */
    fun install() {
        val state = mutableState.value
        val release = state.newerRelease ?: return
        val file = downloadedFile?.takeIf { it.exists() }
        if (file == null) {
            startDownload()
            return
        }
        if (!installer.canInstallPackages()) {
            mutableState.update { it.copy(dialog = AppUpdateDialog.InstallPermission) }
            return
        }
        mutableState.update { it.copy(status = AppUpdateStatus.Installing(release), dialog = AppUpdateDialog.Update) }
        scope.launch {
            try {
                withContext(ioDispatcher) { installer.install(file) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                fail(AppUpdateFailure.InstallFailed(error.message), release)
            }
        }
    }

    /** The next step for a failure: check, download or install again. */
    fun retry() {
        when ((mutableState.value.status as? AppUpdateStatus.Failed)?.failure) {
            AppUpdateFailure.CheckFailed -> checkNow()
            AppUpdateFailure.InstallCancelled, is AppUpdateFailure.InstallFailed -> install()
            is AppUpdateFailure.DownloadFailed, AppUpdateFailure.Damaged, is AppUpdateFailure.NotEnoughSpace -> {
                downloadedFile = null
                startDownload()
            }
            AppUpdateFailure.DifferentKey, AppUpdateFailure.NotThisApp, AppUpdateFailure.NotNewer, null -> Unit
        }
    }

    fun installPermissionSettingsIntent(): Intent = installer.installPermissionSettingsIntent()

    /** The user went to Android's "Install unknown apps" screen; continue when they're back. */
    fun onInstallPermissionSettingsOpened() {
        awaitingInstallPermission = true
        mutableState.update { it.copy(dialog = AppUpdateDialog.None) }
    }

    /** The activity resumed, possibly back from Android's settings. */
    fun onResumed() {
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        if (installer.canInstallPackages()) {
            install()
        } else {
            mutableState.update { it.copy(dialog = AppUpdateDialog.InstallPermission) }
        }
    }

    fun setAutoCheck(enabled: Boolean) {
        mutableState.update { it.copy(autoCheck = enabled) }
        persist { it.copy(autoCheck = enabled) }
    }

    fun showWhatsNew() {
        if (mutableState.value.currentRelease == null) return
        mutableState.update { it.copy(dialog = AppUpdateDialog.WhatsNew, updatedToVersion = null) }
    }

    fun dismissUpdatedNotice() {
        mutableState.update { it.copy(updatedToVersion = null) }
    }

    private suspend fun check(manual: Boolean) {
        if (checkJob?.isActive == true) return
        when (mutableState.value.status) {
            is AppUpdateStatus.Downloading, is AppUpdateStatus.Downloaded, is AppUpdateStatus.Installing -> return
            else -> Unit
        }
        val installed = installedApp ?: return
        val previousStatus = mutableState.value.status
        if (manual) setStatus(AppUpdateStatus.Checking)
        val job = scope.launch {
            val now = clock()
            val result = try {
                Result.success(withContext(ioDispatcher) { releaseSource.latestRelease() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = { release ->
                    val current = AppVersion.parse(installed.versionName)
                    val newer = release?.takeIf { current != null && it.version > current }
                    persist {
                        it.copy(
                            lastCheckAtMillis = now,
                            lastFailedCheckAtMillis = null,
                            latestRelease = release ?: it.latestRelease,
                        )
                    }
                    mutableState.update { state ->
                        state.copy(
                            lastCheckAtMillis = now,
                            status = when {
                                newer != null -> AppUpdateStatus.Available(newer)
                                manual -> AppUpdateStatus.UpToDate
                                else -> AppUpdateStatus.Idle
                            },
                        )
                    }
                    if (!manual) queueOffer(newer)
                },
                onFailure = {
                    persist { it.copy(lastFailedCheckAtMillis = now) }
                    setStatus(
                        if (manual) AppUpdateStatus.Failed(AppUpdateFailure.CheckFailed, null) else previousStatus,
                    )
                },
            )
        }
        checkJob = job
        job.join()
    }

    private fun isAutoCheckDue(): Boolean {
        val installed = installedApp ?: return false
        if (!preferences.autoCheck || installed.isDebuggable || isFirstRun) return false
        if (checkJob?.isActive == true) return false
        val now = clock()
        val lastCheck = preferences.lastCheckAtMillis
        val lastFailure = preferences.lastFailedCheckAtMillis
        return (lastCheck == null || now - lastCheck >= AUTO_CHECK_INTERVAL_MILLIS) &&
            (lastFailure == null || now - lastFailure >= FAILED_CHECK_RETRY_MILLIS)
    }

    private fun queueOffer(release: AppRelease?) {
        if (release == null || offeredThisLaunch) return
        if (preferences.dismissedVersion == release.version.toString()) return
        offeredThisLaunch = true
        mutableState.update { it.copy(pendingOffer = release) }
    }

    private fun onInstallEvent(event: AppUpdateInstallEvent) {
        val installing = mutableState.value.status as? AppUpdateStatus.Installing ?: return
        when (event) {
            is AppUpdateInstallEvent.NeedsConfirmation -> mutableInstallConfirmations.tryEmit(event.confirmation)
            // Android stops the app to replace it, so this is rarely seen.
            AppUpdateInstallEvent.Installed -> Unit
            AppUpdateInstallEvent.Cancelled -> fail(AppUpdateFailure.InstallCancelled, installing.release)
            is AppUpdateInstallEvent.Failed -> fail(AppUpdateFailure.InstallFailed(event.message), installing.release)
        }
    }

    private fun fail(failure: AppUpdateFailure, release: AppRelease?) {
        mutableState.update { state ->
            state.copy(status = AppUpdateStatus.Failed(failure, release), dialog = AppUpdateDialog.Update)
        }
    }

    private fun setStatus(status: AppUpdateStatus) {
        mutableState.update { it.copy(status = status) }
    }

    private fun persist(transform: (AppUpdatePreferences) -> AppUpdatePreferences) {
        preferences = transform(preferences)
        scope.launch(ioDispatcher) {
            runCatching { preferencesStore.update(transform) }
        }
    }

    companion object {
        /** A little under a day, so opening the app at about the same time each day checks. */
        const val AUTO_CHECK_INTERVAL_MILLIS = 20L * 60 * 60 * 1000
        const val FAILED_CHECK_RETRY_MILLIS = 60L * 60 * 1000

        /** The file plus the copy Android's installer stages while it installs. */
        private const val INSTALL_SPACE_FACTOR = 2
    }
}

private fun AppReleaseApk.fileName(): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "yabai-update.apk" }
