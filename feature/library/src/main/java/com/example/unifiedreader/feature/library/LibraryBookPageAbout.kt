package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryTagKey
import com.example.unifiedreader.feature.library.tags.LibraryTagSortSheet
import com.example.unifiedreader.feature.library.tags.LibraryTagSortTarget
import com.example.unifiedreader.feature.library.tags.LocalLibraryTagSorting
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppSheetDivider
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppTextButton

/**
 * The book page's About: the description, its tags and genres in rows (Set in, When, About,
 * Genre, as Browse names them and where you sorted them; a tag opens Browse with it picked when
 * [onBrowse] is given, and its sort sheet on a long press), then the facts.
 */
@Composable
internal fun LibraryBookPageAbout(
    text: LibraryBookPageText,
    source: SourceType,
    descriptionLoading: Boolean,
    onBrowse: ((LibraryBrowseChip) -> Unit)?,
) {
    val sorting = LocalLibraryTagSorting.current.sortingFor(source) ?: LibraryTagSorting()
    var sortTarget by remember(source) { mutableStateOf<LibraryTagSortTarget?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        AppSheetDivider()
        AppSheetLabel(text = "About")
        if (descriptionLoading) {
            LibraryBookDescriptionPlaceholder()
        } else {
            text.description?.let { description ->
                LibraryExpandableText(text = description, horizontalPadding = AppLayout.inset)
            }
        }
        val tagRows = remember(text.genres, text.tags, text.locations, sorting) {
            libraryBookPageTagRows(text.genres, text.tags, text.locations, sorting)
        }
        if (tagRows.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = AppLayout.inset, end = AppLayout.inset, top = AppLayout.headingGap),
                verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
            ) {
                tagRows.forEach { (row, values) ->
                    LibraryBookPageTagRow(
                        row = row,
                        values = values,
                        onBrowse = onBrowse,
                        onSort = if (row == LibraryBrowseRow.GENRE) {
                            null
                        } else {
                            { value -> sortTarget = libraryBookPageSortTarget(source, row, value, text) }
                        },
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = AppLayout.inset, vertical = AppLayout.headingGap),
            verticalArrangement = Arrangement.spacedBy(FactGap),
        ) {
            text.facts.forEach { (label, value) ->
                Row {
                    LibraryBookPageRowLabel(text = label)
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    sortTarget?.let { target ->
        LibraryTagSortSheet(target = target, onDismiss = { sortTarget = null }, onBrowse = onBrowse)
    }
}

/**
 * A book's tags by the Browse rows [sorting] puts them in (a tag nobody sorted is About; a
 * Location is Set in), then its genres; rows it has nothing in are left out.
 */
internal fun libraryBookPageTagRows(
    genres: List<String>,
    tags: List<String>,
    locations: List<String>,
    sorting: LibraryTagSorting,
): List<Pair<LibraryBrowseRow, List<String>>> {
    val distinctTags = tags.distinctBy(::libraryTagKey)
    val places = (distinctTags.filter { sorting.kindOf(it) == LibraryTagKind.PLACE } + locations).distinctBy(::libraryTagKey)
    return listOf(
        LibraryBrowseRow.SET_IN to places,
        LibraryBrowseRow.WHEN to distinctTags.filter { sorting.kindOf(it) == LibraryTagKind.ERA },
        LibraryBrowseRow.ABOUT to distinctTags.filter { sorting.kindOf(it) == LibraryTagKind.THEME },
        LibraryBrowseRow.GENRE to genres.distinctBy(String::lowercase),
    ).filter { (_, values) -> values.isNotEmpty() }
}

/** A Set in value that is no tag of the book but one of its Locations is that Location. */
private fun libraryBookPageSortTarget(
    source: SourceType,
    row: LibraryBrowseRow,
    value: String,
    text: LibraryBookPageText,
): LibraryTagSortTarget {
    val key = libraryTagKey(value)
    val location = row == LibraryBrowseRow.SET_IN &&
        text.tags.none { libraryTagKey(it) == key } &&
        text.locations.any { libraryTagKey(it) == key }
    return LibraryTagSortTarget(source, if (location) LibraryTagField.LOCATION else LibraryTagField.TAG, value)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryBookPageTagRow(
    row: LibraryBrowseRow,
    values: List<String>,
    onBrowse: ((LibraryBrowseChip) -> Unit)?,
    onSort: ((String) -> Unit)?,
) {
    var unfolded by rememberSaveable(row, values) { mutableStateOf(false) }
    val shown = if (unfolded) values else values.take(FoldedTagCount)
    Row(verticalAlignment = Alignment.Top) {
        LibraryBookPageRowLabel(text = row.label, modifier = Modifier.padding(top = AppLayout.headingGap))
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            verticalArrangement = Arrangement.spacedBy(ChipGap),
        ) {
            shown.forEach { value ->
                AppChip(
                    selected = false,
                    onClick = { onBrowse?.invoke(LibraryBrowseChip(row, value)) },
                    enabled = onBrowse != null,
                    modifier = Modifier.height(LibraryBookPageChipHeight),
                    label = { Text(text = value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onLongClick = onSort?.let { sort -> { sort(value) } },
                    onLongClickLabel = "Sort this tag",
                )
            }
            val hidden = values.size - shown.size
            if (hidden > 0) {
                AppTextButton(
                    onClick = { unfolded = true },
                    modifier = Modifier.height(LibraryBookPageChipHeight),
                ) {
                    Text(text = "+$hidden more", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The quiet label at the start of a tag row or a fact. */
@Composable
private fun LibraryBookPageRowLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.width(LibraryBookPageFactLabelWidth),
    )
}

private val LibraryBookPageChipHeight = 30.dp
private val LibraryBookPageFactLabelWidth = 110.dp
private val ChipGap = 8.dp
private val FactGap = 6.dp

/** A row's chips shown before "+N more". */
private const val FoldedTagCount = 6
