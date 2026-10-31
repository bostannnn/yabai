package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryPlaceCountry
import com.example.unifiedreader.core.model.libraryTagKey
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelDefaults
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * Set in as a rail of country cards, most books first, running to the window edges and ending in
 * All places. A picked country's card turns `primaryContainer` with a check.
 */
@Composable
internal fun LibraryBrowsePlaceRail(
    countries: List<LibraryPlaceCountry>,
    picked: List<LibraryBrowseChip>,
    books: LibraryBrowseBooks,
    onToggle: (LibraryBrowseChip) -> Unit,
    onOpenPlaces: () -> Unit,
    modifier: Modifier = Modifier,
    onSortTag: ((LibraryBrowseChip) -> Unit)? = null,
) {
    val gutter = appGutter()
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .browseBleed(gutter),
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
    ) {
        items(items = countries.take(RailCountryCount), key = { country -> libraryTagKey(country.name) }) { country ->
            val chip = LibraryBrowseChip(LibraryBrowseRow.SET_IN, country.name)
            LibraryBrowsePlaceCard(
                country = country,
                picked = picked.any { it.sameAs(chip) },
                books = books,
                onClick = { onToggle(chip) },
                onLongClick = onSortTag?.let { sort -> { sort(chip) } },
            )
        }
        item(key = "all-places") {
            LibraryBrowseAllPlacesCard(count = countries.placeCount(), onClick = onOpenPlaces)
        }
    }
}

/** The cities of each picked country (or of a picked city's country), as chips under the rail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryBrowsePickedCities(
    countries: List<LibraryPlaceCountry>,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
) {
    val cities = countries.filter { country -> country.isOpen(picked) }.flatMap { country -> country.cities }
    if (cities.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CityChipGap),
        verticalArrangement = Arrangement.spacedBy(CityChipGap),
    ) {
        cities.forEach { city ->
            val chip = LibraryBrowseChip(LibraryBrowseRow.SET_IN, city.value)
            AppChip(
                selected = picked.any { it.sameAs(chip) },
                onClick = { onToggle(chip) },
                count = city.count,
                label = { Text(text = city.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/**
 * Set in in the 320 pane: a row per country with a cover, the picked one selected with its cities
 * under it, folded after a few; All places opens the rest.
 */
@Composable
internal fun LibraryBrowsePlaceList(
    countries: List<LibraryPlaceCountry>,
    picked: List<LibraryBrowseChip>,
    books: LibraryBrowseBooks,
    onToggle: (LibraryBrowseChip) -> Unit,
    onOpenPlaces: () -> Unit,
    onSortTag: ((LibraryBrowseChip) -> Unit)?,
) {
    // The picked countries stay in the list however few books they have.
    val shown = countries.take(PaneCountryCount) + countries.drop(PaneCountryCount).filter { it.isOpen(picked) }
    Column {
        shown.forEach { country ->
            LibraryBrowseCountryRows(
                country = country,
                picked = picked,
                books = books,
                onToggle = onToggle,
                onSortTag = onSortTag,
                showCities = country.isOpen(picked),
            )
        }
        if (countries.size > shown.size) {
            AppTextButton(onClick = onOpenPlaces) {
                Text(text = "All ${countries.placeCount()} places", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A country's row (a cover, its name and count) and, when [showCities], its cities under it. */
@Composable
internal fun LibraryBrowseCountryRows(
    country: LibraryPlaceCountry,
    picked: List<LibraryBrowseChip>,
    books: LibraryBrowseBooks,
    onToggle: (LibraryBrowseChip) -> Unit,
    showCities: Boolean,
    onSortTag: ((LibraryBrowseChip) -> Unit)? = null,
) {
    val chip = LibraryBrowseChip(LibraryBrowseRow.SET_IN, country.name)
    LibraryBrowseFacetRow(
        name = country.name,
        count = country.count,
        selected = picked.any { it.sameAs(chip) },
        onClick = { onToggle(chip) },
        leading = country.books.firstOrNull()?.let { item ->
            { LibraryBrowseSmallCover(item = item, books = books, modifier = Modifier.width(RowCoverWidth)) }
        },
        onLongClick = onSortTag?.let { sort -> { sort(chip) } },
    )
    if (showCities) {
        country.cities.forEach { city ->
            val cityChip = LibraryBrowseChip(LibraryBrowseRow.SET_IN, city.value)
            LibraryBrowseFacetRow(
                name = city.value,
                count = city.count,
                selected = picked.any { it.sameAs(cityChip) },
                onClick = { onToggle(cityChip) },
                indent = true,
                onLongClick = onSortTag?.let { sort -> { sort(cityChip) } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBrowsePlaceCard(
    country: LibraryPlaceCountry,
    picked: Boolean,
    books: LibraryBrowseBooks,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val stage = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    // Clipped like a list tile: the panel's large corner would cut into the caption's last line.
    Column(
        modifier = Modifier
            .width(PlaceCardWidth)
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius))
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { "Sort this tag" },
            )
            .semantics(mergeDescendants = true) { selected = picked },
        verticalArrangement = Arrangement.spacedBy(PlaceCaptionGap),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlaceStageHeight)
                .clip(AppPanelDefaults.shape)
                .background(stage),
        ) {
            country.books.take(PlaceCoverCount).forEachIndexed { index, item ->
                LibraryBrowseRingedCover(
                    item = item,
                    books = books,
                    width = PlaceCoverWidth,
                    ring = stage,
                    modifier = Modifier
                        .offset(x = PlaceCoverStart + PlaceCoverStep * index, y = PlaceCoverTop)
                        .zIndex((PlaceCoverCount - index).toFloat()),
                )
            }
            if (picked) {
                LibraryBrowsePickedCheck(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PlaceCheckInset)
                        .zIndex(PlaceCoverCount + 1f),
                )
            }
        }
        LibraryBrowseCardCaption(name = country.name, count = country.count, extra = country.cityLine())
    }
}

@Composable
private fun LibraryBrowseAllPlacesCard(count: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(PlaceCardWidth)
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius))
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(PlaceCaptionGap),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlaceStageHeight)
                .clip(AppPanelDefaults.shape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(AllPlacesIconSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = AppIcons.ArrowForward, contentDescription = null)
            }
        }
        Column {
            Text(
                text = "All $count places",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = "By region",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "New York · Los Angeles · +3": the country's top cities. */
private fun LibraryPlaceCountry.cityLine(): String? = cities.takeIf { it.isNotEmpty() }?.let { all ->
    val named = all.take(CityLineCount).joinToString(" · ") { city -> city.value }
    if (all.size > CityLineCount) "$named · +${all.size - CityLineCount}" else named
}

/** Whether a country shows its cities: it is picked, or one of its cities is. */
internal fun LibraryPlaceCountry.isOpen(picked: List<LibraryBrowseChip>): Boolean = picked.any { chip ->
    chip.row == LibraryBrowseRow.SET_IN &&
        (chip.value.equals(name, ignoreCase = true) || cities.any { city -> city.value.equals(chip.value, ignoreCase = true) })
}

/** How many places Browse can pick: the countries and their cities. */
internal fun List<LibraryPlaceCountry>.placeCount(): Int = size + sumOf { country -> country.cities.size }

private const val RailCountryCount = 12
private const val PaneCountryCount = 6
private const val PlaceCoverCount = 3
private const val CityLineCount = 2
private val PlaceCardWidth = 176.dp
private val PlaceStageHeight = 136.dp
private val PlaceCoverWidth = 68.dp
private val PlaceCoverStart = 14.dp
private val PlaceCoverStep = 38.dp
private val PlaceCoverTop = 15.dp
private val PlaceCheckInset = 10.dp
private val PlaceCaptionGap = 10.dp
private val AllPlacesIconSize = 48.dp
private val CityChipGap = 8.dp
private val RowCoverWidth = 24.dp
