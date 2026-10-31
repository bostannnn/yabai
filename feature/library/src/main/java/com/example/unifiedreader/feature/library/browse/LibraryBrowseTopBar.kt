package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarContainer
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons

/**
 * Browse's bar: Back, "Browse" (a page you opened says its name), and ••• for the rows it shows.
 * No glass until something scrolls under it.
 */
@Composable
fun LibraryBrowseTopBar(
    onBack: () -> Unit,
    onShowRows: () -> Unit,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
) {
    LibraryBrowseTopBarContainer(modifier = modifier, surfaceVisible = scrolled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(onClick = onBack) {
                Icon(imageVector = AppIcons.ArrowBack, contentDescription = "Back to the library")
            }
            Text(
                text = "Browse",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AppIconButton(onClick = onShowRows) {
                Icon(imageVector = AppIcons.MoreVert, contentDescription = "Rows Browse shows")
            }
        }
    }
}
