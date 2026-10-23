package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.navigation.DrawerOrderSettingsGroup
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.updates.AppUpdateActions
import com.example.unifiedreader.feature.updates.AppUpdateState
import com.example.unifiedreader.ui.components.AppScreenBack
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppScreenTransition
import com.example.unifiedreader.ui.components.currentAppWindowWidth

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
    updateState: AppUpdateState = AppUpdateState(),
    updateActions: AppUpdateActions = AppUpdateActions(),
    // For the top bar: whether the hub's own title shows (the bar then has no name), and whether
    // anything has scrolled under the bar (it then needs its glass).
    onTopBarStateChanged: (titleOnScreen: Boolean, scrolled: Boolean) -> Unit = { _, _ -> },
) {
    val usesSidePane = settingsUsesSidePane(currentAppWindowWidth())
    var hubTitleScrolled by remember { mutableStateOf(false) }
    var pageScrolled by remember { mutableStateOf(false) }
    val hubScroll = remember { SettingsScrollReporter(threshold = HubTitleScrollThreshold) { hubTitleScrolled = it } }
    val pageScroll = remember { SettingsScrollReporter(threshold = 0.dp) { pageScrolled = it } }
    val titleOnScreen = (usesSidePane || page == null) && !hubTitleScrolled
    val scrolled = hubTitleScrolled || if (usesSidePane) pageScrolled else page != null
    val latestOnTopBarStateChanged by rememberUpdatedState(onTopBarStateChanged)
    LaunchedEffect(titleOnScreen, scrolled) { latestOnTopBarStateChanged(titleOnScreen, scrolled) }
    val orderedSources = remember(sources, sourceOrder) {
        sources.sortedBy { entry -> sourceOrder.indexOf(entry.sourceType).takeIf { it >= 0 } ?: Int.MAX_VALUE }
    }
    val pageContent: @Composable (SettingsPage, Dp) -> Unit = { openPage, horizontalPadding ->
        CompositionLocalProvider(LocalSettingsScrollReporter provides pageScroll) {
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
                    onOpenNotice = { name -> onPageChanged(SettingsPage.License(name)) },
                    contentTopInset = contentTopInset,
                    horizontalPadding = horizontalPadding,
                )
                is SettingsPage.License -> LicenseNoticePage(
                    name = openPage.name,
                    contentTopInset = contentTopInset,
                    horizontalPadding = horizontalPadding,
                )
            }
        }
    }
    val hub: @Composable (SettingsPage?, Dp, Modifier) -> Unit = { selected, horizontalPadding, hubModifier ->
        CompositionLocalProvider(LocalSettingsScrollReporter provides hubScroll) {
            SettingsHub(
                themeMode = themeMode,
                sources = orderedSources,
                sourceOrder = sourceOrder,
                syncSettings = syncSettings,
                versionName = versionName,
                updateState = updateState,
                updateActions = updateActions,
                selectedPage = selected,
                onOpenPage = onPageChanged,
                contentTopInset = contentTopInset,
                horizontalPadding = horizontalPadding,
                modifier = hubModifier,
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
            hub(shownPage, 0.dp, Modifier.width(SettingsSidePaneWidthDp.dp).fillMaxHeight())
            AppScreenHost(
                screen = shownPage,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                // A license's notice steps back to the list beside the hub.
                back = shownPage.parent?.let { parent -> AppScreenBack(target = parent) { onPageChanged(parent) } },
            ) { openPage ->
                pageContent(openPage, 0.dp)
            }
        }
    } else {
        AppScreenHost(
            screen = page,
            modifier = modifier.fillMaxSize(),
            back = page?.let { open -> AppScreenBack<SettingsPage?>(target = open.parent) { onPageChanged(open.parent) } },
            transitionFor = { from, to ->
                when {
                    from == null || to?.parent == from -> AppScreenTransition.Forward
                    to == null || from.parent == to -> AppScreenTransition.Backward
                    else -> AppScreenTransition.FadeThrough
                }
            },
        ) { openPage ->
            if (openPage == null) {
                hub(null, SettingsScreenHorizontalPaddingDp.dp, Modifier)
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

/** Half the hub's title has gone under the bar. */
private val HubTitleScrollThreshold = 24.dp
