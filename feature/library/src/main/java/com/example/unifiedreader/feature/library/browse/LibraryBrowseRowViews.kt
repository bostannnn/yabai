package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppLayout

/** What a row needs to draw itself in its own shape: the view, the books' covers and what a pick does. */
internal class LibraryBrowseRowContext(
    val view: LibraryBrowseView,
    val books: LibraryBrowseBooks,
    val actions: LibraryBrowseActions,
    /** Opens Places, every place Browse can pick. */
    val onOpenPlaces: () -> Unit,
)

/**
 * A row of the 320 pane on Expanded windows: its name over a compact form of its shape. Set in
 * and Genre are rows with a cover, When is the shelf on its side, Your reading is rows, the rest
 * are chips.
 */
@Composable
internal fun LibraryBrowsePaneGroup(
    row: LibraryBrowseRow,
    context: LibraryBrowseRowContext,
) {
    val view = context.view
    val actions = context.actions
    val facets = view.result.facets[row].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
        AppGroupLabel(text = row.label)
        when {
            row == LibraryBrowseRow.SET_IN && view.countries.isNotEmpty() -> LibraryBrowsePlaceList(
                countries = view.countries,
                picked = view.picked,
                books = context.books,
                onToggle = actions.onToggle,
                onOpenPlaces = context.onOpenPlaces,
                onSortTag = actions.onSortTag,
            )
            row == LibraryBrowseRow.WHEN && view.timeline.isNotEmpty() -> LibraryBrowseShelfList(
                bars = view.timeline,
                picked = view.picked,
                onToggle = actions.onToggle,
            )
            row == LibraryBrowseRow.GENRE -> {
                val genres = remember(facets, view.genreBooks) { LibraryBrowseGenres(facets, view.genreBooks) }
                LibraryBrowseGenreSplit(
                    genres = genres,
                    picked = view.picked,
                    onToggle = actions.onToggle,
                    modifier = Modifier.padding(bottom = AppLayout.headingGap),
                )
                LibraryBrowseGenreList(genres = genres, books = context.books, picked = view.picked, onToggle = actions.onToggle)
            }
            row == LibraryBrowseRow.READING -> Column {
                facets.forEach { facet ->
                    val chip = LibraryBrowseChip(row, facet.value)
                    LibraryBrowseFacetRow(
                        name = facet.value,
                        count = facet.count,
                        selected = view.picked.any { it.sameAs(chip) },
                        onClick = { actions.onToggle(chip) },
                    )
                }
            }
            else -> LibraryBrowseChipFlow(
                row = row,
                facets = facets,
                picked = view.picked,
                onToggle = actions.onToggle,
                onSortTag = actions.onSortTag,
            )
        }
    }
}

/**
 * The chosen row under the tabs on Medium and Compact windows, in its own shape: the country rail
 * with a picked country's cities under it, the shelf, the genre rail, chips for the rest.
 */
@Composable
internal fun LibraryBrowseTabContent(
    row: LibraryBrowseRow,
    context: LibraryBrowseRowContext,
) {
    val view = context.view
    val actions = context.actions
    val facets = view.result.facets[row].orEmpty()
    when {
        row == LibraryBrowseRow.SET_IN && view.countries.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            LibraryBrowsePlaceRail(
                countries = view.countries,
                picked = view.picked,
                books = context.books,
                onToggle = actions.onToggle,
                onOpenPlaces = context.onOpenPlaces,
                onSortTag = actions.onSortTag,
            )
            LibraryBrowsePickedCities(countries = view.countries, picked = view.picked, onToggle = actions.onToggle)
        }
        row == LibraryBrowseRow.WHEN && view.timeline.isNotEmpty() -> LibraryBrowseShelf(
            bars = view.timeline,
            picked = view.picked,
            onToggle = actions.onToggle,
        )
        row == LibraryBrowseRow.GENRE -> {
            val genres = remember(facets, view.genreBooks) { LibraryBrowseGenres(facets, view.genreBooks) }
            Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                LibraryBrowseGenreSplit(genres = genres, picked = view.picked, onToggle = actions.onToggle)
                if (genres.tiles.isNotEmpty()) {
                    LibraryBrowseGenreRail(genres = genres, books = context.books, picked = view.picked, onToggle = actions.onToggle)
                }
            }
        }
        else -> LibraryBrowseChipFlow(
            row = row,
            facets = facets,
            picked = view.picked,
            onToggle = actions.onToggle,
            onSortTag = actions.onSortTag,
        )
    }
}
