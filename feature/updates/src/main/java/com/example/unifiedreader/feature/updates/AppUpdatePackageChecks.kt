package com.example.unifiedreader.feature.updates

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.io.File
import java.security.MessageDigest

/** What the running app is, for comparing a downloaded APK against it. */
data class InstalledAppInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    /** SHA-256 of each certificate the installed app is signed with. */
    val signingCertificates: Set<String>,
    /** Debug builds are signed with another key and never update from GitHub by themselves. */
    val isDebuggable: Boolean,
    /** True on the first launch after a fresh install (Android has never updated it). */
    val isFreshInstall: Boolean,
)

/** The facts read from a downloaded APK. */
data class ApkFileInfo(
    val packageName: String,
    val versionCode: Long,
    val signingCertificates: Set<String>,
)

/** Reads the running app and APK files through the package manager. */
interface AppPackageInspector {
    fun installedApp(): InstalledAppInfo

    /** Null when the file is not an APK Android can parse. */
    fun inspectApk(file: File): ApkFileInfo?
}

class PackageManagerAppPackageInspector(context: Context) : AppPackageInspector {
    private val appContext = context.applicationContext

    override fun installedApp(): InstalledAppInfo {
        val packageManager = appContext.packageManager
        val info = packageManager.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        return InstalledAppInfo(
            packageName = info.packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = info.longVersionCode,
            signingCertificates = info.signingCertificateDigests(),
            isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            isFreshInstall = info.firstInstallTime == info.lastUpdateTime,
        )
    }

    override fun inspectApk(file: File): ApkFileInfo? {
        val info = appContext.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        ) ?: return null
        return ApkFileInfo(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            signingCertificates = info.signingCertificateDigests(),
        )
    }
}

private fun PackageInfo.signingCertificateDigests(): Set<String> {
    val signingInfo = signingInfo ?: return emptySet()
    val signatures: Array<Signature> = if (signingInfo.hasMultipleSigners()) {
        signingInfo.apkContentsSigners
    } else {
        signingInfo.signingCertificateHistory
    } ?: return emptySet()
    return signatures.mapTo(mutableSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
    }
}

/** Why a downloaded file is refused, or null when it is a newer build of this app. */
internal fun refuseApk(
    installed: InstalledAppInfo,
    apk: ApkFileInfo?,
    expectedSha256: String?,
    actualSha256: String,
): AppUpdateFailure? = when {
    expectedSha256 != null && !expectedSha256.equals(actualSha256, ignoreCase = true) -> AppUpdateFailure.Damaged
    apk == null -> AppUpdateFailure.Damaged
    apk.packageName != installed.packageName -> AppUpdateFailure.NotThisApp
    apk.signingCertificates.none { it in installed.signingCertificates } -> AppUpdateFailure.DifferentKey
    apk.versionCode <= installed.versionCode -> AppUpdateFailure.NotNewer
    else -> null
}
