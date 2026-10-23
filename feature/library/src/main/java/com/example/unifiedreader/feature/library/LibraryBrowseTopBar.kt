package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSearchField
import com.example.unifiedreader.ui.components.AppTopBar
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
fun LibraryBrowseTopBarContainer(
    modifier: Modifier = Modifier,
    surfaceVisible: Boolean = true,
    // A solid color instead of the glass: the selection bar.
    containerColor: Color? = null,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    AppTopBar(
        modifier = modifier,
        surfaceVisible = surfaceVisible,
        containerColor = containerColor,
        content = content,
    )
}

@Composable
fun LibraryBrowseSearchModeTopBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    compactToolbar: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    queryContentDescription: String = "Library search query",
) {
    if (compactToolbar) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = "Close search",
                )
            }
            LibraryBrowseTopBarSearchField(
                query = query,
                onQueryChanged = onQueryChanged,
                autoFocus = true,
                queryContentDescription = queryContentDescription,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AppIconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = "Close search",
                )
            }
            LibraryBrowseTopBarSearchField(
                query = query,
                onQueryChanged = onQueryChanged,
                autoFocus = true,
                queryContentDescription = queryContentDescription,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(WideLibrarySearchWidthFraction)
                    .widthIn(max = ExpandedLibrarySearchMaxWidth),
            )
        }
    }
}

/** The library's search field, in the search bar and, on wide windows, in the resting bar. */
@Composable
fun LibraryBrowseTopBarSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    queryContentDescription: String = "Library search query",
    placeholder: String = "Search",
) {
    AppSearchField(
        query = query,
        onQueryChange = onQueryChanged,
        placeholder = placeholder,
        modifier = modifier.testTag(LibraryBrowseTopBarSearchFieldTag),
        height = UnifiedReaderThemeTokens.presentation.searchHeight,
        autoFocus = autoFocus,
        contentDescription = queryContentDescription,
    )
}

private val ExpandedLibrarySearchMaxWidth = 640.dp
private const val WideLibrarySearchWidthFraction = 0.62f
const val LibraryBrowseTopBarSearchFieldTag = "library-search-field"
