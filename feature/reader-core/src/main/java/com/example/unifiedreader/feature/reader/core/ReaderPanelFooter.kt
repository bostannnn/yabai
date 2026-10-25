package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppLayout

/**
 * The foot of a reader's Contents or Pages panel: a hairline, where the reader is ("Chapter 3 of
 * 12", "Pages 4-5 of 24") at the start and the percentage at the end, and [below] under them (the
 * EPUB contents' progress bar).
 */
@Composable
fun ReaderPanelFooter(
    position: String,
    percent: String,
    modifier: Modifier = Modifier,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val tokens = readerOverlayTokens()
    AppDivider()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppLayout.inset, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = position,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = percent,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.primaryText,
                maxLines = 1,
            )
        }
        below?.invoke(this)
    }
}
