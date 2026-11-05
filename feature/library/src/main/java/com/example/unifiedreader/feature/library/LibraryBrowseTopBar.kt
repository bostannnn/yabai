package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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

/**
 * The library's search field: the middle of the library's bar and of the shelf browser's. With
 * [onClick] it is the door to the search panel instead of a field (the library's bar).
 */
@Composable
fun LibraryBrowseTopBarSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    queryContentDescription: String = "Library search query",
    placeholder: String = "Search",
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    AppSearchField(
        query = query,
        onQueryChange = onQueryChanged,
        placeholder = placeholder,
        modifier = modifier.testTag(LibraryBrowseTopBarSearchFieldTag),
        height = UnifiedReaderThemeTokens.presentation.searchHeight,
        autoFocus = autoFocus,
        contentDescription = queryContentDescription,
        trailingContent = trailingContent,
        onClick = onClick,
    )
}

const val LibraryBrowseTopBarSearchFieldTag = "library-search-field"
