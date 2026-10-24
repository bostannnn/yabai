package com.example.unifiedreader.feature.library.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.feature.library.LibraryBrowseOption
import com.example.unifiedreader.feature.library.LibraryBrowseOptionSection
import com.example.unifiedreader.feature.library.LibraryBrowseOptionsMenu
import com.example.unifiedreader.feature.library.defaultLibraryGridCellSizeDp
import com.example.unifiedreader.feature.library.sanitizeLibraryGridCellSizeDp

/**
 * How See all shows each shelf: its sort and grid or list. Kept above the browser so the bar can
 * hold the options on a phone, and so each shelf keeps its own while the tabs switch.
 */
@Stable
class LibraryShelfBrowserViewState internal constructor(
    sortModes: Map<LibraryShelfBrowserKind, ShelfBrowserSortMode> = emptyMap(),
    listKinds: Set<LibraryShelfBrowserKind> = emptySet(),
) {
    private val sortModes = mutableStateMapOf<LibraryShelfBrowserKind, ShelfBrowserSortMode>().apply { putAll(sortModes) }
    private val layouts = mutableStateMapOf<LibraryShelfBrowserKind, Boolean>().apply {
        listKinds.forEach { kind -> put(kind, true) }
    }

    internal fun sortMode(kind: LibraryShelfBrowserKind): ShelfBrowserSortMode =
        sortModes[kind] ?: ShelfBrowserSortMode.ShelfOrder

    internal fun setSortMode(kind: LibraryShelfBrowserKind, mode: ShelfBrowserSortMode) {
        sortModes[kind] = mode
    }

    internal fun showsList(kind: LibraryShelfBrowserKind): Boolean = layouts[kind] == true

    internal fun setShowsList(kind: LibraryShelfBrowserKind, showList: Boolean) {
        layouts[kind] = showList
    }

    internal companion object {
        val Saver = listSaver<LibraryShelfBrowserViewState, String>(
            save = { state ->
                LibraryShelfBrowserKind.entries.flatMap { kind ->
                    listOf(kind.name, state.sortMode(kind).name, state.showsList(kind).toString())
                }
            },
            restore = { saved ->
                val rows = saved.chunked(3)
                LibraryShelfBrowserViewState(
                    sortModes = rows.associate { (kind, sort) ->
                        LibraryShelfBrowserKind.valueOf(kind) to ShelfBrowserSortMode.valueOf(sort)
                    },
                    listKinds = rows.filter { row -> row[2].toBoolean() }
                        .mapTo(mutableSetOf()) { row -> LibraryShelfBrowserKind.valueOf(row[0]) },
                )
            },
        )
    }
}

/** One per library context, like the browser's search. */
@Composable
fun rememberLibraryShelfBrowserViewState(browseContextKey: String): LibraryShelfBrowserViewState =
    rememberSaveable(browseContextKey, saver = LibraryShelfBrowserViewState.Saver) { LibraryShelfBrowserViewState() }

/**
 * The shelf's sort, layout and cover size: the pill at the end of See all's tabs, or with
 * [iconOnly] the icon in its bar on a phone.
 */
@Composable
fun LibraryShelfBrowserOptionsButton(
    shelves: LibraryShelvesState,
    kind: LibraryShelfBrowserKind,
    viewState: LibraryShelfBrowserViewState,
    gridCellSizeDp: Int,
    onGridCellSizeDpChanged: (Int) -> Unit,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val availableSortModes = remember(shelves, kind) {
        shelfBrowserSortModes(kind = kind, items = shelves.browserState(kind).books)
    }
    val sortMode = viewState.sortMode(kind).takeIf { it in availableSortModes } ?: ShelfBrowserSortMode.ShelfOrder
    val showList = viewState.showsList(kind)
    val cellSize = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
    LibraryBrowseOptionsMenu(
        label = sortMode.label(kind),
        selected = sortMode != ShelfBrowserSortMode.ShelfOrder || showList || cellSize != defaultLibraryGridCellSizeDp,
        optionSections = listOf(
            LibraryBrowseOptionSection(
                id = SortSectionId,
                title = "Sort",
                options = availableSortModes.map { mode ->
                    LibraryBrowseOption(id = mode.name, label = mode.label(kind), selected = mode == sortMode)
                },
            ),
            LibraryBrowseOptionSection(
                id = LayoutSectionId,
                title = "Layout",
                options = listOf(
                    LibraryBrowseOption(id = GridOptionId, label = "Grid", selected = !showList),
                    LibraryBrowseOption(id = ListOptionId, label = "List", selected = showList),
                ),
            ),
        ),
        gridCellSizeDp = cellSize,
        fadeReadItems = false,
        showFadeReadItems = false,
        showGridSizeOptions = !showList,
        onOptionSelected = { sectionId, optionId ->
            when (sectionId) {
                SortSectionId -> availableSortModes
                    .firstOrNull { mode -> mode.name == optionId }
                    ?.let { mode -> viewState.setSortMode(kind, mode) }
                LayoutSectionId -> viewState.setShowsList(kind, optionId == ListOptionId)
            }
        },
        onGridCellSizeDpChanged = onGridCellSizeDpChanged,
        onFadeReadItemsChanged = {},
        iconOnly = iconOnly,
        modifier = modifier,
    )
}

private const val SortSectionId = "sort"
private const val LayoutSectionId = "layout"
private const val GridOptionId = "grid"
private const val ListOptionId = "list"
