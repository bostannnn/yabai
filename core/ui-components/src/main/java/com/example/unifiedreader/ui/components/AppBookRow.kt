package com.example.unifiedreader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A book in a list (the journal's What you read, a journal day, downloads): its [cover] (40 dp
 * wide, 2:3), the title at 15 sp SemiBold, whatever [supporting] adds under it ([AppBookRowLine]
 * for the usual one line), and a [trailing] value or action. A list in an order you set puts
 * its number in [leading], before the cover. The ripple is clipped to the control radius.
 */
@Composable
fun AppBookRow(
    title: String,
    cover: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = AppBookRowDefaults.contentPadding,
    supporting: @Composable ColumnScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppBookRowDefaults.cornerRadius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(AppBookRowDefaults.coverGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        cover()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supporting()
        }
        trailing()
    }
}

/** A numbered list's position in [AppBookRow]'s leading slot: 15 sp Bold, right-aligned in 24 dp. */
@Composable
fun AppBookRowNumber(
    number: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = number.toString(),
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = modifier.width(AppBookRowDefaults.numberWidth),
    )
}

/** A book row's line under its title: 13 sp in `onSurfaceVariant` (or [color]). */
@Composable
fun AppBookRowLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

object AppBookRowDefaults {
    val coverWidth = 40.dp
    val numberWidth = 24.dp
    val coverGap = 14.dp
    val cornerRadius = 12.dp
    val contentPadding = PaddingValues(vertical = 10.dp)
}
