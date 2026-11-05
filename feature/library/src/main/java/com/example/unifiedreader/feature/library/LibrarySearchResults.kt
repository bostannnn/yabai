package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibrarySearchMatchSource
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.feature.library.tags.LocalLibraryTagSorting
import com.example.unifiedreader.core.model.LibrarySearchSnippet
import com.example.unifiedreader.core.model.LibrarySearchTagSuggestion
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.librarySearchSnippet
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.ui.components.AppBookRow
import com.example.unifiedreader.ui.components.AppBookRowDefaults
import com.example.unifiedreader.ui.components.AppBookRowLine
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * The tags a search names, above its results: "Set in Japan 96". Picking one filters the library by
 * it, so a word that is really a place, a time or a theme leads to every book that has it.
 */
@Composable
internal fun LibrarySearchTagStrip(
    suggestions: List<LibrarySearchTagSuggestion>,
    onFilterByTag: (LibraryBrowseChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SearchChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Filter by",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        suggestions.forEach { suggestion ->
            val quiet = LocalContentColor.current.copy(alpha = 0.72f)
            AppChip(
                selected = false,
                onClick = { onFilterByTag(suggestion.chip) },
                count = suggestion.count,
                label = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = quiet, fontWeight = FontWeight.Medium)) { append(suggestion.chip.row.label) }
                            append(" ")
                            append(suggestion.chip.value)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/**
 * A search result in the search panel: the cover, the title, who wrote it, and why it matched
 * when that isn't the title or a name: the sentence of its description (or the tag) with the
 * searched words marked, two lines at most, and where that came from. Its read state sits at the
 * end. The [highlighted] row is the one the keyboard's search key opens.
 */
@Composable
internal fun LibrarySearchResultRow(
    item: ReadableItem,
    title: String,
    query: String,
    progress: ItemProgressSummary?,
    isDownloaded: Boolean,
    highlighted: Boolean,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = AppBookRowDefaults.contentPadding,
) {
    val snippet = remember(item.id, item.description, query) { librarySearchSnippet(item, query) }
    val sorting = LocalLibraryTagSorting.current.sortingFor(item.sourceType) ?: LibraryTagSorting()
    val marked = MaterialTheme.colorScheme
    val snippetText = remember(snippet, marked) {
        snippet?.marked(SpanStyle(background = marked.primaryContainer, color = marked.onPrimaryContainer, fontWeight = FontWeight.SemiBold))
    }
    val colors = UnifiedReaderThemeTokens.colors
    AppBookRow(
        title = title,
        modifier = modifier.then(
            if (highlighted) {
                Modifier.background(colors.surfaceSelected, RoundedCornerShape(AppBookRowDefaults.cornerRadius))
            } else {
                Modifier
            },
        ),
        onClick = onClick,
        contentPadding = contentPadding,
        cover = {
            ResolvedLibraryCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                isDownloaded = isDownloaded,
                isRead = progress?.isComplete == true,
                showReadBadge = false,
                modifier = Modifier
                    .width(AppBookRowDefaults.coverWidth)
                    .aspectRatio(2f / 3f),
            )
        },
        supporting = {
            item.librarySecondaryText()?.let { line -> AppBookRowLine(text = line) }
            if (snippet != null && snippetText != null) {
                if (snippet.source == LibrarySearchMatchSource.DESCRIPTION) {
                    Column(verticalArrangement = Arrangement.spacedBy(SnippetGap)) {
                        Text(
                            text = snippetText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppBookRowLine(text = snippet.source.label)
                    }
                } else {
                    // A tag or genre, named the way Filters names it: "Set in Japan".
                    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = quiet)) { append(snippet.browseRowLabel(sorting)) }
                            append(" ")
                            append(snippetText)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) {
        librarySearchReadState(progress)?.let { state ->
            Text(
                text = state,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A result's read state at its row's end: "Finished", "12%", or nothing before it is begun. */
internal fun librarySearchReadState(progress: ItemProgressSummary?): String? = when {
    progress == null -> null
    progress.isComplete -> "Finished"
    progress.isJustStarted -> null
    else -> progress.percentLabel
}

/** The section a tag or genre snippet belongs to, as you sorted the tag. */
private fun LibrarySearchSnippet.browseRowLabel(sorting: LibraryTagSorting): String = when {
    source == LibrarySearchMatchSource.GENRE -> LibraryBrowseRow.GENRE.label
    else -> when (sorting.kindOf(text)) {
        LibraryTagKind.PLACE -> LibraryBrowseRow.SET_IN.label
        LibraryTagKind.ERA -> LibraryBrowseRow.WHEN.label
        LibraryTagKind.THEME -> LibraryBrowseRow.ABOUT.label
    }
}

private fun LibrarySearchSnippet.marked(style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    matches.forEach { range ->
        if (range.first >= 0 && range.last < text.length) addStyle(style, range.first, range.last + 1)
    }
}

private val SearchChipGap = 8.dp
private val SnippetGap = 2.dp
