package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceSettingsActions
import com.example.unifiedreader.feature.settings.SourceSettingsEntry
import com.example.unifiedreader.feature.settings.SourceStartFlow
import com.example.unifiedreader.feature.settings.SourceStartMode
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState

/**
 * What opened the start flow: the first launch, "Add a server", or Connect on an empty library,
 * which opens straight on that [server]'s page.
 */
data class SourceStartRequest(
    val mode: SourceStartMode,
    val server: SourceType? = null,
)

/**
 * The start flow's request, kept across configuration changes. A fresh install that has not yet
 * answered "Where are your books?" starts with it open.
 */
@Composable
internal fun rememberSourceStartRequest(): MutableState<SourceStartRequest?> {
    val context = LocalContext.current.applicationContext
    return rememberSaveable(stateSaver = SourceStartRequestSaver) {
        mutableStateOf(SourceStartRequest(SourceStartMode.FirstLaunch).takeIf { firstLaunchPending(context) })
    }
}

/** The start flow over the whole app, while [request] is set. */
@Composable
internal fun SourceStartHost(
    request: SourceStartRequest,
    uiState: AppUiState,
    viewModel: AppViewModel,
    onImportLocalFileClick: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val finish = {
        markFirstLaunchDone(context)
        onFinished()
    }
    SourceStartFlow(
        mode = request.mode,
        initialServer = request.server,
        // In the source order, as the rail lists them.
        servers = rememberSourceSettingsEntries(uiState = uiState, viewModel = viewModel)
            .sortedBy { entry -> uiState.sourceOrder.indexOf(entry.sourceType) },
        onImportFile = {
            finish()
            viewModel.openLibrarySource(SourceType.LOCAL)
            onImportLocalFileClick()
        },
        onServerConnected = { server ->
            markFirstLaunchDone(context)
            // The first server connected opens when the app starts.
            if (uiState.navigationConnectedServers.none { it != server }) {
                viewModel.reorderSources(listOf(server) + uiState.sourceOrder.filter { it != server })
            }
        },
        onOpenLibrary = { server ->
            finish()
            viewModel.openLibrarySource(server)
        },
        onClose = {
            finish()
            if (request.mode == SourceStartMode.FirstLaunch) viewModel.openLibrarySource(SourceType.LOCAL)
        },
    )
}

/** Each server as Settings and the start flow see it: its connection, its actions, its last sync. */
@Composable
internal fun rememberSourceSettingsEntries(
    uiState: AppUiState,
    viewModel: AppViewModel,
    kavitaReadingDirectionLibraries: List<ReadingDirectionLibraryUiState> = emptyList(),
    stumpReadingDirectionLibraries: List<ReadingDirectionLibraryUiState> = emptyList(),
    kavitaReaderModeLibraries: List<ReaderModeLibraryUiState> = emptyList(),
    stumpReaderModeLibraries: List<ReaderModeLibraryUiState> = emptyList(),
): List<SourceSettingsEntry> {
    val syncTimes = rememberSourceSyncTimes()
    val kavitaActions = remember(viewModel) {
        RemoteSourceSettingsActions(
            onDisplayNameChanged = viewModel::onKavitaDisplayNameChanged,
            onBaseUrlChanged = viewModel::onKavitaBaseUrlChanged,
            onBackupBaseUrlChanged = viewModel::onKavitaBackupBaseUrlChanged,
            onUsernameChanged = viewModel::onKavitaUsernameChanged,
            onPasswordChanged = viewModel::onKavitaPasswordChanged,
            onConnect = viewModel::connectKavitaServer,
            onSync = viewModel::refreshKavitaLibrary,
            onFastSync = viewModel::fastSyncKavitaLibrary,
            onPreloadCovers = { viewModel.preloadCovers(SourceType.KAVITA) },
            onRetryFailedCovers = { viewModel.retryFailedCovers(SourceType.KAVITA) },
            onCancelCoverPreload = { viewModel.cancelCoverPreload(SourceType.KAVITA) },
            onClearCoverCache = { viewModel.clearCachedCovers(SourceType.KAVITA) },
        )
    }
    val grimmoryActions = remember(viewModel) {
        RemoteSourceSettingsActions(
            onDisplayNameChanged = viewModel::onGrimmoryDisplayNameChanged,
            onBaseUrlChanged = viewModel::onGrimmoryBaseUrlChanged,
            onBackupBaseUrlChanged = viewModel::onGrimmoryBackupBaseUrlChanged,
            onUsernameChanged = viewModel::onGrimmoryUsernameChanged,
            onPasswordChanged = viewModel::onGrimmoryPasswordChanged,
            onConnect = viewModel::connectGrimmoryServer,
            onSync = viewModel::refreshGrimmoryLibrary,
            onPreloadCovers = { viewModel.preloadCovers(SourceType.GRIMMORY) },
            onRetryFailedCovers = { viewModel.retryFailedCovers(SourceType.GRIMMORY) },
            onCancelCoverPreload = { viewModel.cancelCoverPreload(SourceType.GRIMMORY) },
            onClearCoverCache = { viewModel.clearCachedCovers(SourceType.GRIMMORY) },
        )
    }
    val stumpActions = remember(viewModel) {
        RemoteSourceSettingsActions(
            onDisplayNameChanged = viewModel::onStumpDisplayNameChanged,
            onBaseUrlChanged = viewModel::onStumpBaseUrlChanged,
            onBackupBaseUrlChanged = viewModel::onStumpBackupBaseUrlChanged,
            onUsernameChanged = viewModel::onStumpUsernameChanged,
            onPasswordChanged = viewModel::onStumpPasswordChanged,
            onConnect = viewModel::connectStumpServer,
            onSync = viewModel::refreshStumpLibrary,
            onPreloadCovers = { viewModel.preloadCovers(SourceType.STUMP) },
            onRetryFailedCovers = { viewModel.retryFailedCovers(SourceType.STUMP) },
            onCancelCoverPreload = { viewModel.cancelCoverPreload(SourceType.STUMP) },
            onClearCoverCache = { viewModel.clearCachedCovers(SourceType.STUMP) },
        )
    }
    fun connectivityOf(source: SourceType) = uiState.sourceConnectivityBySource[source] ?: SourceConnectivityState.Unknown
    return listOf(
        SourceSettingsEntry(
            sourceType = SourceType.KAVITA,
            lastSyncedAtEpochMillis = syncTimes[SourceType.KAVITA],
            connection = uiState.kavitaConnection,
            connectivity = connectivityOf(SourceType.KAVITA),
            readingDirectionLibraries = kavitaReadingDirectionLibraries,
            readerModeLibraries = kavitaReaderModeLibraries,
            actions = kavitaActions,
        ),
        SourceSettingsEntry(
            sourceType = SourceType.GRIMMORY,
            lastSyncedAtEpochMillis = syncTimes[SourceType.GRIMMORY],
            connection = uiState.grimmoryConnection,
            connectivity = connectivityOf(SourceType.GRIMMORY),
            actions = grimmoryActions,
        ),
        SourceSettingsEntry(
            sourceType = SourceType.STUMP,
            lastSyncedAtEpochMillis = syncTimes[SourceType.STUMP],
            connection = uiState.stumpConnection,
            connectivity = connectivityOf(SourceType.STUMP),
            readingDirectionLibraries = stumpReadingDirectionLibraries,
            readerModeLibraries = stumpReaderModeLibraries,
            actions = stumpActions,
        ),
    )
}

private val SourceStartRequestSaver: Saver<SourceStartRequest?, String> = Saver(
    save = { request -> request?.let { "${it.mode.name}:${it.server?.name.orEmpty()}" } ?: "" },
    restore = { saved ->
        saved.takeIf(String::isNotEmpty)?.let {
            val (mode, server) = it.split(':', limit = 2)
            SourceStartRequest(
                mode = SourceStartMode.valueOf(mode),
                server = server.takeIf(String::isNotEmpty)?.let(SourceType::valueOf),
            )
        }
    },
)

/**
 * The first launch is owed by a fresh install (installed, never updated) that has not answered it;
 * an update over an older version never shows it.
 */
private fun firstLaunchPending(context: Context): Boolean {
    if (firstLaunchPreferences(context).getBoolean(KeyFirstLaunchDone, false)) return false
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return false
    return info.firstInstallTime == info.lastUpdateTime
}

private fun markFirstLaunchDone(context: Context) {
    firstLaunchPreferences(context).edit { putBoolean(KeyFirstLaunchDone, true) }
}

private fun firstLaunchPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences(FirstLaunchPreferences, Context.MODE_PRIVATE)

private const val FirstLaunchPreferences = "first_launch"
private const val KeyFirstLaunchDone = "done"
