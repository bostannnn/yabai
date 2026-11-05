package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppProgressBar
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton

/**
 * The "Library" group of a source page: syncing the catalogue and the cover cache. One button per
 * row (Sync now, Preload); the rest sit in the row's ••• (Fast sync, Remove cached covers), and
 * covers that failed get a row of their own with Retry.
 */
@Composable
internal fun SourceLibraryGroup(
    formState: ServerConnectionFormState,
    coverPreload: CoverPreloadUiState,
    actions: RemoteSourceSettingsActions,
    modifier: Modifier = Modifier,
    lastSyncedAtEpochMillis: Long? = null,
) {
    val actionState = buildServerConnectionActionState(formState)
    val syncRunning = actionState.activeAction != null && actionState.activeAction != ServerConnectionAction.Connect
    AppSettingsGroup(label = "Library", modifier = modifier) {
        AppSettingsRow(
            title = "Sync",
            supporting = when {
                // The step the sync is on, the bar under it (design system, AppProgressBar: a sync's bar).
                syncRunning -> formState.syncProgressLabel ?: "Syncing"
                !formState.hasStoredSession -> "Connect to the server first"
                lastSyncedAtEpochMillis != null -> "Synced ${formatSettingsMoment(lastSyncedAtEpochMillis)}"
                // Sync times are kept from 0.17.0 on, so an older install's library has synced before:
                // until the next sync, say what Sync does, not that it never ran.
                else -> "Gets new and changed books from the server"
            },
            supportingContent = if (syncRunning) {
                { RowProgressBar(fraction = formState.syncProgressFraction) }
            } else {
                null
            },
            trailing = {
                AppButton(
                    onClick = actions.onSync,
                    enabled = actionState.canSync,
                    modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                ) {
                    if (syncRunning) SettingsButtonProgressIndicator() else Text("Sync now")
                }
                actions.onFastSync?.let { fastSync ->
                    RowMoreMenu(contentDescription = "More ways to sync") { close ->
                        AppMenuItem(
                            text = { Text("Fast sync") },
                            enabled = actionState.canSync,
                            onClick = { close(); fastSync() },
                        )
                    }
                }
            },
        )
        AppSettingsRowDivider(inset = AppSettingsDefaults.dividerInsetNoIcon)
        AppSettingsRow(
            title = "Cached covers",
            supporting = coverPreload.progressLabel.takeIf { coverPreload.isRunning }
                ?: coverPreload.message?.takeUnless { coverPreload.isError }
                ?: "Cover images kept on this device for browsing offline",
            supportingContent = if (coverPreload.isRunning) {
                { RowProgressBar(fraction = coverPreload.progressFraction) }
            } else {
                null
            },
            trailing = {
                if (coverPreload.isRunning) {
                    AppTextButton(onClick = actions.onCancelCoverPreload) {
                        Text("Cancel")
                    }
                } else {
                    AppTonalButton(
                        onClick = actions.onPreloadCovers,
                        enabled = coverPreload.canStart && formState.hasStoredSession,
                        modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                    ) {
                        if (coverPreload.isClearingCache) SettingsButtonProgressIndicator() else Text("Preload")
                    }
                    RowMoreMenu(contentDescription = "More for cached covers") { close ->
                        AppMenuItem(
                            text = { Text("Remove cached covers") },
                            enabled = coverPreload.canClearCache,
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                            onClick = { close(); actions.onClearCoverCache() },
                        )
                    }
                }
            },
        )
        val coverProblem = coverPreload.message?.takeIf { coverPreload.isError && !coverPreload.isRunning }
        if (coverPreload.failedItems.isNotEmpty() || coverProblem != null) {
            AppSettingsRowDivider()
            FailedCoversRow(
                failedItems = coverPreload.failedItems,
                problem = coverProblem,
                canRetry = coverPreload.canRetryFailed,
                onRetry = actions.onRetryFailedCovers,
            )
        }
    }
}

/**
 * What went wrong with the covers, as one row: how many didn't download and the first titles, with
 * Retry. A tap shows each cover's reason under it.
 */
@Composable
private fun FailedCoversRow(
    failedItems: List<CoverPreloadFailureUiItem>,
    problem: String?,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hasItems = failedItems.isNotEmpty()
    AppSettingsRow(
        title = if (hasItems) failedCoversTitle(failedItems.size) else "Covers didn't download",
        icon = AppIcons.ErrorOutline,
        iconTint = MaterialTheme.colorScheme.error,
        supporting = if (hasItems) failedCoversLine(failedItems.map { it.title }) else problem,
        onClick = if (hasItems) ({ expanded = !expanded }) else null,
        showChevron = false,
        supportingContent = if (expanded && hasItems) {
            {
                Column(
                    modifier = Modifier.padding(top = AppLayout.headingGap),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    failedItems.forEach { failure ->
                        Text(
                            text = "${failure.title} (${failure.variant}): ${failure.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            null
        },
        trailing = if (hasItems) {
            {
                AppTextButton(onClick = onRetry, enabled = canRetry) {
                    Text("Retry")
                }
            }
        } else {
            null
        },
    )
}

/** "1 cover didn't download", "3 covers didn't download". */
internal fun failedCoversTitle(count: Int): String =
    if (count == 1) "1 cover didn't download" else "$count covers didn't download"

/** The first two titles, then how many more: "Star Courier #2, Iron Garden #3 and 1 more". */
internal fun failedCoversLine(titles: List<String>): String {
    val distinct = titles.distinct()
    val shown = distinct.take(2).joinToString(", ")
    val rest = distinct.size - 2
    return if (rest > 0) "$shown and $rest more" else shown
}

/** A row's ••• with the actions that don't earn a button of their own. */
@Composable
internal fun RowMoreMenu(
    contentDescription: String,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppIconButton(onClick = { open = true }) {
            Icon(imageVector = AppIcons.MoreVert, contentDescription = contentDescription)
        }
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

/** A long job's bar under its row's text, as the update download's in About; null is the moving bar. */
@Composable
private fun RowProgressBar(
    fraction: Float?,
) {
    AppProgressBar(
        fraction = fraction,
        height = 4.dp,
        modifier = Modifier
            .padding(top = 6.dp)
            .widthIn(max = 320.dp)
            .fillMaxWidth(),
    )
}
