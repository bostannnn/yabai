package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGesturesPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CompactReaderOverlayFrame(
    title: String,
    progressLabel: String,
    leadingActions: @Composable RowScope.() -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .safeGesturesPadding()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReaderActionGroup(
            content = leadingActions,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xAA11161B), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0x3AF5F7FA), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFF5F7FA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB7C4CE),
            )
        }
        ReaderActionGroup(
            content = trailingActions,
        )
    }
}

@Composable
private fun ReaderActionGroup(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .background(Color(0xAA11161B), RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x3AF5F7FA), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
