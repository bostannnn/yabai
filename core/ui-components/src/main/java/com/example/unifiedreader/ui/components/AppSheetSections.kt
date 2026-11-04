package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    // What the section is set to, at the label's end in the accent ("150–300").
    value: String? = null,
    // The label's side inset; a label inside a dialog card, which is padded already, passes 0.
    horizontalInset: Dp = AppLayout.inset,
) {
    val labelPadding = Modifier.padding(start = horizontalInset, end = horizontalInset, top = 14.dp, bottom = 8.dp)
    if (value == null) {
        AppSheetLabelText(text = text, modifier = modifier.then(labelPadding))
        return
    }
    Row(modifier = modifier.then(labelPadding)) {
        AppSheetLabelText(
            text = text,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun AppSheetLabelText(
    text: String,
    modifier: Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
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

/**
 * The space that ends a sheet section's body (its chips, value rows or slider) before the next
 * [AppSheetDivider], the same under every kind of section. A section that ends in a row, which
 * carries its own padding, leaves it out.
 */
@Composable
fun AppSheetSectionEnd(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(AppLayout.gridGap))
}
