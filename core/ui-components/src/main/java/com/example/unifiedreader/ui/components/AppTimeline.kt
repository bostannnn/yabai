package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One stop on an [AppTimeline]: a pile (a century, a decade) or, with [period], a named period. */
data class AppTimelineEntry(
    val key: String,
    val label: String,
    val count: Int?,
    val selected: Boolean,
    val period: Boolean = false,
)

/**
 * Time as one line down the start: a dot per pile in time order, the named periods placed where they fall,
 * smaller and in italics. A tap picks or drops the entry.
 */
@Composable
fun AppTimeline(
    entries: List<AppTimelineEntry>,
    onToggle: (AppTimelineEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            AppTimelineRow(
                entry = entry,
                first = index == 0,
                last = index == entries.lastIndex,
                onClick = { onToggle(entry) },
            )
        }
    }
}

@Composable
private fun AppTimelineRow(entry: AppTimelineEntry, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val lineColor = colors.outlineVariant
    val dotSize = when {
        entry.selected -> AppTimelineDefaults.selectedDot
        entry.period -> AppTimelineDefaults.periodDot
        else -> AppTimelineDefaults.dot
    }
    val dotColor = when {
        entry.selected -> colors.primary
        entry.period -> colors.outline
        else -> colors.outlineVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppTimelineDefaults.rowHeight)
            .toggleable(value = entry.selected, role = Role.Checkbox, onValueChange = { onClick() }),
        horizontalArrangement = Arrangement.spacedBy(AppTimelineDefaults.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(AppTimelineDefaults.railWidth)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val stroke = AppTimelineDefaults.line.toPx()
                    // The line runs from the first dot to the last, not past them.
                    val top = if (first) size.height / 2 else 0f
                    val bottom = if (last) size.height / 2 else size.height
                    drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = stroke)
                },
            contentAlignment = Alignment.Center,
        ) {
            // The dot, ringed in the ground so the line stops short of it.
            Box(
                modifier = Modifier
                    .size(dotSize + AppTimelineDefaults.ring * 2)
                    .background(colors.surface, CircleShape)
                    .padding(AppTimelineDefaults.ring)
                    .background(dotColor, CircleShape),
            )
        }
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = if (entry.period) 13.sp else 14.sp,
                fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Medium,
                fontStyle = if (entry.period) FontStyle.Italic else FontStyle.Normal,
            ),
            color = if (entry.period && !entry.selected) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entry.count?.let {
            Text(
                text = appCount(it),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                color = colors.onSurfaceVariant,
            )
        }
    }
}

object AppTimelineDefaults {
    val rowHeight: Dp = 40.dp
    val railWidth: Dp = 20.dp
    val line: Dp = 2.dp
    val dot: Dp = 10.dp
    val periodDot: Dp = 8.dp
    val selectedDot: Dp = 14.dp
    val ring: Dp = 2.dp
    val gap: Dp = 14.dp
}
