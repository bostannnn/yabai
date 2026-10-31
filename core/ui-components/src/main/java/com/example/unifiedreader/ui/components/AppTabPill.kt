package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * A tab as a pill: text only until selected, then on `surfaceContainerHigh`. Quieter than an
 * [AppChip], so a row of tabs reads as "which list" and the chips under it as "which value".
 * The library's shelf tabs and Browse's rows on narrow windows.
 */
@Composable
fun AppTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(AppTabPillDefaults.height)
            .clip(CircleShape)
            .background(if (selected) colors.surfaceContainerHigh else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(AppTabPillDefaults.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(AppTabPillDefaults.countGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) colors.onSurface else colors.onSurfaceVariant) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            if (count != null) {
                Text(
                    text = appCount(count),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = LocalContentColor.current.copy(alpha = AppTabPillDefaults.countAlpha),
                )
            }
        }
    }
}

object AppTabPillDefaults {
    val height: Dp = 34.dp
    val contentPadding = PaddingValues(horizontal = 14.dp)
    val countGap: Dp = 6.dp
    const val countAlpha: Float = 0.8f
}

/** A count as the app writes numbers: digits with a separator, "1,474". */
fun appCount(count: Int): String = String.format(Locale.US, "%,d", count)
