package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.availableLibraryFilterPanels
import com.example.unifiedreader.core.model.defaultVisibleLibraryFilterPanels
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.appReorderHandle
import com.example.unifiedreader.ui.components.appReorderableItem
import com.example.unifiedreader.ui.components.movedItem
import com.example.unifiedreader.ui.components.rememberAppReorderState

/** Which filter panels each library's filter sheet shows, and in what order. */
@Composable
internal fun FilterChipsSettingsPage(
    filterPanelsBySource: Map<SourceType, List<LibraryFilterPanel>>,
    onFilterPanelsChanged: (SourceType, List<LibraryFilterPanel>) -> Unit,
    contentTopInset: Dp,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var selectedSource by rememberSaveable { mutableStateOf(SourceType.entries.first()) }
    val visiblePanels = resolveLibraryFilterPanels(
        sourceType = selectedSource,
        preferredPanels = filterPanelsBySource[selectedSource],
    )
    val defaultPanels = defaultVisibleLibraryFilterPanels(selectedSource)
    val hiddenPanels = availableLibraryFilterPanels(selectedSource).filterNot(visiblePanels::contains)

    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
            SourceType.entries.forEachIndexed { index, sourceType ->
                AppSegment(
                    selected = sourceType == selectedSource,
                    onClick = { selectedSource = sourceType },
                    index = index,
                    count = SourceType.entries.size,
                    label = { Text(text = sourceType.displayName()) },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
                    .heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Shown in the ${selectedSource.displayName()} filter sheet, top to bottom. Drag to reorder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (visiblePanels != defaultPanels) {
                    AppTextButton(onClick = { onFilterPanelsChanged(selectedSource, defaultPanels) }) {
                        Text(text = "Reset")
                    }
                }
            }
            AppSettingsGroup {
                // The reorder state is per source: switching tabs must not carry a drag over.
                key(selectedSource) {
                    // Rows render from this local copy so consecutive moves in one drag build on
                    // each other even before the app state round-trips.
                    var order by remember(visiblePanels) { mutableStateOf(visiblePanels) }
                    val reorderState = rememberAppReorderState { fromIndex, toIndex ->
                        val next = order.movedItem(fromIndex, toIndex)
                        order = next
                        onFilterPanelsChanged(selectedSource, next)
                    }
                    order.forEachIndexed { index, panel ->
                        key(panel) {
                            if (index > 0) AppSettingsRowDivider(inset = FilterRowDividerInset)
                            FilterPanelRow(
                                panel = panel,
                                visible = true,
                                canHide = order.size > 1,
                                onVisibilityChanged = { onFilterPanelsChanged(selectedSource, order - panel) },
                                modifier = Modifier.appReorderableItem(reorderState, index),
                                handleModifier = Modifier.appReorderHandle(reorderState, index),
                            )
                        }
                    }
                    hiddenPanels.forEach { panel ->
                        key(panel) {
                            AppSettingsRowDivider(inset = FilterRowDividerInset)
                            FilterPanelRow(
                                panel = panel,
                                visible = false,
                                canHide = true,
                                onVisibilityChanged = { onFilterPanelsChanged(selectedSource, order + panel) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPanelRow(
    panel: LibraryFilterPanel,
    visible: Boolean,
    canHide: Boolean,
    onVisibilityChanged: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = visible,
            enabled = !visible || canHide,
            onCheckedChange = { onVisibilityChanged() },
        )
        Text(
            text = panel.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (handleModifier != null) {
            Icon(
                imageVector = AppIcons.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handleModifier
                    .size(40.dp)
                    .padding(8.dp)
                    .semantics { contentDescription = "Drag to reorder ${panel.label}" },
            )
        }
    }
}

private val FilterRowDividerInset: Dp = AppSettingsDefaults.horizontalPadding + 36.dp
