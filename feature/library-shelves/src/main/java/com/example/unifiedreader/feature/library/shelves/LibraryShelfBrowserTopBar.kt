package com.example.unifiedreader.feature.library.shelves

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarContainer
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarSearchField
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppTopBarCentredRow

/** See all's search: it stays while the tabs switch shelves, and clears when See all closes. */
@Composable
fun rememberLibraryShelfBrowserSearchQuery(
    browseContextKey: String,
    browserKind: LibraryShelfBrowserKind?,
): MutableState<String> = rememberSaveable(
    browseContextKey,
    browserKind != null,
) {
    mutableStateOf("")
}

/**
 * See all's bar, laid out like the library's: Back, the search field across the middle, and on a
 * phone the shelf's options. It names no shelf: the tabs under it say which shelf is open and how
 * much it holds. Back clears a query before it leaves. No glass until the shelf scrolls under it.
 */
@Composable
fun LibraryShelfBrowserTopBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
    scrolled: Boolean = false,
    // The shelf's options on a phone, where See all's tabs row has no room for them.
    actions: (@Composable () -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = searchQuery.isNotBlank()) {
        onSearchQueryChanged("")
        focusManager.clearFocus()
    }
    LibraryBrowseTopBarContainer(surfaceVisible = scrolled) {
        // The field is centred on the bar, as the library's is (design system, AppTopBar).
        AppTopBarCentredRow(
            modifier = Modifier.fillMaxWidth(),
            leading = {
                AppIconButton(onClick = onBack) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            trailing = { actions?.invoke() },
        ) {
            LibraryBrowseTopBarSearchField(
                query = searchQuery,
                onQueryChanged = onSearchQueryChanged,
                placeholder = "Search shelves",
                queryContentDescription = "Search shelves",
            )
        }
    }
}
