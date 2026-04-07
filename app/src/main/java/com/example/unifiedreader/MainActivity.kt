package com.example.unifiedreader

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryScreen
import com.example.unifiedreader.feature.reader.core.ReaderHost
import com.example.unifiedreader.feature.settings.SettingsScreen
import com.example.unifiedreader.ui.theme.applyAndroidThemeMode
import com.example.unifiedreader.ui.theme.UnifiedReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnifiedReaderAppRoute()
        }
    }
}

@Composable
private fun UnifiedReaderAppRoute() {
    val viewModel: AppViewModel = hiltViewModel()
    val localFileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importDocumentIntoApp(
                contentResolver = viewModel.getApplication<Application>().contentResolver,
                viewModel = viewModel,
                uri = uri,
            )
        }
    }

    UnifiedReaderAppContent(
        viewModel = viewModel,
        onImportLocalFileClick = {
            localFileImportLauncher.launch(
                arrayOf(
                    "*/*",
                ),
            )
        },
    )
}

@Composable
fun UnifiedReaderAppContent(
    viewModel: AppViewModel,
    onImportLocalFileClick: () -> Unit,
) {
    val uiState = viewModel.uiState
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshProgress()
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (isReaderMode) {
                ReaderHost(
                    item = uiState.selectedItem!!,
                    factories = viewModel.readerFactories,
                    onBack = viewModel::onBackPressed,
                )
            } else {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            TopLevelDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = uiState.topLevel == destination,
                                    onClick = { viewModel.onTopLevelSelected(destination) },
                                    icon = {
                                        Text(text = destination.shortLabel)
                                    },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    when (uiState.topLevel) {
                        TopLevelDestination.Library -> {
                            LibraryFlow(
                                uiState = uiState,
                                onImportLocalFileClick = onImportLocalFileClick,
                                onSourceSelected = viewModel::onSourceSelected,
                                onItemSelected = viewModel::onItemSelected,
                                onDeleteImportedItem = viewModel::onDeleteImportedItem,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }

                        TopLevelDestination.Settings -> {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                SettingsScreen(
                                    themeMode = uiState.themeMode,
                                    onThemeModeSelected = viewModel::onThemeModeSelected,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFlow(
    uiState: AppUiState,
    onImportLocalFileClick: () -> Unit,
    onSourceSelected: (com.example.unifiedreader.core.model.SourceType) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LibraryScreen(
            selectedSource = uiState.selectedSource,
            libraries = uiState.visibleLibraries,
            items = uiState.visibleItems,
            progressByItemId = uiState.progressByItemId,
            libraryMessage = uiState.libraryMessage,
            onImportLocalFileClick = onImportLocalFileClick,
            onSourceSelected = onSourceSelected,
            onItemSelected = onItemSelected,
            onDeleteImportedItem = onDeleteImportedItem,
        )
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
