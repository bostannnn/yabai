package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPreloadCovers,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isRunning) {
                CircularProgressIndicator()
            } else {
                Text(label)
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
    }
}

