package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.library.LibraryBrowseSearchModeTopBar
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarContainer

@Composable
fun rememberLibraryShelfBrowserSearchQuery(
    browseContextKey: String,
    browserKind: LibraryShelfBrowserKind?,
): MutableState<String> = rememberSaveable(
    browseContextKey,
    browserKind,
) {
    mutableStateOf("")
}

@Composable
fun LibraryShelfBrowserTopBar(
    title: String,
    count: Int,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    var searchModeOpen by remember(title) { mutableStateOf(searchQuery.isNotBlank()) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            searchModeOpen = true
        }
    }

    LibraryBrowseTopBarContainer {
        val compactToolbar = maxWidth < ShelfBrowserTopBarCompactBreakpoint
        if (searchModeOpen || searchQuery.isNotBlank()) {
            LibraryBrowseSearchModeTopBar(
                query = searchQuery,
                onQueryChanged = onSearchQueryChanged,
                compactToolbar = compactToolbar,
                onClose = {
                    searchModeOpen = false
                    if (searchQuery.isNotEmpty()) {
                        onSearchQueryChanged("")
                    }
                },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = count.itemCountLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { searchModeOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search shelf",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Int.itemCountLabel(): String = when (this) {
    1 -> "1 item"
    else -> "$this items"
}

private val ShelfBrowserTopBarCompactBreakpoint = 760.dp
