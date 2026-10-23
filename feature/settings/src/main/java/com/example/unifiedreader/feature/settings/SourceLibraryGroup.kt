package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppLinearProgress
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton

/** The "Library" group of a source page: syncing the catalogue and the cover cache. */
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
                syncRunning -> "Syncing"
                !formState.hasStoredSession -> "Connect to the server first"
                lastSyncedAtEpochMillis != null -> "Synced ${formatSettingsMoment(lastSyncedAtEpochMillis)}"
                // Sync times are kept from 0.17.0 on, so an older install's library has synced before:
                // until the next sync, say what Sync does, not that it never ran.
                else -> "Gets new and changed books from the server"
            },
            trailing = {
                val fastSync = actions.onFastSync
                if (fastSync != null) {
                    AppTonalButton(
                        onClick = fastSync,
                        enabled = actionState.canSync,
                        modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                    ) {
                        if (actionState.activeAction == ServerConnectionAction.FastSync) {
                            SettingsButtonProgressIndicator()
                        } else {
                            Text("Fast sync")
                        }
                    }
                }
                AppButton(
                    onClick = actions.onSync,
                    enabled = actionState.canSync,
                    modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                ) {
                    if (actionState.activeAction == ServerConnectionAction.Sync ||
                        actionState.activeAction == ServerConnectionAction.FullSync
                    ) {
                        SettingsButtonProgressIndicator()
                    } else {
                        Text("Sync now")
                    }
                }
            },
        )
        if (syncRunning && formState.syncProgressLabel != null) {
            ProgressFooter(
                fraction = formState.syncProgressFraction,
                label = formState.syncProgressLabel,
            )
        }
        AppSettingsRowDivider(inset = AppSettingsDefaults.dividerInsetNoIcon)
        AppSettingsRow(
            title = "Cached covers",
            supporting = coverPreload.message ?: "Cover images kept on this device for browsing offline",
            supportingColor = if (coverPreload.isError && coverPreload.message != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            trailing = {
                if (coverPreload.isRunning) {
                    AppTextButton(onClick = actions.onCancelCoverPreload) {
                        Text("Cancel")
                    }
                } else {
                    AppTonalButton(
                        onClick = actions.onClearCoverCache,
                        enabled = coverPreload.canClearCache,
                        modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                    ) {
                        if (coverPreload.isClearingCache) {
                            SettingsButtonProgressIndicator()
                        } else {
                            Text("Remove")
                        }
                    }
                    if (coverPreload.canRetryFailed) {
                        AppTonalButton(
                            onClick = actions.onRetryFailedCovers,
                            modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                        ) {
                            Text("Retry failed")
                        }
                    }
                    AppTonalButton(
                        onClick = actions.onPreloadCovers,
                        enabled = coverPreload.canStart && formState.hasStoredSession,
                        modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                    ) {
                        Text("Preload")
                    }
                }
            },
        )
        if (coverPreload.isRunning && coverPreload.progressLabel != null) {
            ProgressFooter(
                fraction = coverPreload.progressFraction,
                label = coverPreload.progressLabel,
            )
        }
        if (coverPreload.failedItems.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(
                    start = AppSettingsDefaults.horizontalPadding,
                    end = AppSettingsDefaults.horizontalPadding,
                    bottom = 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                coverPreload.failedItems.forEach { failure ->
                    Text(
                        text = "${failure.title} (${failure.variant}): ${failure.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressFooter(
    fraction: Float?,
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppSettingsDefaults.horizontalPadding,
                end = AppSettingsDefaults.horizontalPadding,
                bottom = 14.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLinearProgress(
            progress = { fraction ?: 0f },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
