package com.example.unifiedreader

import android.content.ComponentCallbacks2
import android.content.ContentResolver
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.LocalLibraryCoverGeneration
import com.example.unifiedreader.feature.reader.core.ReaderHost
import com.example.unifiedreader.feature.reader.core.ReaderNavigationActions
import com.example.unifiedreader.feature.updates.AppUpdateActions
import com.example.unifiedreader.feature.updates.AppUpdateController
import com.example.unifiedreader.feature.updates.AppUpdateHost
import com.example.unifiedreader.feature.updates.AppUpdateState
import com.example.unifiedreader.feature.updates.rememberAppUpdateActions
import com.example.unifiedreader.feature.library.rememberLibraryCoverColors
import com.example.unifiedreader.ui.components.AppBackground
import com.example.unifiedreader.ui.components.AppScreenBack
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.LocalAppToastState
import com.example.unifiedreader.ui.theme.AppCoverAccentTheme
import com.example.unifiedreader.ui.theme.ApplyLaunchSplashTheme
import com.example.unifiedreader.ui.theme.UnifiedReaderTheme
import com.example.unifiedreader.ui.theme.applyAndroidThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun UnifiedReaderRoute() {
    val viewModel: AppViewModel = hiltViewModel()
    val updates: AppUpdatesViewModel = hiltViewModel()
    val context = LocalContext.current
    val localFileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importDocumentIntoApp(
                contentResolver = context.contentResolver,
                viewModel = viewModel,
                uri = uri,
            )
        }
    }

    UnifiedReaderAppContent(
        viewModel = viewModel,
        onImportLocalFileClick = {
            localFileImportLauncher.launch(
                arrayOf("*/*"),
            )
        },
        updateController = updates.controller,
    )
}

@Composable
fun UnifiedReaderAppContent(
    viewModel: AppViewModel,
    onImportLocalFileClick: () -> Unit,
    updateController: AppUpdateController? = null,
) {
    val uiState = viewModel.uiState
    val updateStateFlow = remember(updateController) {
        updateController?.state ?: MutableStateFlow(AppUpdateState())
    }
    val updateState by updateStateFlow.collectAsStateWithLifecycle()
    val updateActions = updateController?.let { controller -> rememberAppUpdateActions(controller) }
        ?: remember { AppUpdateActions() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var libraryViewMode by rememberSaveable { mutableStateOf(LibraryViewMode.Grid) }

    DisposableEffect(context, viewModel) {
        val applicationContext = context.applicationContext
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            override fun onLowMemory() {
                viewModel.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }

            override fun onTrimMemory(level: Int) {
                viewModel.onTrimMemory(level)
            }
        }
        applicationContext.registerComponentCallbacks(callbacks)
        onDispose {
            applicationContext.unregisterComponentCallbacks(callbacks)
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshProgress()
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppStopped()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    UnifiedReaderTheme(themeMode = uiState.themeMode) {
        DisposableEffect(uiState.themeMode) {
            applyAndroidThemeMode(uiState.themeMode)
            onDispose { }
        }
        ApplyLaunchSplashTheme(uiState.themeMode)
        // The start flow: the first launch, "Add a server", Connect on an empty library.
        var sourceStartRequest by rememberSourceStartRequest()
        val isReaderMode = uiState.activeReader != null && uiState.selectedItem != null
        // Leaving the reader is handled by the screen host below, with the predictive gesture.
        BackHandler(
            enabled = uiState.selectedKavitaSeriesKey != null ||
                uiState.selectedStumpSeriesKey != null ||
                uiState.isKavitaScopeActive ||
                uiState.isKavitaVisualSectionActive,
            onBack = viewModel::onBackPressed,
        )
        // One toast state for the app: the shell, a book and every sheet show it where they are.
        CompositionLocalProvider(LocalAppToastState provides viewModel.toasts) {
            AppBackground(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val rootScreen = if (isReaderMode) {
                        AppRootScreen.Reader(itemId = uiState.selectedItem!!.id)
                    } else {
                        AppRootScreen.Shell
                    }
                    AppScreenHost(
                        screen = rootScreen,
                        // The reader that is closing keeps drawing its book while it fades.
                        frame = uiState.selectedItem,
                        back = if (isReaderMode) AppScreenBack(AppRootScreen.Shell, viewModel::onBackPressed) else null,
                        // One reader slot: the next book in a series replaces the open one in place.
                        contentKey = { screen -> screen is AppRootScreen.Reader },
                        modifier = Modifier.fillMaxSize(),
                    ) { screen, readerItem ->
                        if (screen is AppRootScreen.Reader && readerItem != null) {
                            // The reader's accent (slider fills, selected states) is the book's cover colour.
                            AppCoverAccentTheme(rememberLibraryCoverColors(readerItem, viewModel::coverImageUrlFor)) {
                                ReaderHost(
                                    item = readerItem,
                                    factories = viewModel.readerFactories,
                                    onBack = viewModel::onBackPressed,
                                    readerWorkCoordinator = viewModel.readerWorkCoordinator,
                                    navigationActions = ReaderNavigationActions(
                                        onRequestNextInSeries = viewModel::onReaderNextRequested,
                                        onRequestCompletion = viewModel::onReaderCompletionRequested,
                                        currentOpenMode = uiState.selectedReaderOpenMode,
                                    ),
                                )
                            }
                        } else {
                            // Remote covers are asked for again after a reset or a catalog sync.
                            val coverGeneration by viewModel.coverResetGeneration.collectAsStateWithLifecycle()
                            CompositionLocalProvider(LocalLibraryCoverGeneration provides coverGeneration) {
                                UnifiedReaderScaffold(
                                    uiState = uiState,
                                    libraryViewMode = libraryViewMode,
                                    onLibraryViewModeChanged = { libraryViewMode = it },
                                    onImportLocalFileClick = onImportLocalFileClick,
                                    viewModel = viewModel,
                                    updateState = updateState,
                                    updateActions = updateActions,
                                    onStartSource = { request -> sourceStartRequest = request },
                                )
                            }
                        }
                    }
                    sourceStartRequest?.takeIf { !isReaderMode }?.let { request ->
                        SourceStartHost(
                            request = request,
                            uiState = uiState,
                            viewModel = viewModel,
                            onImportLocalFileClick = onImportLocalFileClick,
                            onFinished = { sourceStartRequest = null },
                        )
                    }
                    ProgressSyncNoticeToast(
                        notice = uiState.progressSyncNotice,
                        viewModel = viewModel,
                    )
                    ReaderFinishedHost(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
                    if (updateController != null) {
                        AppUpdateHost(
                            controller = updateController,
                            state = updateState,
                            actions = updateActions,
                            isReaderOpen = isReaderMode,
                            isLibraryVisible = !isReaderMode &&
                                uiState.topLevel == TopLevelDestination.Library &&
                                !viewModel.isDownloadsSheetVisible &&
                                uiState.readerCompletionState == null,
                        )
                    }
                }
            }
        }
    }
}

/** The two screens at the root: the app shell (library, journal, settings) or a book. */
private sealed interface AppRootScreen {
    data object Shell : AppRootScreen

    data class Reader(val itemId: String) : AppRootScreen
}

/** A failed progress sync becomes an error toast with Retry; the state's notice is then cleared. */
@Composable
private fun ProgressSyncNoticeToast(
    notice: AppProgressSyncNotice?,
    viewModel: AppViewModel,
) {
    if (notice == null) return
    LaunchedEffect(notice.id) {
        viewModel.toasts.show(AppToasts.syncFailure(notice, onRetry = viewModel::retryPendingRemoteSync))
        viewModel.dismissProgressSyncNotice(notice.id)
    }
}

internal fun importDocumentIntoApp(
    contentResolver: ContentResolver,
    viewModel: AppViewModel,
    uri: Uri,
) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
    viewModel.onDocumentImported(uri)
}


