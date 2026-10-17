package com.example.unifiedreader.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
fun SourceNavigationSettingsSection(
    sourceOrder: List<SourceType>,
    onMoveSourceUp: (SourceType) -> Unit,
    onMoveSourceDown: (SourceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPanel(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Navigation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "The drawer follows this order. The first source opens after app restart.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sourceOrder.forEachIndexed { index, sourceType ->
                    SourceOrderRow(
                        sourceType = sourceType,
                        isStartupSource = index == 0,
                        canMoveUp = index > 0,
                        canMoveDown = index < sourceOrder.lastIndex,
                        onMoveUp = { onMoveSourceUp(sourceType) },
                        onMoveDown = { onMoveSourceDown(sourceType) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceOrderRow(
    sourceType: SourceType,
    isStartupSource: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    AppPanel(
        shape = MaterialTheme.shapes.medium,
        color = semanticColors.surfaceRaised.copy(alpha = 0.82f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sourceType.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (isStartupSource) {
                        AppBadge(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(presentation.chipCornerRadius),
                            color = semanticColors.syncAccent,
                        ) {
                            Text(
                                text = "Startup",
                                style = MaterialTheme.typography.labelMedium,
                                color = semanticColors.onSyncAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Text(
                    text = if (isStartupSource) {
                        "Opens automatically on launch"
                    } else {
                        "Available from the drawer"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move ${sourceType.displayName()} up",
                    )
                }
                AppIconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move ${sourceType.displayName()} down",
                    )
                }
            }
        }
    }
}
