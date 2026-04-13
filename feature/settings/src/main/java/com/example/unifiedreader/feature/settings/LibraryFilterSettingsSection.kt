package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.availableLibraryFilterPanels
import com.example.unifiedreader.core.model.defaultVisibleLibraryFilterPanels
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels

@Composable
internal fun LibraryFilterSettingsSection(
    filterPanelsBySource: Map<SourceType, List<LibraryFilterPanel>>,
    onFilterPanelsChanged: (SourceType, List<LibraryFilterPanel>) -> Unit,
) {
    SettingsCard(
        title = "Library filters",
        body = "Choose which filter chips show for each library tab and reorder them. Remote sources may need a sync after upgrading before publisher, genre, tag, and language filters populate.",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SourceType.entries.forEach { sourceType ->
                val visiblePanels = resolveLibraryFilterPanels(
                    sourceType = sourceType,
                    preferredPanels = filterPanelsBySource[sourceType],
                )
                LibraryFilterSourceSection(
                    sourceType = sourceType,
                    visiblePanels = visiblePanels,
                    onPanelsChanged = { panels ->
                        onFilterPanelsChanged(sourceType, panels)
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryFilterSourceSection(
    sourceType: SourceType,
    visiblePanels: List<LibraryFilterPanel>,
    onPanelsChanged: (List<LibraryFilterPanel>) -> Unit,
) {
    val defaultPanels = defaultVisibleLibraryFilterPanels(sourceType)
    val availablePanels = availableLibraryFilterPanels(sourceType)
    val orderedPanels = visiblePanels + availablePanels.filterNot(visiblePanels::contains)

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = sourceType.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (visiblePanels != defaultPanels) {
                TextButton(onClick = { onPanelsChanged(defaultPanels) }) {
                    Text(text = "Reset")
                }
            }
        }

        orderedPanels.forEach { panel ->
            val visibleIndex = visiblePanels.indexOf(panel)
            val isVisible = visibleIndex >= 0
            LibraryFilterPanelRow(
                panel = panel,
                isVisible = isVisible,
                canHide = visiblePanels.size > 1,
                canMoveUp = visibleIndex > 0,
                canMoveDown = visibleIndex in 0 until visiblePanels.lastIndex,
                onVisibilityChanged = { shouldShow ->
                    onPanelsChanged(
                        if (shouldShow) {
                            visiblePanels + panel
                        } else {
                            visiblePanels - panel
                        },
                    )
                },
                onMoveUp = {
                    onPanelsChanged(visiblePanels.moved(fromIndex = visibleIndex, toIndex = visibleIndex - 1))
                },
                onMoveDown = {
                    onPanelsChanged(visiblePanels.moved(fromIndex = visibleIndex, toIndex = visibleIndex + 1))
                },
            )
        }
    }
}

@Composable
private fun LibraryFilterPanelRow(
    panel: LibraryFilterPanel,
    isVisible: Boolean,
    canHide: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(
                checked = isVisible,
                enabled = isVisible.not() || canHide,
                onCheckedChange = onVisibilityChanged,
            )
            Text(
                text = panel.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = isVisible && canMoveUp,
                onClick = onMoveUp,
            ) {
                Text(text = "Up")
            }
            TextButton(
                enabled = isVisible && canMoveDown,
                onClick = onMoveDown,
            ) {
                Text(text = "Down")
            }
        }
    }
}

private fun List<LibraryFilterPanel>.moved(
    fromIndex: Int,
    toIndex: Int,
): List<LibraryFilterPanel> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
