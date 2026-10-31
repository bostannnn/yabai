package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseFacet
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryTagKey
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppListCover
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** Genre's chips and the books its tiles show, with Fiction and Nonfiction apart when the books have both. */
internal class LibraryBrowseGenres(
    facets: List<LibraryBrowseFacet>,
    val books: Map<String, List<ReadableItem>>,
) {
    private val fiction = facets.firstOrNull { libraryTagKey(it.value) == "fiction" }
    private val nonfiction = facets.firstOrNull { libraryTagKey(it.value) in NonfictionNames }

    /** Fiction and Nonfiction, or nothing unless both are there. */
    val split: List<LibraryBrowseFacet> = if (fiction != null && nonfiction != null) listOf(fiction, nonfiction) else emptyList()
    val tiles: List<LibraryBrowseFacet> = facets.filterNot { it in split }
    val total: Int = facets.size

    fun booksOf(facet: LibraryBrowseFacet): List<ReadableItem> = books[libraryTagKey(facet.value)].orEmpty()
}

/** Fiction and Nonfiction as a segmented row: a segment picks that genre. */
@Composable
internal fun LibraryBrowseGenreSplit(
    genres: LibraryBrowseGenres,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (genres.split.isEmpty()) return
    AppSegmentedRow(modifier = modifier.fillMaxWidth()) {
        genres.split.forEach { facet ->
            val chip = LibraryBrowseChip(LibraryBrowseRow.GENRE, facet.value)
            AppSegment(
                selected = picked.any { it.sameAs(chip) },
                onClick = { onToggle(chip) },
                label = {
                    Row(horizontalArrangement = Arrangement.spacedBy(SplitCountGap), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = facet.value, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            text = appCount(facet.count),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current.copy(alpha = CountAlpha),
                        )
                    }
                },
            )
        }
    }
}

/** Genre on the landing page: one row of list-cover tiles (3 on phones, 4 on Medium, 6 on Expanded), All N for the rest. */
@Composable
internal fun LibraryBrowseGenreTiles(
    genres: LibraryBrowseGenres,
    books: LibraryBrowseBooks,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
) {
    val columns = when (currentAppWindowWidth()) {
        AppWindowWidth.Compact -> 3
        AppWindowWidth.Medium -> 4
        AppWindowWidth.Expanded -> 6
    }
    var unfolded by rememberSaveable { mutableStateOf(false) }
    val shown = if (unfolded) genres.tiles else genres.tiles.take(columns)
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
        shown.chunked(columns).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                line.forEach { facet ->
                    LibraryBrowseGenreTile(
                        facet = facet,
                        covers = genres.booksOf(facet),
                        books = books,
                        picked = picked,
                        onToggle = onToggle,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - line.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
        if (genres.tiles.size > shown.size) {
            AppTextButton(onClick = { unfolded = true }) {
                Text(text = "All ${genres.total}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Genre under its tab: the tiles as a rail running to the window edges. */
@Composable
internal fun LibraryBrowseGenreRail(
    genres: LibraryBrowseGenres,
    books: LibraryBrowseBooks,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
) {
    val gutter = appGutter()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .browseBleed(gutter),
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
    ) {
        items(items = genres.tiles, key = { facet -> libraryTagKey(facet.value) }) { facet ->
            LibraryBrowseGenreTile(
                facet = facet,
                covers = genres.booksOf(facet),
                books = books,
                picked = picked,
                onToggle = onToggle,
                modifier = Modifier.width(RailTileWidth),
            )
        }
    }
}

/** Genre in the 320 pane: a row per genre with a small list cover, folded after a few. */
@Composable
internal fun LibraryBrowseGenreList(
    genres: LibraryBrowseGenres,
    books: LibraryBrowseBooks,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
) {
    var unfolded by rememberSaveable { mutableStateOf(false) }
    val shown = if (unfolded) {
        genres.tiles
    } else {
        genres.tiles.take(PaneGenreCount) + genres.tiles.drop(PaneGenreCount).filter { facet ->
            picked.any { it.sameAs(LibraryBrowseChip(LibraryBrowseRow.GENRE, facet.value)) }
        }
    }
    Column {
        shown.forEach { facet ->
            val chip = LibraryBrowseChip(LibraryBrowseRow.GENRE, facet.value)
            val covers = genres.booksOf(facet)
            LibraryBrowseFacetRow(
                name = facet.value,
                count = facet.count,
                selected = picked.any { it.sameAs(chip) },
                onClick = { onToggle(chip) },
                leading = {
                    AppListCover(count = covers.size.coerceAtMost(MosaicCount), modifier = Modifier.width(RowCoverWidth)) { index, cellModifier ->
                        LibraryBrowseSmallCover(item = covers[index], books = books, modifier = cellModifier)
                    }
                },
            )
        }
        if (genres.tiles.size > shown.size) {
            AppTextButton(onClick = { unfolded = true }) {
                Text(text = "${genres.tiles.size - shown.size} more", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A genre as a list cover of four of its books, its name and count; picked, a check sits on its corner. */
@Composable
private fun LibraryBrowseGenreTile(
    facet: LibraryBrowseFacet,
    covers: List<ReadableItem>,
    books: LibraryBrowseBooks,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chip = LibraryBrowseChip(LibraryBrowseRow.GENRE, facet.value)
    val isPicked = picked.any { it.sameAs(chip) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius))
            .clickable(role = Role.Button, onClick = { onToggle(chip) })
            .semantics(mergeDescendants = true) { selected = isPicked },
        verticalArrangement = Arrangement.spacedBy(TileCaptionGap),
    ) {
        Box {
            AppListCover(count = covers.size.coerceAtMost(MosaicCount)) { index, cellModifier ->
                LibraryBrowseSmallCover(item = covers[index], books = books, modifier = cellModifier)
            }
            if (isPicked) {
                LibraryBrowsePickedCheck(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(TileCheckInset),
                )
            }
        }
        LibraryBrowseCardCaption(name = facet.value, count = facet.count)
    }
}

private val NonfictionNames = setOf("nonfiction", "non-fiction", "non fiction")
private const val CountAlpha = 0.7f
private const val PaneGenreCount = 6
private const val MosaicCount = 4
private val SplitCountGap = 8.dp
private val RailTileWidth = 124.dp
private val RowCoverWidth = 24.dp
private val TileCaptionGap = 6.dp
private val TileCheckInset = 8.dp
