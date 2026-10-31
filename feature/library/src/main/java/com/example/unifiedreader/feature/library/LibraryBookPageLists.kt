package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSheetLabel

/** The book page's In your lists: one row per list holding the book; a tap opens the list. */
@Composable
internal fun LibraryBookPageLists(
    memberships: List<LibraryItemListMembership>,
    onOpenList: (String) -> Unit,
) {
    // Spaced like the page's Reading section above it.
    Column(modifier = Modifier.padding(top = AppLayout.headingGap)) {
        AppSheetLabel(text = "In your lists")
        memberships.forEach { membership ->
            AppSettingsRow(
                title = membership.name,
                supporting = membership.line,
                onClick = { onOpenList(membership.listId) },
            )
        }
    }
}
