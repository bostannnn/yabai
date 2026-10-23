package com.example.unifiedreader.feature.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCopyTest {
    private val release = AppRelease(
        version = AppVersion.parse("0.13.0")!!,
        tagName = "0.13.0",
        notes = emptyList(),
        pageUrl = "",
        apk = AppReleaseApk("yabai-0.13.0.apk", "https://example.com/yabai.apk", 61_179_881, null),
    )

    @Test
    fun `sizes read like Android's settings`() {
        assertEquals("61 MB", formatUpdateSize(61_179_881))
        assertEquals("9.4 MB", formatUpdateSize(9_400_000))
        assertEquals("24 MB", formatUpdateSize(24_000_000))
        assertEquals("512 KB", formatUpdateSize(512_000))
    }

    @Test
    fun `the version row names the next step for each state`() {
        fun row(status: AppUpdateStatus, lastChecked: String? = null) =
            versionRowCopy("0.12.0", AppUpdateState(status = status), lastChecked)

        assertEquals("0.12.0 · checked 2 hours ago", row(AppUpdateStatus.Idle, "2 hours ago").supporting)
        assertEquals("0.12.0", row(AppUpdateStatus.Idle).supporting)
        assertEquals("Check for updates", row(AppUpdateStatus.Idle).button?.label)
        assertTrue(row(AppUpdateStatus.Checking).button!!.busy)
        assertTrue(row(AppUpdateStatus.UpToDate).showsCheck)
        assertEquals("0.13.0 is available · 61 MB", row(AppUpdateStatus.Available(release)).supporting)
        assertEquals(AppUpdateAction.OpenUpdate, row(AppUpdateStatus.Available(release)).button?.action)

        val downloading = row(AppUpdateStatus.Downloading(release, 24_000_000, 61_179_881))
        assertEquals("Downloading 0.13.0 · 24 MB of 61 MB", downloading.supporting)
        assertEquals(AppUpdateAction.CancelDownload, downloading.button?.action)
        assertEquals(0.39f, downloading.progress!!, 0.01f)

        assertEquals("Install", row(AppUpdateStatus.Downloaded(release)).button?.label)
        val checkFailed = row(AppUpdateStatus.Failed(AppUpdateFailure.CheckFailed, null))
        assertEquals(AppUpdateTone.Error, checkFailed.tone)
        assertEquals("Try again", checkFailed.button?.label)
        assertEquals(
            AppUpdateAction.OpenReleasePage,
            row(AppUpdateStatus.Failed(AppUpdateFailure.DifferentKey, release)).button?.action,
        )
    }

    @Test
    fun `failures say what happened and offer the next step`() {
        val dropped = failureCopy(AppUpdateFailure.DownloadFailed(24_000_000, 61_179_881), "0.12.0")
        assertEquals("Couldn't download the update", dropped.title)
        assertTrue(dropped.body.contains("24 MB of 61 MB"))
        assertEquals(AppUpdateAction.Retry, dropped.confirm?.action)

        val space = failureCopy(AppUpdateFailure.NotEnoughSpace(122_000_000, 12_000_000), "0.12.0")
        assertTrue(space.body.contains("122 MB") && space.body.contains("12 MB"))

        val cancelled = failureCopy(AppUpdateFailure.InstallCancelled, "0.12.0")
        assertEquals("Later", cancelled.dismiss.label)
        assertEquals("Install", cancelled.confirm?.label)

        assertEquals("Open on GitHub", failureCopy(AppUpdateFailure.DifferentKey, "0.12.0").confirm?.label)
    }
}
