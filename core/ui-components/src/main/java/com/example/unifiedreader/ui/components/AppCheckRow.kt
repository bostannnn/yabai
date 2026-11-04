package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * One value of a list you can pick several of: a 20 box, the name, the count at the end. With [share]
 * (0 to 1), a faint bar behind the row shows how big the value is next to the biggest in its list; [indent]
 * sets it 26 in (a city under its country).
 */
@Composable
fun AppCheckRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    share: Float? = null,
    indent: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val start = if (indent) AppCheckRowDefaults.indent else 0.dp
    val shareColor = colors.surfaceContainerLow
    val barCorner = UnifiedReaderThemeTokens.presentation.innerCornerRadius
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (indent) AppCheckRowDefaults.indentedHeight else AppCheckRowDefaults.height)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
            .then(
                if (share == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        val left = start.toPx()
                        val inset = AppCheckRowDefaults.barInset.toPx()
                        val room = size.width - left - AppCheckRowDefaults.countRoom.toPx()
                        val width = room * share.coerceIn(0f, 1f)
                        if (width > 0f) {
                            drawRoundRect(
                                color = shareColor,
                                topLeft = Offset(left, inset),
                                size = Size(width, size.height - inset * 2),
                                cornerRadius = CornerRadius(barCorner.toPx()),
                            )
                        }
                    }
                },
            )
            .padding(start = start + if (share != null) AppCheckRowDefaults.barPadding else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(AppCheckRowDefaults.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppCheckBox(checked = checked)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = colors.onSurface,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text(
                text = appCount(it),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** The kit's 20 box: an `outline` edge, filled `primary` with an `on-primary` check when [checked]. */
@Composable
fun AppCheckBox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AppCheckRowDefaults.boxCorner)
    Box(
        modifier = modifier
            .size(AppCheckRowDefaults.boxSize)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(colors.primary)
                } else {
                    Modifier.border(AppCheckRowDefaults.boxEdge, colors.outline, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(imageVector = AppIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(AppCheckRowDefaults.checkSize))
        }
    }
}

object AppCheckRowDefaults {
    val height: Dp = 44.dp
    val indentedHeight: Dp = 40.dp
    val indent: Dp = 26.dp
    val gap: Dp = 14.dp
    val boxSize: Dp = 20.dp
    val boxCorner: Dp = 5.dp
    val boxEdge: Dp = 1.5.dp
    val checkSize: Dp = 16.dp
    val barInset: Dp = 6.dp
    val barPadding: Dp = 8.dp
    val countRoom: Dp = 40.dp
}
