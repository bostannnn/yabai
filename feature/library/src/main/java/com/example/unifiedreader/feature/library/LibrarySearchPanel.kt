package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibrarySearchTagSuggestion
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.librarySearchTagSuggestions
import com.example.unifiedreader.feature.library.tags.LocalLibraryTagSorting
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppSearchPanel
import com.example.unifiedreader.ui.components.AppSearchPanelDefaults
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.rememberAppSearchPanelState
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the library's search panel was left, acted on once it has closed. */
sealed interface LibrarySearchPanelOutcome {
    /** Outside, Back or Close: the search goes back to what it was when the panel opened. */
    data object Cancel : LibrarySearchPanelOutcome

    /** A result was picked: open it. */
    data class Open(val item: ReadableItem) : LibrarySearchPanelOutcome

    /** Show all: the search stays on All books, as covers. */
    data object ShowAll : LibrarySearchPanelOutcome

    /** A tag the words name becomes a filter on All books, and the search makes way. */
    data class FilterByTag(val chip: LibraryBrowseChip) : LibrarySearchPanelOutcome
}

/**
 * The library's search in the middle of the screen (design system, AppSearchPanel): the tags the
 * words name, then the books that match, the first highlighted, and Show all at the foot. It
 * opens out of the bar's field ([anchor]) and is left through [onClosed], once its exit has played.
 */
@Composable
fun LibrarySearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    // The books the search finds, in the library's order (its filters and place apply).
    results: List<ReadableItem>,
    selectedSource: SourceType,
    // The source's books, for the tags the words name; empty where tags don't apply (a series).
    tagPool: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    locallyAvailableItemIds: Set<String>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onClosed: (LibrarySearchPanelOutcome) -> Unit,
    anchor: Rect? = null,
) {
    val panelState = rememberAppSearchPanelState()
    var outcome by remember { mutableStateOf<LibrarySearchPanelOutcome>(LibrarySearchPanelOutcome.Cancel) }
    fun leave(next: LibrarySearchPanelOutcome) {
        outcome = next
        panelState.dismiss()
    }
    val searching = query.isNotBlank()
    val tagSorting = LocalLibraryTagSorting.current.sortingFor(selectedSource) ?: LibraryTagSorting()
    val tagSuggestions by produceState(emptyList<LibrarySearchTagSuggestion>(), query, tagPool, tagSorting) {
        value = if (query.isBlank() || tagPool.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) { librarySearchTagSuggestions(tagPool, query, tagSorting) }
        }
    }
    val shown = if (searching) results.take(LibrarySearchPanelMaxRows) else emptyList()
    val listState = rememberLazyListState()
    // New words start the list from the top, where the best match is.
    LaunchedEffect(query) { listState.scrollToItem(0) }
    AppSearchPanel(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = placeholder,
        onDismissRequest = { onClosed(outcome) },
        anchor = anchor,
        state = panelState,
        listState = listState,
        onSearch = { shown.firstOrNull()?.let { item -> leave(LibrarySearchPanelOutcome.Open(item)) } },
        footer = if (searching && results.isNotEmpty()) {
            {
                LibrarySearchShowAll(
                    count = results.size,
                    onClick = { leave(LibrarySearchPanelOutcome.ShowAll) },
                )
            }
        } else {
            null
        },
    ) {
        if (!searching) {
            item(key = "hint") { LibrarySearchPanelNote("Titles, authors, series, tags and the words of a description") }
            return@AppSearchPanel
        }
        if (tagSuggestions.isNotEmpty()) {
            item(key = "tags") {
                LibrarySearchTagStrip(
                    suggestions = tagSuggestions,
                    onFilterByTag = { chip -> leave(LibrarySearchPanelOutcome.FilterByTag(chip)) },
                    modifier = Modifier.padding(start = AppLayout.inset, end = AppLayout.inset, top = AppLayout.headingGap),
                )
            }
        }
        if (results.isEmpty()) {
            item(key = "none") { LibrarySearchPanelNote("No books match “${query.trim()}”") }
            return@AppSearchPanel
        }
        item(key = "books") {
            AppSheetLabel(text = "Books", value = NumberFormat.getIntegerInstance().format(results.size))
        }
        items(items = shown, key = { item -> item.id }) { item ->
            LibrarySearchResultRow(
                item = item,
                title = itemPrimaryText(item),
                query = query,
                progress = progressByItemId[item.id],
                isDownloaded = item.id in locallyAvailableItemIds,
                highlighted = item.id == shown.first().id,
                coverImageUrlProvider = coverImageUrlProvider,
                onClick = { leave(LibrarySearchPanelOutcome.Open(item)) },
                modifier = Modifier.padding(horizontal = AppSearchPanelDefaults.rowMargin),
                contentPadding = AppSearchPanelDefaults.rowPadding,
            )
        }
        item(key = "end") { Spacer(Modifier.height(AppSearchPanelDefaults.listBottom)) }
    }
}

@Composable
private fun LibrarySearchPanelNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(AppLayout.inset),
    )
}

/** The panel's foot: every match on All books, as covers. */
@Composable
private fun LibrarySearchShowAll(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LibrarySearchShowAllHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = AppLayout.inset),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Show all ${NumberFormat.getIntegerInstance().format(count)} in All books",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = AppIcons.NavigateNext,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The panel lists this many; Show all has the rest. */
internal const val LibrarySearchPanelMaxRows = 50

private val LibrarySearchShowAllHeight = 52.dp
