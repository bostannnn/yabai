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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider

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

/** Reading progress waiting to upload to the remote servers, per source. */
@Composable
internal fun SyncStatusSettingsPage(
    state: SyncSettingsUiState,
    onRetrySync: () -> Unit,
    contentTopInset: Dp,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        AppSettingsGroup {
            AppSettingsRow(
                title = state.summaryLabel(),
                supporting = if (state.hasPending) {
                    state.detailLabel()
                } else {
                    "Progress you make on remote books is uploaded as you read."
                },
                trailing = {
                    if (state.hasPending) {
                        AppButton(
                            onClick = onRetrySync,
                            modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                        ) {
                            Text(text = "Retry now")
                        }
                    }
                },
            )
        }
        state.sourceGroups.forEach { group ->
            SyncSourceGroup(group = group)
        }
    }
}

@Composable
private fun SyncSourceGroup(
    group: SyncSettingsSourceGroup,
) {
    AppSettingsGroup(label = group.sourceLabel) {
        Column(
            modifier = Modifier.padding(
                horizontal = AppSettingsDefaults.horizontalPadding,
                vertical = 14.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.countLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
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
        }
        group.items.take(MaxVisibleSyncRowsPerSource).forEach { item ->
            AppSettingsRowDivider(inset = AppSettingsDefaults.dividerInsetNoIcon)
            SyncItemRow(item = item)
        }
        val hiddenCount = group.items.size - MaxVisibleSyncRowsPerSource
        if (hiddenCount > 0) {
            Text(
                text = "$hiddenCount more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = AppSettingsDefaults.horizontalPadding,
                    end = AppSettingsDefaults.horizontalPadding,
                    bottom = 14.dp,
                ),
            )
        }
    }
}

@Composable
private fun SyncItemRow(
    item: SyncSettingsItemRow,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSettingsDefaults.horizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            item.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = listOfNotNull(item.artifactLabel, item.updatedAtLabel).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
}

internal fun SyncSettingsUiState.summaryLabel(): String = when (pendingCount) {
    0 -> "Up to date"
    1 -> "1 item waiting to upload"
    else -> "$pendingCount items waiting to upload"
}

internal fun SyncSettingsUiState.detailLabel(): String = when {
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
