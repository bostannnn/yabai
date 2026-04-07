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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryViewMode
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
    var libraryViewMode by rememberSaveable { mutableStateOf(LibraryViewMode.Detailed) }

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
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        MainTopBar(
                            selectedSource = uiState.selectedSource,
                            topLevel = uiState.topLevel,
                            libraryViewMode = libraryViewMode,
                            onSourceSelected = { source ->
                                viewModel.onTopLevelSelected(TopLevelDestination.Library)
                                viewModel.onSourceSelected(source)
                            },
                            onLibraryViewModeChanged = { libraryViewMode = it },
                            onSettingsClick = {
                                viewModel.onTopLevelSelected(TopLevelDestination.Settings)
                            },
                            onImportLocalFileClick = onImportLocalFileClick,
                        )
                        when (uiState.topLevel) {
                            TopLevelDestination.Library -> {
                                LibraryFlow(
                                    uiState = uiState,
                                    viewMode = libraryViewMode,
                                    onItemSelected = viewModel::onItemSelected,
                                    onDeleteImportedItem = viewModel::onDeleteImportedItem,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            TopLevelDestination.Settings -> {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
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
}

@Composable
private fun MainTopBar(
    selectedSource: SourceType,
    topLevel: TopLevelDestination,
    libraryViewMode: LibraryViewMode,
    onSourceSelected: (SourceType) -> Unit,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    onSettingsClick: () -> Unit,
    onImportLocalFileClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedSource.ordinal,
            modifier = Modifier.weight(1f),
            edgePadding = 0.dp,
        ) {
            SourceType.entries.forEach { source ->
                Tab(
                    selected = topLevel == TopLevelDestination.Library && source == selectedSource,
                    onClick = { onSourceSelected(source) },
                    text = { Text(text = source.displayName()) },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewModeButton(
                mode = LibraryViewMode.Detailed,
                selectedMode = libraryViewMode,
                onLibraryViewModeChanged = onLibraryViewModeChanged,
            )
            ViewModeButton(
                mode = LibraryViewMode.Grid,
                selectedMode = libraryViewMode,
                onLibraryViewModeChanged = onLibraryViewModeChanged,
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (topLevel == TopLevelDestination.Settings) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onImportLocalFileClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import local file",
                )
            }
        }
    }
}

@Composable
private fun ViewModeButton(
    mode: LibraryViewMode,
    selectedMode: LibraryViewMode,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
) {
    val isSelected = mode == selectedMode
    IconButton(onClick = { onLibraryViewModeChanged(mode) }) {
        Icon(
            imageVector = when (mode) {
                LibraryViewMode.Detailed -> Icons.AutoMirrored.Filled.ViewList
                LibraryViewMode.Grid -> Icons.Default.GridView
            },
            contentDescription = when (mode) {
                LibraryViewMode.Detailed -> "Detailed view"
                LibraryViewMode.Grid -> "Grid view"
            },
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun LibraryFlow(
    uiState: AppUiState,
    viewMode: LibraryViewMode,
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
            items = uiState.visibleItems,
            viewMode = viewMode,
            progressByItemId = uiState.progressByItemId,
            libraryMessage = uiState.libraryMessage,
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

private fun SourceType.displayName(): String = when (this) {
    SourceType.KAVITA -> "Kavita"
    SourceType.GRIMMORY -> "Grimmory"
    SourceType.LOCAL -> "Local"
}
