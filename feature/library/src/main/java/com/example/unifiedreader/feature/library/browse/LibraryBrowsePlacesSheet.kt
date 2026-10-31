package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryPlaceCountry
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.libraryTagKey
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppSearchField
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet

/**
 * Every place Browse can pick: the countries by region, the regions with most books first, each
 * with its cities under it. A search narrows them by country or city; a tap picks the place and
 * closes the sheet.
 */
@Composable
internal fun LibraryBrowsePlacesSheet(
    countries: List<LibraryPlaceCountry>,
    picked: List<LibraryBrowseChip>,
    books: LibraryBrowseBooks,
    onToggle: (LibraryBrowseChip) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim()
    val shown = if (needle.isEmpty()) {
        countries
    } else {
        countries.filter { country ->
            country.name.contains(needle, ignoreCase = true) || country.cities.any { it.value.contains(needle, ignoreCase = true) }
        }
    }
    val regions = shown.groupBy(LibraryPlaceCountry::region).entries
        .sortedWith(
            compareByDescending<Map.Entry<LibraryPlaceRegion, List<LibraryPlaceCountry>>> { (_, inRegion) -> inRegion.sumOf { it.count } }
                .thenBy { (region, _) -> region.ordinal },
        )
    AppSideSheet(onDismissRequest = onDismiss) {
        val pick: (LibraryBrowseChip) -> Unit = { chip ->
            onToggle(chip)
            dismiss()
        }
        AppPanelHeader(title = "Places", subtitle = "${countries.placeCount()} places", onClose = { dismiss() })
        AppSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Find a place",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppLayout.inset),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (regions.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = "No place called “$needle”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(AppLayout.inset),
                    )
                }
            }
            regions.forEach { (region, inRegion) ->
                item(key = "region:${region.name}") { AppSheetLabel(text = region.label) }
                items(items = inRegion, key = { country -> libraryTagKey(country.name) }) { country ->
                    Column(modifier = Modifier.padding(horizontal = AppLayout.inset)) {
                        LibraryBrowseCountryRows(
                            country = country,
                            picked = picked,
                            books = books,
                            onToggle = pick,
                            showCities = true,
                        )
                    }
                }
            }
        }
    }
}
