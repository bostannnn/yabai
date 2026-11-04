package com.example.unifiedreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** One tile of an [AppTileGrid]: a short choice and the line under it (its count, or its pages and count). */
data class AppTile(
    val key: String,
    val label: String,
    val supporting: String? = null,
    val selected: Boolean = false,
)

/**
 * A few short choices as tiles you tap (a library, a reading status, a mood, a length): 2 columns for 2 or
 * 4 tiles, 3 otherwise, [AppTileGridDefaults.gap] apart both ways. [more], when given, is a last tile with a
 * hairline edge and its name in `primary` that opens the rest ("4 more").
 */
@Composable
fun AppTileGrid(
    tiles: List<AppTile>,
    onTileClick: (AppTile) -> Unit,
    modifier: Modifier = Modifier,
    more: AppTile? = null,
    onMoreClick: (() -> Unit)? = null,
) {
    val count = tiles.size + if (more != null) 1 else 0
    val columns = AppTileGridDefaults.columnsFor(count, longestLabel = tiles.maxOfOrNull { tile -> tile.label.length } ?: 0)
    val cells: List<AppTile?> = remember(tiles, more) { tiles + listOfNotNull(more) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTileGridDefaults.gap)) {
        cells.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(AppTileGridDefaults.gap),
            ) {
                row.forEach { tile ->
                    if (tile == null) return@forEach
                    val isMore = tile === more
                    AppTileCell(
                        tile = tile,
                        isMore = isMore,
                        onClick = { if (isMore) onMoreClick?.invoke() else onTileClick(tile) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppTileCell(tile: AppTile, isMore: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.controlCornerRadius)
    val selected = tile.selected && !isMore
    val labelColor = when {
        selected -> colors.onPrimaryContainer
        isMore -> colors.primary
        else -> colors.onSurface
    }
    val lineColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = AppTileGridDefaults.minHeight)
            .appPressScale(interactionSource)
            .clip(shape)
            .then(
                when {
                    selected -> Modifier.background(colors.primaryContainer).border(BorderStroke(1.dp, colors.primary), shape)
                    isMore -> Modifier.border(BorderStroke(1.dp, colors.outlineVariant), shape)
                    else -> Modifier.background(colors.surfaceContainer)
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, role = if (isMore) Role.Button else Role.Checkbox, onClick = onClick)
            .semantics { if (!isMore) this.selected = selected }
            .padding(horizontal = AppTileGridDefaults.horizontalPadding, vertical = AppTileGridDefaults.verticalPadding),
        verticalArrangement = Arrangement.spacedBy(AppTileGridDefaults.lineGap, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text(
            text = tile.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        tile.supporting?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                color = lineColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

object AppTileGridDefaults {
    val minHeight: Dp = 56.dp
    val gap: Dp = 8.dp
    val horizontalPadding: Dp = 12.dp
    val verticalPadding: Dp = 8.dp
    val lineGap: Dp = 2.dp

    /** How many tiles fit the most before the grid shows a "N more" tile instead. */
    const val maxTiles: Int = 6

    /** The longest name that fits a third of a sheet without breaking a word ("Not started", "Reflective"). */
    const val maxThirdLabel: Int = 11

    /** 2 columns for 2 or 4 tiles, or when a name is too long for a third of the row; 3 otherwise. */
    fun columnsFor(count: Int, longestLabel: Int = 0): Int =
        if (count == 2 || count == 4 || longestLabel > maxThirdLabel) 2 else 3
}
