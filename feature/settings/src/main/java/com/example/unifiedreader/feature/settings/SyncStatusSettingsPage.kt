package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider

data class SyncSettingsUiState(
    val pendingCount: Int = 0,
    val queuedCount: Int = 0,
    val failedCount: Int = 0,
    val sourceGroups: List<SyncSettingsSourceGroup> = emptyList(),
    /** The day the oldest upload started waiting: "20 Oct". */
    val waitingSinceLabel: String? = null,
    /** When an upload was last tried, from any source: "today at 09:14". */
    val lastAttemptLabel: String? = null,
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

/**
 * What waits to upload to the servers, and why. With nothing waiting it says so in one row. With
 * a queue: a pill with how many and since when, when it last tried, Retry now, then each source
 * with its books (a book's reading sessions on one row) and the server's last answer.
 */
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
        if (!state.hasPending) {
            AppSettingsGroup {
                AppSettingsRow(
                    title = "Everything is uploaded",
                    icon = AppIcons.Check,
                    supporting = "Progress, reading sessions and journal entries go to your servers as you read.",
                )
            }
            return@SettingsPageContainer
        }
        SyncQueueHeader(state = state, onRetrySync = onRetrySync)
        state.sourceGroups.forEach { group ->
            SyncSourceGroup(group = group)
        }
    }
}

@Composable
private fun SyncQueueHeader(
    state: SyncSettingsUiState,
    onRetrySync: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val failed = state.failedCount > 0
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppBadge(
                shape = CircleShape,
                color = if (failed) scheme.errorContainer else scheme.surfaceContainerHighest,
                contentColor = if (failed) scheme.onErrorContainer else scheme.onSurface,
            ) {
                Text(
                    text = state.pillLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            state.lastAttemptLabel?.let { attempted ->
                Text(
                    text = "Last tried $attempted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        AppButton(onClick = onRetrySync, modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight)) {
            Text(text = "Retry now")
        }
    }
}

@Composable
private fun SyncSourceGroup(
    group: SyncSettingsSourceGroup,
) {
    val rows = group.queueRows()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSettingsGroup(label = group.sourceLabel) {
            rows.take(MaxVisibleSyncRowsPerSource).forEachIndexed { index, row ->
                if (index > 0) AppSettingsRowDivider(inset = AppSettingsDefaults.dividerInsetNoIcon)
                SyncQueueRowItem(row = row)
            }
            val hiddenCount = rows.size - MaxVisibleSyncRowsPerSource
            if (hiddenCount > 0) {
                Text(
                    text = "and $hiddenCount more",
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
        group.lastError?.let { error ->
            Text(
                text = "Last error: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = AppSettingsDefaults.horizontalPadding),
            )
        }
    }
}

@Composable
private fun SyncQueueRowItem(row: SyncQueueRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSettingsDefaults.horizontalPadding, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (row.failed) "Failed" else "Waiting",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (row.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One row of a source's queue: a book's waiting uploads of one kind, counted together. */
internal data class SyncQueueRow(
    val title: String,
    val line: String,
    val failed: Boolean,
)

/** "2 reading sessions · since 20 Oct", "Progress · since 20 Oct": a book's uploads of one kind on one row. */
internal fun SyncSettingsSourceGroup.queueRows(): List<SyncQueueRow> =
    items.groupBy { item -> Triple(item.title, item.artifactLabel, item.stateLabel) }
        .map { (key, group) ->
            val (title, artifact, state) = key
            val kind = if (group.size == 1) artifact else "${group.size} ${artifact.pluralArtifact()}"
            SyncQueueRow(
                title = title,
                line = listOfNotNull(kind, group.last().updatedAtLabel).joinToString(" · "),
                failed = state == "Failed",
            )
        }

private fun String.pluralArtifact(): String = when (this) {
    "Reading session" -> "reading sessions"
    "Journal entry" -> "journal entries"
    else -> "${lowercase()} updates"
}

/** "2 waiting since 20 Oct", "1 failed, 1 waiting". */
internal fun SyncSettingsUiState.pillLabel(): String = when {
    failedCount > 0 && queuedCount > 0 -> "$failedCount failed, $queuedCount waiting"
    failedCount > 0 -> "$failedCount failed"
    else -> listOfNotNull("$pendingCount waiting", waitingSinceLabel?.let { "since $it" }).joinToString(" ")
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

private const val MaxVisibleSyncRowsPerSource = 6
