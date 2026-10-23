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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.journal.JournalItemReviewCard
import com.example.unifiedreader.feature.journal.rememberJournalReviewState
import com.example.unifiedreader.feature.journal.shouldSaveJournalReview
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.VisibleResolvedLibraryCover
import com.example.unifiedreader.feature.reader.core.ReaderCompletionDialog
import com.example.unifiedreader.feature.reader.core.ReaderHost
import com.example.unifiedreader.feature.reader.core.ReaderNavigationActions
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
import com.example.unifiedreader.feature.updates.AppUpdateActions
import com.example.unifiedreader.feature.updates.AppUpdateController
import com.example.unifiedreader.feature.updates.AppUpdateHost
import com.example.unifiedreader.feature.updates.AppUpdateState
import com.example.unifiedreader.feature.updates.rememberAppUpdateActions
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.ui.components.AppBackground
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppDialog
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppScreenBack
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppSnackbar
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.theme.UnifiedReaderTheme
import com.example.unifiedreader.ui.theme.applyAndroidThemeMode
import kotlinx.coroutines.delay
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
    if (viewModel.isUnsupportedImportDialogVisible) {
        AppDialog(
            onDismissRequest = viewModel::dismissUnsupportedImportDialog,
            title = { Text(text = "Unsupported file") },
            text = { Text(text = UnsupportedImportSelectionMessage) },
            confirmButton = {
                AppDialogConfirmButton(text = "OK", onClick = viewModel::dismissUnsupportedImportDialog)
            },
        )
    }
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
        val isReaderMode = uiState.activeReader != null && uiState.selectedItem != null
        // Leaving the reader is handled by the screen host below, with the predictive gesture.
        BackHandler(
            enabled = uiState.selectedKavitaSeriesKey != null ||
                uiState.selectedStumpSeriesKey != null ||
                uiState.isKavitaScopeActive ||
                uiState.isKavitaVisualSectionActive,
            onBack = viewModel::onBackPressed,
        )
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
                    } else {
                        UnifiedReaderScaffold(
                            uiState = uiState,
                            libraryViewMode = libraryViewMode,
                            onLibraryViewModeChanged = { libraryViewMode = it },
                            onImportLocalFileClick = onImportLocalFileClick,
                            viewModel = viewModel,
                            updateState = updateState,
                            updateActions = updateActions,
                        )
                    }
                }
                ProgressSyncNoticeHost(
                    notice = uiState.progressSyncNotice,
                    onDismiss = viewModel::dismissProgressSyncNotice,
                )
                ReaderCompletionDialogHost(
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

/** The two screens at the root: the app shell (library, journal, settings) or a book. */
private sealed interface AppRootScreen {
    data object Shell : AppRootScreen

    data class Reader(val itemId: String) : AppRootScreen
}

@Composable
private fun ReaderCompletionDialogHost(
    uiState: AppUiState,
    viewModel: AppViewModel,
) {
    val completionState = uiState.readerCompletionState ?: return
    val item = uiState.items.firstOrNull { it.id == completionState.itemId }
        ?: uiState.selectedItem?.takeIf { it.id == completionState.itemId }
        ?: return
    val nextItem = completionState.nextItemId?.let { nextItemId ->
        uiState.items.firstOrNull { item -> item.id == nextItemId }
    }
    val entry = uiState.journalSnapshot.entryByItemId[item.id]
    val reviewState = rememberJournalReviewState(
        itemId = item.id,
        initialRating = entry?.rating,
        initialReviewText = entry?.reviewText.orEmpty(),
    )
    val journalItem = item.toJournalItemRef()
    val saveReviewIfNeeded = {
        if (shouldSaveJournalReview(
                initialRating = entry?.rating,
                initialReviewText = entry?.reviewText.orEmpty(),
                rating = reviewState.selectedRating,
                reviewText = reviewState.reviewText,
            )
        ) {
            viewModel.saveJournalEntry(
                item = journalItem,
                rating = reviewState.selectedRating,
                reviewText = reviewState.reviewText,
            )
        }
    }
    val dismissCompletion = {
        saveReviewIfNeeded()
        viewModel.dismissReaderCompletion()
    }

    ReaderCompletionDialog(
        nextItem = nextItem,
        nextItemTitle = nextItem?.libraryPrimaryText(),
        nextItemSubtitle = nextItem?.librarySecondaryText(),
        coverContent = { coverItem, modifier ->
            VisibleResolvedLibraryCover(
                item = coverItem,
                coverImageUrlProvider = viewModel::coverImageUrlFor,
                onCoverVisible = viewModel::ensureCoverCached,
                imageVariant = LibraryCoverImageVariant.ShelfRow,
                modifier = modifier,
            )
        },
        reviewContent = {
            JournalItemReviewCard(
                item = journalItem,
                reviewState = reviewState,
                coverImageUrlProvider = viewModel::coverImageUrlFor,
                onCoverVisible = viewModel::ensureCoverCached,
                activityText = "Finished",
                onRatingSelected = { rating ->
                    viewModel.saveJournalEntry(
                        item = journalItem,
                        rating = rating,
                        reviewText = reviewState.reviewText,
                    )
                },
            ) {
                AppTextButton(onClick = dismissCompletion) {
                    Text(text = "Close")
                }
                AppButton(
                    onClick = {
                        viewModel.saveJournalEntry(
                            item = journalItem,
                            rating = reviewState.selectedRating,
                            reviewText = reviewState.reviewText,
                        )
                    },
                ) {
                    Text(text = "Save review")
                }
            }
        },
        onStartNext = {
            saveReviewIfNeeded()
            viewModel.startReaderCompletionNextItem()
        },
        onDismiss = dismissCompletion,
    )
}

private fun ReadableItem.toJournalItemRef(): JournalItemRef = JournalItemRef(
    itemId = id,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = title,
    itemPrimaryText = libraryPrimaryText(),
    itemSecondaryText = librarySecondaryText(),
    coverUrl = coverUrl,
    itemKind = kind,
    format = format,
)

@Composable
private fun BoxScope.ProgressSyncNoticeHost(
    notice: AppProgressSyncNotice?,
    onDismiss: (Long) -> Unit,
) {
    if (notice == null) return
    LaunchedEffect(notice.id) {
        delay(PROGRESS_SYNC_NOTICE_DURATION_MILLIS)
        onDismiss(notice.id)
    }
    AppSnackbar(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp),

        containerColor = if (notice.isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.inverseSurface
        },
        contentColor = if (notice.isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        },
    ) {
        Text(text = notice.message)
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

private const val UnsupportedImportSelectionMessage =
    "Choose an EPUB, PDF, CBZ, or CBR file to import."
private const val PROGRESS_SYNC_NOTICE_DURATION_MILLIS = 6000L
