package com.example.unifiedreader.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.navigation.navigationIcon
import com.example.unifiedreader.feature.updates.AppUpdateActions
import com.example.unifiedreader.feature.updates.AppUpdateAutoCheckRow
import com.example.unifiedreader.feature.updates.AppUpdateState
import com.example.unifiedreader.feature.updates.AppUpdateVersionRow
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider

/** The settings front page: grouped rows that show their current value and open a page. */
@Composable
internal fun SettingsHub(
    themeMode: ThemeMode,
    sources: List<SourceSettingsEntry>,
    sourceOrder: List<SourceType>,
    syncSettings: SyncSettingsUiState,
    versionName: String,
    updateState: AppUpdateState,
    updateActions: AppUpdateActions,
    selectedPage: SettingsPage?,
    onOpenPage: (SettingsPage) -> Unit,
    contentTopInset: Dp,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = SettingsScreenHorizontalPaddingDp.dp,
) {
    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        AppSettingsGroup(label = "Appearance") {
            AppSettingsRow(
                title = "Theme",
                icon = AppIcons.Palette,
                supporting = themeMode.label,
                selected = selectedPage == SettingsPage.Theme,
                onClick = { onOpenPage(SettingsPage.Theme) },
                trailing = { ThemeSwatchDots(swatches = themeMode.previewSwatches) },
            )
        }
        AppSettingsGroup(label = "Sources") {
            sources.forEachIndexed { index, entry ->
                if (index > 0) AppSettingsRowDivider()
                val page = SettingsPage.Source(entry.sourceType)
                AppSettingsRow(
                    title = entry.sourceType.displayName(),
                    icon = entry.sourceType.navigationIcon(),
                    supporting = remoteSourceStatusLabel(entry.connection, entry.connectivity),
                    selected = selectedPage == page,
                    onClick = { onOpenPage(page) },
                )
            }
        }
        AppSettingsGroup(label = "Library") {
            AppSettingsRow(
                title = SettingsPage.FilterChips.title,
                icon = AppIcons.FilterList,
                supporting = "Which filters each library shows, and in what order",
                selected = selectedPage == SettingsPage.FilterChips,
                onClick = { onOpenPage(SettingsPage.FilterChips) },
            )
            AppSettingsRowDivider()
            AppSettingsRow(
                title = SettingsPage.DrawerOrder.title,
                icon = AppIcons.SwapVert,
                supporting = drawerOrderSummary(sourceOrder),
                selected = selectedPage == SettingsPage.DrawerOrder,
                onClick = { onOpenPage(SettingsPage.DrawerOrder) },
            )
            AppSettingsRowDivider()
            AppSettingsRow(
                title = SettingsPage.SyncStatus.title,
                icon = AppIcons.Refresh,
                supporting = syncSettings.summaryLabel(),
                supportingColor = if (syncSettings.failedCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                selected = selectedPage == SettingsPage.SyncStatus,
                onClick = { onOpenPage(SettingsPage.SyncStatus) },
            )
        }
        AppSettingsGroup(label = "About") {
            AppUpdateVersionRow(
                versionName = versionName,
                state = updateState,
                actions = updateActions,
            )
            AppSettingsRowDivider()
            AppUpdateAutoCheckRow(
                state = updateState,
                onCheckedChange = updateActions.onAutoCheckChanged,
            )
            AppSettingsRowDivider(inset = AppSettingsDefaults.dividerInset)
            AppSettingsRow(
                title = SettingsPage.Licenses.title,
                icon = AppIcons.Description,
                selected = selectedPage == SettingsPage.Licenses,
                onClick = { onOpenPage(SettingsPage.Licenses) },
            )
        }
    }
}

internal fun drawerOrderSummary(sourceOrder: List<SourceType>): String {
    val first = sourceOrder.firstOrNull() ?: return "Sources in the drawer"
    return "${first.displayName()} opens on launch"
}
