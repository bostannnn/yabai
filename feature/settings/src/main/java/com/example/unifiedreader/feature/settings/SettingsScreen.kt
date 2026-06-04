package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.settings.direction.ComicReadingDirectionSettingsSection
import com.example.unifiedreader.feature.settings.direction.ReadingDirectionLibraryUiState
import com.example.unifiedreader.feature.settings.mode.ComicReaderModeSettingsSection
import com.example.unifiedreader.feature.settings.mode.ReaderModeLibraryUiState
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.UnifiedReaderSurfaceTreatment
import com.example.unifiedreader.ui.theme.unifiedReaderContentOverlay

internal const val DefaultKavitaServerUrl = "http://192.168.50.60:5000/"
internal const val DefaultKavitaBackupServerUrl = "http://100.84.32.88:5000/"
internal const val DefaultGrimmoryServerUrl = "http://192.168.50.60:6060/"
internal const val DefaultGrimmoryBackupServerUrl = "http://100.84.32.88:6060/"
internal const val DefaultStumpServerUrl = "http://192.168.50.60:10801/"
internal const val DefaultStumpBackupServerUrl = "http://100.84.32.88:10801/"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    libraryFilterPanelsBySource: Map<SourceType, List<LibraryFilterPanel>>,
    kavitaReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    stumpReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    kavitaReaderModeLibraries: List<ReaderModeLibraryUiState>,
    stumpReaderModeLibraries: List<ReaderModeLibraryUiState>,
    kavitaConnection: KavitaConnectionUiState,
    stumpConnection: StumpConnectionUiState,
    grimmoryConnection: GrimmoryConnectionUiState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLibraryFilterPanelsChanged: (SourceType, List<LibraryFilterPanel>) -> Unit,
    supplementarySection: SettingsSupplementarySection? = null,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onLibraryReaderModeSelected: (String, ComicReaderMode) -> Unit,
    onKavitaDisplayNameChanged: (String) -> Unit,
    onKavitaBaseUrlChanged: (String) -> Unit,
    onKavitaBackupBaseUrlChanged: (String) -> Unit,
    onKavitaUsernameChanged: (String) -> Unit,
    onKavitaPasswordChanged: (String) -> Unit,
    onConnectKavita: () -> Unit,
    onSyncKavita: () -> Unit,
    onFastSyncKavita: () -> Unit,
    onPreloadKavitaCovers: () -> Unit,
    onRetryFailedKavitaCovers: () -> Unit,
    onCancelKavitaCoverPreload: () -> Unit,
    onClearKavitaCoverCache: () -> Unit,
    onStumpDisplayNameChanged: (String) -> Unit,
    onStumpBaseUrlChanged: (String) -> Unit,
    onStumpBackupBaseUrlChanged: (String) -> Unit,
    onStumpUsernameChanged: (String) -> Unit,
    onStumpPasswordChanged: (String) -> Unit,
    onConnectStump: () -> Unit,
    onSyncStump: () -> Unit,
    onPreloadStumpCovers: () -> Unit,
    onRetryFailedStumpCovers: () -> Unit,
    onCancelStumpCoverPreload: () -> Unit,
    onClearStumpCoverCache: () -> Unit,
    onGrimmoryDisplayNameChanged: (String) -> Unit,
    onGrimmoryBaseUrlChanged: (String) -> Unit,
    onGrimmoryBackupBaseUrlChanged: (String) -> Unit,
    onGrimmoryUsernameChanged: (String) -> Unit,
    onGrimmoryPasswordChanged: (String) -> Unit,
    onConnectGrimmory: () -> Unit,
    onSyncGrimmory: () -> Unit,
    onPreloadGrimmoryCovers: () -> Unit,
    onRetryFailedGrimmoryCovers: () -> Unit,
    onCancelGrimmoryCoverPreload: () -> Unit,
    onClearGrimmoryCoverCache: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val overviewContentWidthDp = resolveSettingsOverviewContentWidth(maxWidth.value.toInt())
        val useListDetail = shouldUseSettingsListDetail(overviewContentWidthDp)
        val useOverviewSingleLaneCards =
            overviewContentWidthDp >= (SettingsOverviewMinColumnWidthDp * 2) + SettingsOverviewColumnSpacingDp
        val sections = buildSettingsSectionDescriptors(supplementarySection)
        var selectedSectionId by rememberSaveable {
            mutableStateOf(sections.first().id)
        }

        LaunchedEffect(sections) {
            if (sections.none { section -> section.id == selectedSectionId }) {
                selectedSectionId = sections.first().id
            }
        }

        if (useListDetail) {
            SettingsListDetailLayout(
                sections = sections,
                selectedSectionId = selectedSectionId,
                onSectionSelected = { sectionId -> selectedSectionId = sectionId },
                header = {
                    SettingsHeader()
                },
                detailContent = { sectionId ->
                    when (sectionId) {
                        SettingsSectionId.Theme -> ThemeSettingsCard(
                            themeMode = themeMode,
                            onThemeModeSelected = onThemeModeSelected,
                        )
                        SettingsSectionId.Supplementary -> supplementarySection?.content?.invoke()
                        SettingsSectionId.LibraryFilters -> LibraryFilterSettingsSection(
                            filterPanelsBySource = libraryFilterPanelsBySource,
                            onFilterPanelsChanged = onLibraryFilterPanelsChanged,
                        )
                        SettingsSectionId.Kavita -> KavitaSettingsCard(
                            kavitaConnection = kavitaConnection,
                            kavitaReadingDirectionLibraries = kavitaReadingDirectionLibraries,
                            kavitaReaderModeLibraries = kavitaReaderModeLibraries,
                            onKavitaDisplayNameChanged = onKavitaDisplayNameChanged,
                            onKavitaBaseUrlChanged = onKavitaBaseUrlChanged,
                            onKavitaBackupBaseUrlChanged = onKavitaBackupBaseUrlChanged,
                            onKavitaUsernameChanged = onKavitaUsernameChanged,
                            onKavitaPasswordChanged = onKavitaPasswordChanged,
                            onConnectKavita = onConnectKavita,
                            onSyncKavita = onSyncKavita,
                            onFastSyncKavita = onFastSyncKavita,
                            onPreloadKavitaCovers = onPreloadKavitaCovers,
                            onRetryFailedKavitaCovers = onRetryFailedKavitaCovers,
                            onCancelKavitaCoverPreload = onCancelKavitaCoverPreload,
                            onClearKavitaCoverCache = onClearKavitaCoverCache,
                            onLibraryDirectionSelected = onLibraryDirectionSelected,
                            onLibraryReaderModeSelected = onLibraryReaderModeSelected,
                        )
                        SettingsSectionId.Grimmory -> GrimmorySettingsCard(
                            grimmoryConnection = grimmoryConnection,
                            onGrimmoryDisplayNameChanged = onGrimmoryDisplayNameChanged,
                            onGrimmoryBaseUrlChanged = onGrimmoryBaseUrlChanged,
                            onGrimmoryBackupBaseUrlChanged = onGrimmoryBackupBaseUrlChanged,
                            onGrimmoryUsernameChanged = onGrimmoryUsernameChanged,
                            onGrimmoryPasswordChanged = onGrimmoryPasswordChanged,
                            onConnectGrimmory = onConnectGrimmory,
                            onSyncGrimmory = onSyncGrimmory,
                            onPreloadGrimmoryCovers = onPreloadGrimmoryCovers,
                            onRetryFailedGrimmoryCovers = onRetryFailedGrimmoryCovers,
                            onCancelGrimmoryCoverPreload = onCancelGrimmoryCoverPreload,
                            onClearGrimmoryCoverCache = onClearGrimmoryCoverCache,
                        )
                        SettingsSectionId.Stump -> StumpSettingsCard(
                            stumpConnection = stumpConnection,
                            stumpReadingDirectionLibraries = stumpReadingDirectionLibraries,
                            stumpReaderModeLibraries = stumpReaderModeLibraries,
                            onStumpDisplayNameChanged = onStumpDisplayNameChanged,
                            onStumpBaseUrlChanged = onStumpBaseUrlChanged,
                            onStumpBackupBaseUrlChanged = onStumpBackupBaseUrlChanged,
                            onStumpUsernameChanged = onStumpUsernameChanged,
                            onStumpPasswordChanged = onStumpPasswordChanged,
                            onConnectStump = onConnectStump,
                            onSyncStump = onSyncStump,
                            onPreloadStumpCovers = onPreloadStumpCovers,
                            onRetryFailedStumpCovers = onRetryFailedStumpCovers,
                            onCancelStumpCoverPreload = onCancelStumpCoverPreload,
                            onClearStumpCoverCache = onClearStumpCoverCache,
                            onLibraryDirectionSelected = onLibraryDirectionSelected,
                            onLibraryReaderModeSelected = onLibraryReaderModeSelected,
                        )
                    }
                },
            )
        } else {
            SettingsOverviewGrid(
                useSingleLaneCards = useOverviewSingleLaneCards,
                header = {
                    SettingsHeader()
                },
                themeCard = {
                    ThemeSettingsCard(
                        themeMode = themeMode,
                        onThemeModeSelected = onThemeModeSelected,
                    )
                },
                supplementarySection = supplementarySection,
                libraryFiltersCard = {
                    LibraryFilterSettingsSection(
                        filterPanelsBySource = libraryFilterPanelsBySource,
                        onFilterPanelsChanged = onLibraryFilterPanelsChanged,
                    )
                },
                kavitaCard = {
                    KavitaSettingsCard(
                        kavitaConnection = kavitaConnection,
                        kavitaReadingDirectionLibraries = kavitaReadingDirectionLibraries,
                        kavitaReaderModeLibraries = kavitaReaderModeLibraries,
                        onKavitaDisplayNameChanged = onKavitaDisplayNameChanged,
                        onKavitaBaseUrlChanged = onKavitaBaseUrlChanged,
                        onKavitaBackupBaseUrlChanged = onKavitaBackupBaseUrlChanged,
                        onKavitaUsernameChanged = onKavitaUsernameChanged,
                        onKavitaPasswordChanged = onKavitaPasswordChanged,
                        onConnectKavita = onConnectKavita,
                        onSyncKavita = onSyncKavita,
                        onFastSyncKavita = onFastSyncKavita,
                        onPreloadKavitaCovers = onPreloadKavitaCovers,
                        onRetryFailedKavitaCovers = onRetryFailedKavitaCovers,
                        onCancelKavitaCoverPreload = onCancelKavitaCoverPreload,
                        onClearKavitaCoverCache = onClearKavitaCoverCache,
                        onLibraryDirectionSelected = onLibraryDirectionSelected,
                        onLibraryReaderModeSelected = onLibraryReaderModeSelected,
                    )
                },
                grimmoryCard = {
                    GrimmorySettingsCard(
                        grimmoryConnection = grimmoryConnection,
                        onGrimmoryDisplayNameChanged = onGrimmoryDisplayNameChanged,
                        onGrimmoryBaseUrlChanged = onGrimmoryBaseUrlChanged,
                        onGrimmoryBackupBaseUrlChanged = onGrimmoryBackupBaseUrlChanged,
                        onGrimmoryUsernameChanged = onGrimmoryUsernameChanged,
                        onGrimmoryPasswordChanged = onGrimmoryPasswordChanged,
                        onConnectGrimmory = onConnectGrimmory,
                        onSyncGrimmory = onSyncGrimmory,
                        onPreloadGrimmoryCovers = onPreloadGrimmoryCovers,
                        onRetryFailedGrimmoryCovers = onRetryFailedGrimmoryCovers,
                        onCancelGrimmoryCoverPreload = onCancelGrimmoryCoverPreload,
                        onClearGrimmoryCoverCache = onClearGrimmoryCoverCache,
                    )
                },
                stumpCard = {
                    StumpSettingsCard(
                        stumpConnection = stumpConnection,
                        stumpReadingDirectionLibraries = stumpReadingDirectionLibraries,
                        stumpReaderModeLibraries = stumpReaderModeLibraries,
                        onStumpDisplayNameChanged = onStumpDisplayNameChanged,
                        onStumpBaseUrlChanged = onStumpBaseUrlChanged,
                        onStumpBackupBaseUrlChanged = onStumpBackupBaseUrlChanged,
                        onStumpUsernameChanged = onStumpUsernameChanged,
                        onStumpPasswordChanged = onStumpPasswordChanged,
                        onConnectStump = onConnectStump,
                        onSyncStump = onSyncStump,
                        onPreloadStumpCovers = onPreloadStumpCovers,
                        onRetryFailedStumpCovers = onRetryFailedStumpCovers,
                        onCancelStumpCoverPreload = onCancelStumpCoverPreload,
                        onClearStumpCoverCache = onClearStumpCoverCache,
                        onLibraryDirectionSelected = onLibraryDirectionSelected,
                        onLibraryReaderModeSelected = onLibraryReaderModeSelected,
                    )
                },
            )
        }
    }
}

@Composable
private fun KavitaSettingsCard(
    kavitaConnection: KavitaConnectionUiState,
    kavitaReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    kavitaReaderModeLibraries: List<ReaderModeLibraryUiState>,
    onKavitaDisplayNameChanged: (String) -> Unit,
    onKavitaBaseUrlChanged: (String) -> Unit,
    onKavitaBackupBaseUrlChanged: (String) -> Unit,
    onKavitaUsernameChanged: (String) -> Unit,
    onKavitaPasswordChanged: (String) -> Unit,
    onConnectKavita: () -> Unit,
    onSyncKavita: () -> Unit,
    onFastSyncKavita: () -> Unit,
    onPreloadKavitaCovers: () -> Unit,
    onRetryFailedKavitaCovers: () -> Unit,
    onCancelKavitaCoverPreload: () -> Unit,
    onClearKavitaCoverCache: () -> Unit,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onLibraryReaderModeSelected: (String, ComicReaderMode) -> Unit,
) {
    RemoteSourceSettingsCard(
        title = "Kavita",
        body = "Connect a Kavita server and sync comic libraries into the Kavita tab.",
        formState = kavitaConnection.toFormState(),
        connectLabel = "Connect Kavita",
        syncLabel = "Sync Kavita",
        secondarySyncLabel = "Fast Sync",
        onDisplayNameChanged = onKavitaDisplayNameChanged,
        onBaseUrlChanged = onKavitaBaseUrlChanged,
        onBackupBaseUrlChanged = onKavitaBackupBaseUrlChanged,
        onUsernameChanged = onKavitaUsernameChanged,
        onPasswordChanged = onKavitaPasswordChanged,
        onConnect = onConnectKavita,
        onSync = onSyncKavita,
        onSecondarySync = onFastSyncKavita,
        coverPreloadState = kavitaConnection.coverPreload,
        preloadCoversLabel = "Preload Kavita covers",
        onPreloadCovers = onPreloadKavitaCovers,
        onRetryFailedCovers = onRetryFailedKavitaCovers,
        onCancelPreload = onCancelKavitaCoverPreload,
        onClearCachedCovers = onClearKavitaCoverCache,
        readingDirectionLibraries = kavitaReadingDirectionLibraries,
        readerModeLibraries = kavitaReaderModeLibraries,
        onLibraryDirectionSelected = onLibraryDirectionSelected,
        onLibraryModeSelected = onLibraryReaderModeSelected,
    )
}

@Composable
private fun GrimmorySettingsCard(
    grimmoryConnection: GrimmoryConnectionUiState,
    onGrimmoryDisplayNameChanged: (String) -> Unit,
    onGrimmoryBaseUrlChanged: (String) -> Unit,
    onGrimmoryBackupBaseUrlChanged: (String) -> Unit,
    onGrimmoryUsernameChanged: (String) -> Unit,
    onGrimmoryPasswordChanged: (String) -> Unit,
    onConnectGrimmory: () -> Unit,
    onSyncGrimmory: () -> Unit,
    onPreloadGrimmoryCovers: () -> Unit,
    onRetryFailedGrimmoryCovers: () -> Unit,
    onCancelGrimmoryCoverPreload: () -> Unit,
    onClearGrimmoryCoverCache: () -> Unit,
) {
    RemoteSourceSettingsCard(
        title = "Grimmory",
        body = "Connect a Grimmory server and sync book libraries into the Grimmory tab.",
        formState = grimmoryConnection.toFormState(),
        connectLabel = "Connect Grimmory",
        syncLabel = "Sync Grimmory",
        onDisplayNameChanged = onGrimmoryDisplayNameChanged,
        onBaseUrlChanged = onGrimmoryBaseUrlChanged,
        onBackupBaseUrlChanged = onGrimmoryBackupBaseUrlChanged,
        onUsernameChanged = onGrimmoryUsernameChanged,
        onPasswordChanged = onGrimmoryPasswordChanged,
        onConnect = onConnectGrimmory,
        onSync = onSyncGrimmory,
        coverPreloadState = grimmoryConnection.coverPreload,
        preloadCoversLabel = "Preload Grimmory covers",
        onPreloadCovers = onPreloadGrimmoryCovers,
        onRetryFailedCovers = onRetryFailedGrimmoryCovers,
        onCancelPreload = onCancelGrimmoryCoverPreload,
        onClearCachedCovers = onClearGrimmoryCoverCache,
    )
}

@Composable
private fun StumpSettingsCard(
    stumpConnection: StumpConnectionUiState,
    stumpReadingDirectionLibraries: List<ReadingDirectionLibraryUiState>,
    stumpReaderModeLibraries: List<ReaderModeLibraryUiState>,
    onStumpDisplayNameChanged: (String) -> Unit,
    onStumpBaseUrlChanged: (String) -> Unit,
    onStumpBackupBaseUrlChanged: (String) -> Unit,
    onStumpUsernameChanged: (String) -> Unit,
    onStumpPasswordChanged: (String) -> Unit,
    onConnectStump: () -> Unit,
    onSyncStump: () -> Unit,
    onPreloadStumpCovers: () -> Unit,
    onRetryFailedStumpCovers: () -> Unit,
    onCancelStumpCoverPreload: () -> Unit,
    onClearStumpCoverCache: () -> Unit,
    onLibraryDirectionSelected: (String, ComicReadingDirection) -> Unit,
    onLibraryReaderModeSelected: (String, ComicReaderMode) -> Unit,
) {
    RemoteSourceSettingsCard(
        title = "Stump",
        body = "Connect a Stump server and sync comics or manga grouped by series into the Stump tab.",
        formState = stumpConnection.toFormState(),
        connectLabel = "Connect Stump",
        syncLabel = "Sync Stump",
        onDisplayNameChanged = onStumpDisplayNameChanged,
        onBaseUrlChanged = onStumpBaseUrlChanged,
        onBackupBaseUrlChanged = onStumpBackupBaseUrlChanged,
        onUsernameChanged = onStumpUsernameChanged,
        onPasswordChanged = onStumpPasswordChanged,
        onConnect = onConnectStump,
        onSync = onSyncStump,
        coverPreloadState = stumpConnection.coverPreload,
        preloadCoversLabel = "Preload Stump covers",
        onPreloadCovers = onPreloadStumpCovers,
        onRetryFailedCovers = onRetryFailedStumpCovers,
        onCancelPreload = onCancelStumpCoverPreload,
        onClearCachedCovers = onClearStumpCoverCache,
        readingDirectionLibraries = stumpReadingDirectionLibraries,
        readerModeLibraries = stumpReaderModeLibraries,
        onLibraryDirectionSelected = onLibraryDirectionSelected,
        onLibraryModeSelected = onLibraryReaderModeSelected,
    )
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Appearance is the first persisted app setting. It updates the full Material 3 shell immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ThemeSettingsCard(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    SettingsCard(
        title = "Theme",
        body = "Choose how the app chrome should render across the library and settings surfaces.",
    ) {
        val themeOptions = if (themeMode.isAvailableInSettings) {
            ThemeMode.settingsEntries
        } else {
            ThemeMode.settingsEntries + themeMode
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            themeOptions.forEach { option ->
                ThemeModePreviewChip(
                    option = option,
                    selected = option == themeMode,
                    onClick = { onThemeModeSelected(option) },
                )
            }
        }
        Text(
            text = themeMode.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeModePreviewChip(
    option: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(presentation.chipCornerRadius),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = semanticColors.surfaceSelected,
            selectedLabelColor = semanticColors.onSurfaceSelected,
        ),
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeModeSwatches(
                    swatches = option.previewSwatches,
                    treatment = option.previewSurfaceTreatment(),
                )
                Text(text = option.label)
            }
        },
    )
}

@Composable
private fun ThemeModeSwatches(
    swatches: ThemePreviewSwatches,
    treatment: UnifiedReaderSurfaceTreatment,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(swatches.surface, swatches.reader, swatches.sync, swatches.source).forEach { color ->
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(color))
                    .unifiedReaderContentOverlay(
                        treatment = treatment,
                        accentColor = Color(swatches.reader),
                    )
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

private fun ThemeMode.previewSurfaceTreatment(): UnifiedReaderSurfaceTreatment = when (this) {
    ThemeMode.Terminal95 -> UnifiedReaderSurfaceTreatment.TerminalGrid
    ThemeMode.CrimsonCourt -> UnifiedReaderSurfaceTreatment.GothicVignette
    ThemeMode.PanelPop -> UnifiedReaderSurfaceTreatment.ComicHalftone
    ThemeMode.Blueprint -> UnifiedReaderSurfaceTreatment.BlueprintGrid
    ThemeMode.VhsNight -> UnifiedReaderSurfaceTreatment.VhsScanlines
    ThemeMode.DetectiveFile -> UnifiedReaderSurfaceTreatment.DossierPaper
    else -> UnifiedReaderSurfaceTreatment.Plain
}

data class GrimmoryConnectionUiState(
    val displayName: String = "Grimmory",
    val baseUrl: String = DefaultGrimmoryServerUrl,
    val backupBaseUrl: String = DefaultGrimmoryBackupServerUrl,
    val username: String = "",
    val password: String = "",
    val hasStoredSession: Boolean = false,
    val isConnecting: Boolean = false,
    val activeAction: ServerConnectionAction? = null,
    val syncProgressFraction: Float? = null,
    val syncProgressLabel: String? = null,
    val coverPreload: CoverPreloadUiState = CoverPreloadUiState(),
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

data class KavitaConnectionUiState(
    val displayName: String = "Kavita",
    val baseUrl: String = DefaultKavitaServerUrl,
    val backupBaseUrl: String = DefaultKavitaBackupServerUrl,
    val username: String = "",
    val password: String = "",
    val availableLibraries: List<Library> = emptyList(),
    val hasStoredSession: Boolean = false,
    val isConnecting: Boolean = false,
    val activeAction: ServerConnectionAction? = null,
    val syncProgressFraction: Float? = null,
    val syncProgressLabel: String? = null,
    val coverPreload: CoverPreloadUiState = CoverPreloadUiState(),
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

data class StumpConnectionUiState(
    val displayName: String = "Stump",
    val baseUrl: String = DefaultStumpServerUrl,
    val backupBaseUrl: String = DefaultStumpBackupServerUrl,
    val username: String = "",
    val password: String = "",
    val availableLibraries: List<Library> = emptyList(),
    val hasStoredSession: Boolean = false,
    val isConnecting: Boolean = false,
    val activeAction: ServerConnectionAction? = null,
    val syncProgressFraction: Float? = null,
    val syncProgressLabel: String? = null,
    val coverPreload: CoverPreloadUiState = CoverPreloadUiState(),
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isConnecting &&
            displayName.isNotBlank() &&
            baseUrl.isNotBlank() &&
            username.isNotBlank() &&
            (password.isNotBlank() || hasStoredSession)
}

private fun GrimmoryConnectionUiState.toFormState(): ServerConnectionFormState = ServerConnectionFormState(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    username = username,
    password = password,
    hasStoredSession = hasStoredSession,
    isConnecting = isConnecting,
    activeAction = activeAction,
    syncProgressFraction = syncProgressFraction,
    syncProgressLabel = syncProgressLabel,
    message = message,
    isError = isError,
    canConnect = canSubmit,
)

private fun KavitaConnectionUiState.toFormState(): ServerConnectionFormState = ServerConnectionFormState(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    username = username,
    password = password,
    hasStoredSession = hasStoredSession,
    isConnecting = isConnecting,
    activeAction = activeAction,
    syncProgressFraction = syncProgressFraction,
    syncProgressLabel = syncProgressLabel,
    message = message,
    isError = isError,
    canConnect = canSubmit,
)

private fun StumpConnectionUiState.toFormState(): ServerConnectionFormState = ServerConnectionFormState(
    displayName = displayName,
    baseUrl = baseUrl,
    backupBaseUrl = backupBaseUrl,
    username = username,
    password = password,
    hasStoredSession = hasStoredSession,
    isConnecting = isConnecting,
    activeAction = activeAction,
    syncProgressFraction = syncProgressFraction,
    syncProgressLabel = syncProgressLabel,
    message = message,
    isError = isError,
    canConnect = canSubmit,
)
