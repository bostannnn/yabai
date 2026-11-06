package com.example.unifiedreader.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppReorderHandleIcon
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.appReorderableItem
import com.example.unifiedreader.ui.components.movedItem
import com.example.unifiedreader.ui.components.rememberAppReorderState

/** The sources' order, for the rail and the drawer, as a drag-to-reorder list. */
@Composable
fun DrawerOrderSettingsGroup(
    sourceOrder: List<SourceType>,
    onSourceOrderChanged: (List<SourceType>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "The rail and the drawer list your sources in this order.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppSettingsGroup {
            // Rows render from this local copy so consecutive moves in one drag build on each
            // other even before the app state round-trips.
            var order by remember(sourceOrder) { mutableStateOf(sourceOrder) }
            val reorderState = rememberAppReorderState { fromIndex, toIndex ->
                val next = order.movedItem(fromIndex, toIndex)
                order = next
                onSourceOrderChanged(next)
            }
            order.forEachIndexed { index, sourceType ->
                key(sourceType) {
                    if (index > 0) AppSettingsRowDivider()
                    AppSettingsRow(
                        title = sourceType.displayName(),
                        icon = sourceType.navigationIcon(),
                        modifier = Modifier.appReorderableItem(reorderState, index),
                        trailing = { AppReorderHandleIcon(reorderState, index, sourceType.displayName()) },
                    )
                }
            }
        }
    }
}
