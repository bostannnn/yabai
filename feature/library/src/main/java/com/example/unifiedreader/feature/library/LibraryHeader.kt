package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.ui.components.AppScreenTitle
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth

internal const val LibraryHeaderItemKey = "header"

/** In compact windows (phones) the header and the reading card take their compact layouts. */
@Composable
fun isLibraryCompactWindow(): Boolean = currentAppWindowWidth() == AppWindowWidth.Compact

/** The top of the library: the source (or series) name as a large title and a one-line summary. */
data class LibraryHeader(
    val title: String,
    val summary: String? = null,
)

/**
 * The large title and its summary. It scrolls with the grid; once it has scrolled away the top
 * bar shows the name instead. [compact] (phones) sets the title at 32 sp instead of 40.
 */
@Composable
internal fun LibraryHeaderBlock(
    header: LibraryHeader,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppScreenTitle(text = header.title, compact = compact)
        // The line is kept while the summary is still unknown, so the grid does not move when it arrives.
        Text(
            text = header.summary ?: "\u00A0",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * "12 books · 9 comics · 7 in progress" for a source: books and comics counted from [items]
 * (a series container counts as its issues), and the number of books in progress. Parts that are
 * zero are left out; null when there is nothing to count.
 */
fun librarySourceSummary(
    items: List<ReadableItem>,
    inProgressCount: Int,
): String? {
    var books = 0
    var comics = 0
    items.forEach { item ->
        when (item.kind) {
            ReadableItemKind.BOOK -> books += 1
            ReadableItemKind.COMIC -> comics += item.issueCount ?: 1
        }
    }
    return listOfNotNull(
        countLabel(books, "book", "books"),
        countLabel(comics, "comic", "comics"),
        "$inProgressCount in progress".takeIf { inProgressCount > 0 },
    ).joinToString(" · ").ifEmpty { null }
}

/** "3 issues · 1 read · 1 in progress" for the issues of one series. */
fun librarySeriesSummary(
    issues: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): String? {
    if (issues.isEmpty()) return null
    val progress = issues.mapNotNull { issue -> progressByItemId[issue.id] }
    val read = progress.count { it.isComplete }
    val inProgress = progress.count { it.hasStarted && !it.isComplete }
    return listOfNotNull(
        countLabel(issues.size, "issue", "issues"),
        "$read read".takeIf { read > 0 },
        "$inProgress in progress".takeIf { inProgress > 0 },
    ).joinToString(" · ")
}

private fun countLabel(count: Int, one: String, many: String): String? = when (count) {
    0 -> null
    1 -> "1 $one"
    else -> "$count $many"
}
