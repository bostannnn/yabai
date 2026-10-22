package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.navigation.DrawerOrderSettingsGroup
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppScreenBack
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppScreenTransition

internal const val DefaultKavitaServerUrl = "http://192.168.50.60:5000/"
internal const val DefaultKavitaBackupServerUrl = "http://100.84.32.88:5000/"
internal const val DefaultGrimmoryServerUrl = "http://192.168.50.60:6060/"
internal const val DefaultGrimmoryBackupServerUrl = "http://100.84.32.88:6060/"
internal const val DefaultStumpServerUrl = "http://192.168.50.60:10801/"
internal const val DefaultStumpBackupServerUrl = "http://100.84.32.88:10801/"

/**
 * The settings route: a hub of grouped rows and one page per thing that needs a form. On a
 * wide window the hub stays as a side column; on a narrow one the open [page] slides in over
 * it, the caller's top bar shows a back arrow, and back (or the predictive gesture) returns to
 * the hub.
 */
@Composable
fun SettingsScreen(
    page: SettingsPage?,
    onPageChanged: (SettingsPage?) -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    sources: List<SourceSettingsEntry>,
    sourceOrder: List<SourceType>,
    onSourceOrderChanged: (List<SourceType>) -> Unit,
    libraryFilterPanelsBySource: Map<SourceType, List<LibraryFilterPanel>>,
    onLibraryFilterPanelsChanged: (SourceType, List<LibraryFilterPanel>) -> Unit,
    syncSettings: SyncSettingsUiState,
    onRetrySync: () -> Unit,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onLibraryReaderModeSelected: (String, ComicReaderMode) -> Unit,
    versionName: String,
    contentTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val usesSidePane = settingsUsesSidePane(LocalConfiguration.current.screenWidthDp)
    val orderedSources = remember(sources, sourceOrder) {
        sources.sortedBy { entry -> sourceOrder.indexOf(entry.sourceType).takeIf { it >= 0 } ?: Int.MAX_VALUE }
    }
    val pageContent: @Composable (SettingsPage, Dp) -> Unit = { openPage, horizontalPadding ->
        when (openPage) {
            SettingsPage.Theme -> ThemeSettingsPage(
                themeMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
                contentTopInset = contentTopInset,
                horizontalPadding = horizontalPadding,
            )
            is SettingsPage.Source -> {
                val entry = orderedSources.firstOrNull { it.sourceType == openPage.sourceType }
                if (entry != null) {
                    SourceSettingsPage(
                        entry = entry,
                        onLibraryDirectionSelected = onLibraryDirectionSelected,
                        onLibraryReaderModeSelected = onLibraryReaderModeSelected,
                        contentTopInset = contentTopInset,
                        horizontalPadding = horizontalPadding,
                    )
                }
            }
            SettingsPage.FilterChips -> FilterChipsSettingsPage(
                filterPanelsBySource = libraryFilterPanelsBySource,
                onFilterPanelsChanged = onLibraryFilterPanelsChanged,
                contentTopInset = contentTopInset,
                horizontalPadding = horizontalPadding,
            )
            SettingsPage.DrawerOrder -> SettingsPageContainer(
                contentTopInset = contentTopInset,
                horizontalPadding = horizontalPadding,
            ) {
                DrawerOrderSettingsGroup(
                    sourceOrder = sourceOrder,
                    onSourceOrderChanged = onSourceOrderChanged,
                )
            }
            SettingsPage.SyncStatus -> SyncStatusSettingsPage(
                state = syncSettings,
                onRetrySync = onRetrySync,
                contentTopInset = contentTopInset,
                horizontalPadding = horizontalPadding,
            )
            SettingsPage.Licenses -> LicensesSettingsPage(
                contentTopInset = contentTopInset,
                horizontalPadding = horizontalPadding,
            )
        }
    }

    if (usesSidePane) {
        val shownPage = page ?: SettingsPage.Theme
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = SettingsScreenHorizontalPaddingDp.dp),
            horizontalArrangement = Arrangement.spacedBy(SettingsSidePaneSpacingDp.dp),
        ) {
            SettingsHub(
                themeMode = themeMode,
                sources = orderedSources,
                sourceOrder = sourceOrder,
                syncSettings = syncSettings,
                versionName = versionName,
                selectedPage = shownPage,
                onOpenPage = onPageChanged,
                contentTopInset = contentTopInset,
                horizontalPadding = 0.dp,
                modifier = Modifier
                    .width(SettingsSidePaneWidthDp.dp)
                    .fillMaxHeight(),
            )
            AppScreenHost(
                screen = shownPage,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { openPage ->
                pageContent(openPage, 0.dp)
            }
        }
    } else {
        AppScreenHost(
            screen = page,
            modifier = modifier.fillMaxSize(),
            back = page?.let { AppScreenBack<SettingsPage?>(target = null) { onPageChanged(null) } },
            transitionFor = { from, to ->
                when {
                    from == null -> AppScreenTransition.Forward
                    to == null -> AppScreenTransition.Backward
                    else -> AppScreenTransition.FadeThrough
                }
            },
        ) { openPage ->
            if (openPage == null) {
                SettingsHub(
                    themeMode = themeMode,
                    sources = orderedSources,
                    sourceOrder = sourceOrder,
                    syncSettings = syncSettings,
                    versionName = versionName,
                    selectedPage = null,
                    onOpenPage = onPageChanged,
                    contentTopInset = contentTopInset,
                )
            } else {
                pageContent(openPage, SettingsScreenHorizontalPaddingDp.dp)
            }
        }
    }
}

data class GrimmoryConnectionUiState(
    override val displayName: String = "Grimmory",
    override val baseUrl: String = DefaultGrimmoryServerUrl,
    override val backupBaseUrl: String = DefaultGrimmoryBackupServerUrl,
    override val username: String = "",
    override val password: String = "",
    override val hasStoredSession: Boolean = false,
    override val isConnecting: Boolean = false,
    override val activeAction: ServerConnectionAction? = null,
    override val syncProgressFraction: Float? = null,
    override val syncProgressLabel: String? = null,
    override val coverPreload: CoverPreloadUiState = CoverPreloadUiState(),
    override val message: String? = null,
    override val isError: Boolean = false,
) : RemoteSourceConnectionUiState {
    override val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

data class KavitaConnectionUiState(
    override val displayName: String = "Kavita",
    override val baseUrl: String = DefaultKavitaServerUrl,
    override val backupBaseUrl: String = DefaultKavitaBackupServerUrl,
    override val username: String = "",
    override val password: String = "",
    val availableLibraries: List<Library> = emptyList(),
    override val hasStoredSession: Boolean = false,
    override val isConnecting: Boolean = false,
    override val activeAction: ServerConnectionAction? = null,
    override val syncProgressFraction: Float? = null,
    override val syncProgressLabel: String? = null,
    override val coverPreload: CoverPreloadUiState = CoverPreloadUiState(),
    override val message: String? = null,
    override val isError: Boolean = false,
) : RemoteSourceConnectionUiState {
    override val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

data class StumpConnectionUiState(
    override val displayName: String = "Stump",
    override val baseUrl: String = DefaultStumpServerUrl,
    override val backupBaseUrl: String = DefaultStumpBackupServerUrl,
    override val username: String = "",
    override val password: String = "",
    val availableLibraries: List<Library> = emptyList(),
    override val hasStoredSession: Boolean = false,
    override val isConnecting: Boolean = false,
    override val activeAction: ServerConnectionAction? = null,
    override val syncProgressFraction: Float? = null,
    override val syncProgressLabel: String? = null,
    override val coverPreload: CoverPreloadUiState = CoverPreloadUiState(),
    override val message: String? = null,
    override val isError: Boolean = false,
) : RemoteSourceConnectionUiState {
    override val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}
