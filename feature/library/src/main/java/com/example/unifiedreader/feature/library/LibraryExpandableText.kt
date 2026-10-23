package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppTextButton

/**
 * A description folded to four lines, with More when four lines do not hold it all (and Less once
 * it is open). The book page's About and an opened list's top use it.
 */
@Composable
internal fun LibraryExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else ExpandableTextCollapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout -> if (!expanded) overflows = layout.hasVisualOverflow },
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
        if (overflows || expanded) {
            // Shifted so the label lines up with the text, past the button's own 12 dp inset.
            AppTextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.offset(x = horizontalPadding - TextButtonLabelInset),
            ) {
                Text(text = if (expanded) "Less" else "More", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private const val ExpandableTextCollapsedLines = 4
private val TextButtonLabelInset = 12.dp
