package com.example.unifiedreader

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.journal.JournalItemReviewCard
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.feature.journal.rememberJournalReviewState
import com.example.unifiedreader.feature.journal.shouldSaveJournalReview
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryViewMode
import com.example.unifiedreader.feature.library.ResolvedLibraryCover
import com.example.unifiedreader.feature.reader.core.ReaderCompletionDialog
import com.example.unifiedreader.feature.reader.core.ReaderOpenMode
import com.example.unifiedreader.feature.reader.core.ReaderHost
import com.example.unifiedreader.feature.reader.core.ReaderNavigationActions
import com.example.unifiedreader.ui.theme.UnifiedReaderTheme
import com.example.unifiedreader.ui.theme.applyAndroidThemeMode
import kotlinx.coroutines.delay

@Composable
fun UnifiedReaderRoute() {
    val viewModel: AppViewModel = hiltViewModel()
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
    )
    if (viewModel.isUnsupportedImportDialogVisible) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUnsupportedImportDialog,
            title = { Text(text = "Unsupported file") },
            text = { Text(text = UnsupportedImportSelectionMessage) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissUnsupportedImportDialog) {
                    Text(text = "OK")
                }
            },
        )
    }
}

@Composable
fun UnifiedReaderAppContent(
    viewModel: AppViewModel,
    onImportLocalFileClick: () -> Unit,
) {
    val uiState = viewModel.uiState
    val lifecycleOwner = LocalLifecycleOwner.current
    var libraryViewMode by rememberSaveable { mutableStateOf(LibraryViewMode.Grid) }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshProgress()
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushPendingRemoteProgress()
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
        BackHandler(
            enabled = isReaderMode ||
                uiState.selectedKavitaSeriesKey != null ||
                uiState.selectedStumpSeriesKey != null,
            onBack = viewModel::onBackPressed,
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isReaderMode) {
                    ReaderHost(
                        item = uiState.selectedItem!!,
                        factories = viewModel.readerFactories,
                        onBack = viewModel::onBackPressed,
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
                    )
                }
                ProgressSyncNoticeHost(
                    notice = uiState.progressSyncNotice,
                    onDismiss = viewModel::dismissProgressSyncNotice,
                )
                ReaderCompletionDialogHost(
                    uiState = uiState,
                    viewModel = viewModel,
                )
            }
        }
    }
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
            ResolvedLibraryCover(
                item = coverItem,
                coverImageUrlProvider = viewModel::coverImageUrlFor,
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
                TextButton(onClick = dismissCompletion) {
                    Text(text = "Close")
                }
                Button(
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
    Snackbar(
        modifier = Modifier
            .padding(16.dp)
            .align(Alignment.BottomCenter),
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
