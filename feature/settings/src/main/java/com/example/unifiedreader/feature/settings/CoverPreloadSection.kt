package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun CoverPreloadSection(
    state: CoverPreloadUiState,
    label: String,
    onPreloadCovers: () -> Unit,
    onRetryFailedCovers: () -> Unit,
    onCancelPreload: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onPreloadCovers,
                enabled = state.canStart,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SettingsActionButtonMinHeight),
            ) {
                if (state.isRunning) {
                    SettingsButtonProgressIndicator()
                } else {
                    Text(label)
                }
            }
            if (state.isRunning) {
                OutlinedButton(
                    onClick = onCancelPreload,
                    modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                ) {
                    Text("Cancel")
                }
            } else if (state.canRetryFailed) {
                OutlinedButton(
                    onClick = onRetryFailedCovers,
                    modifier = Modifier.heightIn(min = SettingsActionButtonMinHeight),
                ) {
                    Text("Retry failed")
                }
            }
        }
        if (state.isRunning && state.progressLabel != null) {
            LinearProgressIndicator(
                progress = { state.progressFraction ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = state.progressLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        state.failedItems.takeIf { failures -> failures.isNotEmpty() }?.let { failures ->
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                failures.forEach { failure ->
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
