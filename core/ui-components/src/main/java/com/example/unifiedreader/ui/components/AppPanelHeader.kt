package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The first row of every panel and sheet: an optional back arrow (for a second page inside the
 * panel), the title and an optional subtitle, optional [actions], and an optional close button.
 * Back and Close sit in the same places on every sheet. A sheet whose content starts with its own
 * title (a book's page, a journal day) passes no [title] and keeps only the buttons.
 */
@Composable
fun AppPanelHeader(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    backContentDescription: String = "Back",
    closeContentDescription: String = "Close",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) 8.dp else AppPanelHeaderDefaults.horizontalPadding,
                end = if (onClose != null) 8.dp else AppPanelHeaderDefaults.horizontalPadding,
                top = AppPanelHeaderDefaults.topPadding,
                bottom = AppPanelHeaderDefaults.bottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            AppIconButton(onClick = onBack) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = backContentDescription,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (title != null) {
                // The spec's panel title: 20 sp Bold.
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
        if (onClose != null) {
            AppIconButton(onClick = onClose) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = closeContentDescription,
                )
            }
        }
    }
}

object AppPanelHeaderDefaults {
    val horizontalPadding = AppLayout.inset
    val topPadding = 16.dp
    val bottomPadding = 8.dp
}
