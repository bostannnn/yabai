package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppDivider

data class SyncSettingsUiState(
    val pendingCount: Int = 0,
    val queuedCount: Int = 0,
    val failedCount: Int = 0,
    val sourceGroups: List<SyncSettingsSourceGroup> = emptyList(),
) {
    val hasPending: Boolean
        get() = pendingCount > 0
}

data class SyncSettingsSourceGroup(
    val sourceLabel: String,
    val stateLabel: String,
    val queuedCount: Int,
    val failedCount: Int,
    val lastAttemptLabel: String? = null,
    val lastError: String? = null,
    val items: List<SyncSettingsItemRow> = emptyList(),
)

data class SyncSettingsItemRow(
    val title: String,
    val subtitle: String? = null,
    val artifactLabel: String,
    val stateLabel: String,
    val updatedAtLabel: String? = null,
)

@Composable
internal fun SyncSettingsCard(
    state: SyncSettingsUiState,
    onRetrySync: () -> Unit,
) {
    SettingsCard(
        title = "Sync",
        body = "Remote reading activity waiting to upload from this device.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = state.summaryLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.detailLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppButton(
                onClick = onRetrySync,
                enabled = state.hasPending,
                modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
            ) {
                Text(text = "Retry")
            }
        }
        if (state.sourceGroups.isEmpty()) {
            Text(
                text = "All remote reading activity is synced.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.sourceGroups.forEachIndexed { index, sourceGroup ->
                if (index > 0) {
                    AppDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                SyncSourceGroup(group = sourceGroup)
            }
        }
    }
}

@Composable
private fun SyncSourceGroup(
    group: SyncSettingsSourceGroup,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = group.sourceLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = group.countLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = group.stateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (group.failedCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        group.lastAttemptLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        group.lastError?.let { error ->
            Text(
                text = "Last error: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        group.items.take(MaxVisibleSyncRowsPerSource).forEach { item ->
            SyncItemRow(item = item)
        }
        val hiddenCount = group.items.size - MaxVisibleSyncRowsPerSource
        if (hiddenCount > 0) {
            Text(
                text = "$hiddenCount more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncItemRow(
    item: SyncSettingsItemRow,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                item.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = item.stateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (item.stateLabel == "Failed") {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = listOfNotNull(item.artifactLabel, item.updatedAtLabel).joinToString(" - "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SyncSettingsUiState.summaryLabel(): String = when (pendingCount) {
    0 -> "No pending items"
    1 -> "1 pending item"
    else -> "$pendingCount pending items"
}

private fun SyncSettingsUiState.detailLabel(): String = when {
    failedCount > 0 && queuedCount > 0 -> "$failedCount failed, $queuedCount queued"
    failedCount > 0 -> "$failedCount failed"
    queuedCount > 0 -> "$queuedCount queued"
    else -> "Nothing waiting"
}

private fun SyncSettingsSourceGroup.countLabel(): String = when {
    failedCount > 0 && queuedCount > 0 -> "$failedCount failed, $queuedCount queued"
    failedCount > 0 -> "$failedCount failed"
    queuedCount == 1 -> "1 queued"
    else -> "$queuedCount queued"
}

private const val MaxVisibleSyncRowsPerSource = 6
