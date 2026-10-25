package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A section label inside a sheet or dialog ("Format", "Reading status", "About", "What's new"):
 * 13 sp Bold in `onSurfaceVariant`, on the sheet's inset, 14 above and 8 below. A page outside a
 * sheet labels its groups with [AppGroupLabel] instead.
 */
@Composable
fun AppSheetLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(start = AppLayout.inset, end = AppLayout.inset, top = 14.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** The hairline between a sheet's sections: the sheet's width less its inset on both sides. */
@Composable
fun AppSheetDivider(modifier: Modifier = Modifier) {
    AppDivider(
        modifier = modifier.padding(horizontal = AppLayout.inset),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AppDividerDefaults.alpha),
    )
}
