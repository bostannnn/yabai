package com.example.unifiedreader.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * A screen's large title at the top of its content (the library's source, the settings hub):
 * 40 sp Bold, 32 on phones. It scrolls with the content; the top bar takes the name once it has
 * scrolled away.
 */
@Composable
fun AppScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = currentAppWindowWidth() == AppWindowWidth.Compact,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = if (compact) 32.sp else 40.sp,
            lineHeight = if (compact) 36.sp else 44.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}
