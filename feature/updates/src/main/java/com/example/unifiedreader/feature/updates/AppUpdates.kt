package com.example.unifiedreader.feature.updates

import android.content.Context
import io.ktor.client.HttpClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Builds the app's updater. Construction only: nothing is read or fetched until it starts. */
object AppUpdates {
    fun createController(
        context: Context,
        httpClient: HttpClient,
    ): AppUpdateController {
        val appContext = context.applicationContext
        return AppUpdateController(
            releaseSource = GitHubReleaseSource(httpClient),
            downloader = KtorAppUpdateDownloader(httpClient),
            packageInspector = PackageManagerAppPackageInspector(appContext),
            installer = PackageInstallerAppUpdateInstaller(appContext),
            preferencesStore = DataStoreAppUpdatePreferencesStore(appContext),
            downloadDirectory = { File(appContext.cacheDir, DOWNLOAD_DIRECTORY_NAME) },
            // State changes on the main thread; file and network work hop to IO.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            ioDispatcher = Dispatchers.IO,
        )
    }

    private const val DOWNLOAD_DIRECTORY_NAME = "updates"
}
