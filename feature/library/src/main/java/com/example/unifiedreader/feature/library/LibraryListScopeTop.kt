package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem

/**
 * The top of an opened Kavita list or collection; its name stays in the bar. A reading list has
 * its description, "5 items · 2 read" and the card for the one to read next; a collection has its
 * description and "12 series", without a card: its series have no order to follow.
 */
data class LibraryListScopeTop(
    val description: String?,
    val countLabel: String,
    val next: LibrarySeriesNext? = null,
    val nextOverline: String? = null,
)

/** A reading list's top, from its items in list order. */
fun libraryReadingListTop(
    description: String?,
    items: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): LibraryListScopeTop {
    val read = items.count { item -> progressByItemId[item.id]?.isComplete == true }
    val next = librarySeriesNextIssue(items, progressByItemId)
    return LibraryListScopeTop(
        description = description.cleanDescription(),
        countLabel = listOfNotNull(
            if (items.size == 1) "1 item" else "${items.size} items",
            "$read read".takeIf { read > 0 },
        ).joinToString(" · "),
        next = next,
        nextOverline = next?.let { "Up next in this list · ${items.indexOf(it.item) + 1} of ${items.size}" },
    )
}

/** A collection's top: its description and how many series it holds. */
fun libraryCollectionTop(description: String?, seriesCount: Int): LibraryListScopeTop =
    LibraryListScopeTop(
        description = description.cleanDescription(),
        countLabel = "$seriesCount series",
    )

private fun String?.cleanDescription(): String? =
    this?.let(::plainTextDescription)?.trim()?.takeIf(String::isNotEmpty)

@Composable
fun LibraryListScopeTopContent(
    top: LibraryListScopeTop,
    progressByItemId: Map<String, ItemProgressSummary>,
    gridMetrics: LibraryGridMetrics,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    modifier: Modifier = Modifier,
    actionContext: LibraryItemActionContext = LibraryItemActionContext(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        top.description?.let { description ->
            LibraryExpandableText(text = description, modifier = Modifier.padding(end = 24.dp))
        }
        Text(
            text = top.countLabel,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        top.next?.let { next ->
            LibrarySeriesReadingCard(
                next = next,
                progress = progressByItemId[next.item.id],
                gridMetrics = gridMetrics,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                onItemSelected = onItemSelected,
                actionContext = actionContext,
                overline = top.nextOverline,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
