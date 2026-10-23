package com.example.unifiedreader.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButton
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle

/** The "•••" under a cover: opens the item's book page. */
@Composable
fun LibraryItemPageButton(
    item: ReadableItem,
    actionContext: LibraryItemActionContext,
    modifier: Modifier = Modifier,
    buttonStyle: LibraryItemMenuButtonStyle = LibraryItemMenuButtonStyle.CompactOverlay,
) {
    LibraryItemMenuButton(
        onClick = { actionContext.onOpenItemPage(item) },
        modifier = modifier,
        style = buttonStyle,
    )
}
