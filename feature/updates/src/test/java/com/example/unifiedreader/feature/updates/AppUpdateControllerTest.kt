package com.example.unifiedreader.feature.updates

import android.content.Intent
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateControllerTest {
    private val now = 1_800_000_000_000L
    private val directory: File = Files.createTempDirectory("updates-test").toFile()
    private val releaseSource = FakeReleaseSource()
    private val downloader = FakeDownloader()
    private val inspector = FakeInspector()
    private val installer = FakeInstaller()
    private val preferences = InMemoryPreferencesStore()
    private val installEvents = MutableSharedFlow<AppUpdateInstallEvent>(extraBufferCapacity = 4)
    private var freeSpace = Long.MAX_VALUE

    // Not backgroundScope: advanceUntilIdle() does not run background work.
    private val controllerJob = SupervisorJob()

    @After
    fun tearDown() {
        controllerJob.cancel()
        directory.deleteRecursively()
    }

    private fun TestScope.controller(clock: () -> Long = { now }): AppUpdateController = AppUpdateController(
        releaseSource = releaseSource,
        downloader = downloader,
        packageInspector = inspector,
        installer = installer,
        preferencesStore = preferences,
        downloadDirectory = { File(directory, "updates") },
        scope = CoroutineScope(controllerJob + StandardTestDispatcher(testScheduler)),
        ioDispatcher = StandardTestDispatcher(testScheduler),
        clock = clock,
        freeSpaceBytes = { freeSpace },
        installEvents = installEvents,
    ).also { it.start() }

    @Test
    fun `the library checks once, offers a newer release, and doesn't check again within a day`() = runTest {
        releaseSource.release = release("0.13.0")
        val controller = controller()
        advanceUntilIdle()

        controller.onLibraryShown()
        advanceUntilIdle()
        controller.onLibraryShown()
        advanceUntilIdle()

        assertEquals(1, releaseSource.calls)
        assertEquals("0.13.0", controller.state.value.pendingOffer?.version.toString())
        assertTrue(controller.state.value.status is AppUpdateStatus.Available)
        assertEquals(now, preferences.stored.lastCheckAtMillis)

        controller.presentOffer()
        assertEquals(AppUpdateDialog.Update, controller.state.value.dialog)
        assertNull(controller.state.value.pendingOffer)
    }

    @Test
    fun `a check is due again after the interval, and a failed one retries after an hour`() = runTest {
        preferences.stored = AppUpdatePreferences(
            lastCheckAtMillis = now - AppUpdateController.AUTO_CHECK_INTERVAL_MILLIS,
            lastFailedCheckAtMillis = now - AppUpdateController.FAILED_CHECK_RETRY_MILLIS + 1,
        )
        val controller = controller()
        advanceUntilIdle()

        controller.onLibraryShown()
        advanceUntilIdle()
        assertEquals(0, releaseSource.calls)

        preferences.stored = AppUpdatePreferences(lastFailedCheckAtMillis = now - AppUpdateController.FAILED_CHECK_RETRY_MILLIS)
        val second = controllerAfterRestart()
        second.onLibraryShown()
        advanceUntilIdle()
        assertEquals(1, releaseSource.calls)
    }

    @Test
    fun `no automatic check when it is turned off, on a debug build, or on the first launch`() = runTest {
        preferences.stored = AppUpdatePreferences(autoCheck = false, lastRunVersion = "0.12.0")
        controller().also { advanceUntilIdle() }.onLibraryShown()
        advanceUntilIdle()

        preferences.stored = AppUpdatePreferences(lastRunVersion = "0.12.0")
        inspector.installed = inspector.installed.copy(isDebuggable = true)
        controllerAfterRestart().onLibraryShown()
        advanceUntilIdle()

        preferences.stored = AppUpdatePreferences()
        inspector.installed = inspector.installed.copy(isDebuggable = false, isFreshInstall = true)
        controllerAfterRestart().onLibraryShown()
        advanceUntilIdle()

        assertEquals(0, releaseSource.calls)
    }

    @Test
    fun `the button checks even when automatic checks are off, and says when nothing is newer`() = runTest {
        preferences.stored = AppUpdatePreferences(autoCheck = false)
        releaseSource.release = release("0.12.0")
        val controller = controller()
        advanceUntilIdle()

        controller.checkNow()
        advanceUntilIdle()

        assertEquals(1, releaseSource.calls)
        assertEquals(AppUpdateStatus.UpToDate, controller.state.value.status)
        assertNull(controller.state.value.pendingOffer)
    }

    @Test
    fun `a failed manual check shows it, a failed automatic one stays quiet`() = runTest {
        releaseSource.failure = IOException("offline")
        val controller = controller()
        advanceUntilIdle()

        controller.onLibraryShown()
        advanceUntilIdle()
        assertEquals(AppUpdateStatus.Idle, controller.state.value.status)
        assertEquals(now, preferences.stored.lastFailedCheckAtMillis)

        controller.checkNow()
        advanceUntilIdle()
        assertEquals(AppUpdateStatus.Failed(AppUpdateFailure.CheckFailed, null), controller.state.value.status)
    }

    @Test
    fun `Later stops the offer for that version, but Settings still has it`() = runTest {
        preferences.stored = AppUpdatePreferences(
            latestRelease = release("0.13.0"),
            lastCheckAtMillis = now,
            lastRunVersion = "0.12.0",
        )
        val controller = controller()
        advanceUntilIdle()
        controller.onLibraryShown()
        advanceUntilIdle()
        controller.presentOffer()

        controller.dismissOffer()
        advanceUntilIdle()

        assertEquals("0.13.0", preferences.stored.dismissedVersion)
        assertEquals(AppUpdateDialog.None, controller.state.value.dialog)
        assertTrue(controller.state.value.status is AppUpdateStatus.Available)

        val restarted = controllerAfterRestart()
        restarted.onLibraryShown()
        advanceUntilIdle()
        assertNull(restarted.state.value.pendingOffer)
        assertEquals("0.13.0", restarted.state.value.newerRelease?.version.toString())
    }

    @Test
    fun `Update downloads, checks the file and hands it to the installer`() = runTest {
        val controller = availableController()

        controller.startDownload()
        advanceUntilIdle()

        assertEquals(1, installer.installed.size)
        assertEquals("yabai-0.13.0.apk", installer.installed.single().name)
        assertTrue(controller.state.value.status is AppUpdateStatus.Installing)
        assertEquals(AppUpdateDialog.Update, controller.state.value.dialog)
    }

    @Test
    fun `a download hidden from view asks again when it is done instead of installing`() = runTest {
        val controller = availableController()
        downloader.gate = CompletableDeferred()

        controller.startDownload()
        advanceUntilIdle()
        controller.closeDialog()
        downloader.gate!!.complete(Unit)
        advanceUntilIdle()

        assertTrue(installer.installed.isEmpty())
        assertTrue(controller.state.value.status is AppUpdateStatus.Downloaded)
        assertEquals(AppUpdateDialog.Update, controller.state.value.dialog)
    }

    @Test
    fun `a file that doesn't match GitHub's checksum is deleted and never installed`() = runTest {
        val controller = availableController(sha256 = "0".repeat(64))

        controller.startDownload()
        advanceUntilIdle()

        assertEquals(AppUpdateStatus.Failed(AppUpdateFailure.Damaged, release("0.13.0", sha256 = "0".repeat(64))), controller.state.value.status)
        assertTrue(installer.installed.isEmpty())
        assertFalse(File(directory, "updates/yabai-0.13.0.apk").exists())
    }

    @Test
    fun `an apk signed with another key is refused`() = runTest {
        inspector.apk = inspector.apk.copy(signingCertificates = setOf("debug-key"))
        val controller = availableController()

        controller.startDownload()
        advanceUntilIdle()

        val status = controller.state.value.status as AppUpdateStatus.Failed
        assertEquals(AppUpdateFailure.DifferentKey, status.failure)
        assertTrue(installer.installed.isEmpty())
    }

    @Test
    fun `not enough space stops before downloading`() = runTest {
        freeSpace = 1_000
        val controller = availableController()

        controller.startDownload()
        advanceUntilIdle()

        val status = controller.state.value.status as AppUpdateStatus.Failed
        assertEquals(AppUpdateFailure.NotEnoughSpace(neededBytes = 2 * ApkSize, freeBytes = 1_000), status.failure)
        assertEquals(0, downloader.calls)
    }

    @Test
    fun `without install permission it explains first, then installs when the user comes back`() = runTest {
        installer.canInstall = false
        val controller = availableController()

        controller.startDownload()
        advanceUntilIdle()
        assertEquals(AppUpdateDialog.InstallPermission, controller.state.value.dialog)
        assertTrue(installer.installed.isEmpty())

        controller.onInstallPermissionSettingsOpened()
        installer.canInstall = true
        controller.onResumed()
        advanceUntilIdle()

        assertEquals(1, installer.installed.size)
    }

    @Test
    fun `closing Android's installer keeps the download for another try`() = runTest {
        val controller = availableController()
        controller.startDownload()
        advanceUntilIdle()

        installEvents.emit(AppUpdateInstallEvent.Cancelled)
        advanceUntilIdle()
        val status = controller.state.value.status as AppUpdateStatus.Failed
        assertEquals(AppUpdateFailure.InstallCancelled, status.failure)

        controller.retry()
        advanceUntilIdle()
        assertEquals(1, downloader.calls)
        assertEquals(2, installer.installed.size)
    }

    @Test
    fun `cancelling a download goes back to the offer and deletes the file`() = runTest {
        val controller = availableController()
        downloader.gate = CompletableDeferred()

        controller.startDownload()
        advanceUntilIdle()
        controller.cancelDownload()
        advanceUntilIdle()

        assertTrue(controller.state.value.status is AppUpdateStatus.Available)
        assertFalse(File(directory, "updates/yabai-0.13.0.apk").exists())
    }

    @Test
    fun `the first launch of a newer version says it was updated and keeps its notes`() = runTest {
        preferences.stored = AppUpdatePreferences(lastRunVersion = "0.11.0", latestRelease = release("0.12.0"))

        val controller = controller()
        advanceUntilIdle()

        assertEquals("0.12.0", controller.state.value.updatedToVersion)
        assertEquals("0.12.0", controller.state.value.currentRelease?.version.toString())
        assertEquals("0.12.0", preferences.stored.lastRunVersion)

        controller.showWhatsNew()
        assertEquals(AppUpdateDialog.WhatsNew, controller.state.value.dialog)
        assertNull(controller.state.value.updatedToVersion)
    }

    @Test
    fun `starting clears an earlier launch's download`() = runTest {
        val leftover = File(directory, "updates/yabai-0.13.0.apk").apply { parentFile!!.mkdirs(); writeText("old") }

        controller()
        advanceUntilIdle()

        assertFalse(leftover.exists())
    }

    @Test
    fun `refusals name the reason`() {
        val installed = FakeInspector().installed
        val good = ApkFileInfo(installed.packageName, installed.versionCode + 1, installed.signingCertificates)

        assertNull(refuseApk(installed, good, expectedSha256 = "AB", actualSha256 = "ab"))
        assertNull(refuseApk(installed, good, expectedSha256 = null, actualSha256 = "ab"))
        assertEquals(AppUpdateFailure.Damaged, refuseApk(installed, good, "aa", "ab"))
        assertEquals(AppUpdateFailure.Damaged, refuseApk(installed, null, null, "ab"))
        assertEquals(AppUpdateFailure.NotThisApp, refuseApk(installed, good.copy(packageName = "other"), null, "ab"))
        assertEquals(AppUpdateFailure.DifferentKey, refuseApk(installed, good.copy(signingCertificates = setOf("x")), null, "ab"))
        assertEquals(AppUpdateFailure.NotNewer, refuseApk(installed, good.copy(versionCode = installed.versionCode), null, "ab"))
    }

    private fun TestScope.controllerAfterRestart(): AppUpdateController = controller().also { advanceUntilIdle() }

    private fun TestScope.availableController(sha256: String? = null): AppUpdateController {
        preferences.stored = AppUpdatePreferences(
            latestRelease = release("0.13.0", sha256 = sha256 ?: downloader.sha256),
            lastCheckAtMillis = now,
            lastRunVersion = "0.12.0",
        )
        return controller().also { advanceUntilIdle() }
    }

    private class FakeReleaseSource : AppReleaseSource {
        var release: AppRelease? = null
        var failure: Throwable? = null
        var calls = 0

        override suspend fun latestRelease(): AppRelease? {
            calls++
            failure?.let { throw it }
            return release
        }
    }

    private class FakeDownloader : AppUpdateDownloader {
        val sha256 = "5d41402abc4b2a76b9719d911017c592ae59a1b8b0f2a1d2e1f4b4b4b4b4b4b4"
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0

        override suspend fun download(
            apk: AppReleaseApk,
            destination: File,
            onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        ): String {
            calls++
            destination.writeText("apk")
            onProgress(apk.sizeBytes / 2, apk.sizeBytes)
            gate?.await()
            onProgress(apk.sizeBytes, apk.sizeBytes)
            return sha256
        }
    }

    private class FakeInspector : AppPackageInspector {
        var installed = InstalledAppInfo(
            packageName = "com.example.unifiedreader",
            versionName = "0.12.0",
            versionCode = 11,
            signingCertificates = setOf("release-key"),
            isDebuggable = false,
            isFreshInstall = false,
        )
        var apk = ApkFileInfo(packageName = "com.example.unifiedreader", versionCode = 12, signingCertificates = setOf("release-key"))

        override fun installedApp(): InstalledAppInfo = installed

        override fun inspectApk(file: File): ApkFileInfo? = apk.takeIf { file.exists() }
    }

    private class FakeInstaller : AppUpdateInstaller {
        var canInstall = true
        val installed = mutableListOf<File>()

        override fun canInstallPackages(): Boolean = canInstall

        override fun installPermissionSettingsIntent(): Intent = throw UnsupportedOperationException()

        override fun install(file: File) {
            installed += file
        }
    }

    private class InMemoryPreferencesStore : AppUpdatePreferencesStore {
        var stored = AppUpdatePreferences()

        override suspend fun read(): AppUpdatePreferences = stored

        override suspend fun update(transform: (AppUpdatePreferences) -> AppUpdatePreferences) {
            stored = transform(stored)
        }
    }

    private companion object {
        const val ApkSize = 61_179_881L

        fun release(version: String, sha256: String? = null) = AppRelease(
            version = AppVersion.parse(version)!!,
            tagName = version,
            notes = listOf("Add motion: screen transitions"),
            pageUrl = "https://github.com/bostannnn/yabai/releases/tag/$version",
            apk = AppReleaseApk(
                name = "yabai-$version.apk",
                downloadUrl = "https://github.com/bostannnn/yabai/releases/download/$version/yabai-$version.apk",
                sizeBytes = ApkSize,
                sha256 = sha256,
            ),
        )
    }
}
