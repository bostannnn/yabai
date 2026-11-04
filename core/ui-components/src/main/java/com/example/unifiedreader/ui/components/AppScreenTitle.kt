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
 * A large title that is the content itself (a series, a Kavita list, a first-launch question):
 * 40 sp Bold, 32 on phones. Never a screen's own name: the rail or the drawer already says where
 * you are, so Settings, the journal and a source's library have no title.
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
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02).em,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}
