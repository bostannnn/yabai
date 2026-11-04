package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The heading on the page ground above a grid or a list ("All books 1,474", "Your lists 5"):
 * 20 sp SemiBold (18 on phones) with an optional [count] in Regular `onSurfaceVariant`. It sits on the gutter
 * line, flush with what it heads; not inside panels or sheets.
 */
@Composable
fun AppSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    count: String? = null,
) {
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = if (compact) 18.sp else 20.sp,
                lineHeight = if (compact) 24.sp else 26.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
        count?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = if (compact) 14.sp else 15.sp,
                    fontWeight = FontWeight.Normal,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
