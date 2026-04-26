package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CompactReaderOverlayFrame(
    title: String,
    progressLabel: String,
    leadingActions: @Composable RowScope.() -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    applySafeGesturesPadding: Boolean = true,
    bottomPadding: Dp = 8.dp,
) {
    val tokens = readerOverlayTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (applySafeGesturesPadding) Modifier.safeGesturesPadding() else Modifier)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .background(tokens.colors.container, tokens.shapes.controlBar)
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            ReaderActionRow(
                modifier = Modifier.align(Alignment.CenterStart),
                content = leadingActions,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.42f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.tertiaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ReaderActionRow(
                modifier = Modifier.align(Alignment.CenterEnd),
                content = trailingActions,
            )
        }
    }
}

@Composable
private fun ReaderActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}
