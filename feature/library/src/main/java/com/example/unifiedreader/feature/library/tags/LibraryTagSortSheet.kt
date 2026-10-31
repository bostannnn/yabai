package com.example.unifiedreader.feature.library.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.core.model.libraryPlaceRegionGuess
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.core.model.libraryTagGuess
import com.example.unifiedreader.core.model.libraryTagKey
import com.example.unifiedreader.feature.library.ResolvedLibraryCover
import com.example.unifiedreader.feature.library.browse.bookCountLabel
import com.example.unifiedreader.ui.components.AppBookRow
import com.example.unifiedreader.ui.components.AppBookRowDefaults
import com.example.unifiedreader.ui.components.AppBookRowLine
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSheetDivider
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The name a sort sheet is open for. */
@Immutable
data class LibraryTagSortTarget(
    val source: SourceType,
    val field: LibraryTagField,
    val name: String,
)

/**
 * A tag's sort sheet: the Browse row it shows in (Set in, When or About), a place's region, and up
 * to five of its books. It says the app's guess and never applies it; a choice saves at once and
 * holds for every book with the tag. A Location is a place already, so its sheet asks only for the
 * region. "All N in Browse" shows where [onBrowse] is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryTagSortSheet(
    target: LibraryTagSortTarget,
    onDismiss: () -> Unit,
    onBrowse: ((LibraryBrowseChip) -> Unit)? = null,
) {
    val context = LocalLibraryTagSorting.current
    val sorting = context.sortingFor(target.source) ?: LibraryTagSorting()
    val books by produceState(emptyList<ReadableItem>(), context.items, target) {
        value = withContext(Dispatchers.Default) { libraryTagBooks(context.items, target) }
    }
    val choice = sorting.choiceOf(target.field, target.name)
    val guess = libraryTagSortGuess(target)
    val shown = choice ?: libraryTagUnsorted(target.field)
    val sort = { next: LibraryTagChoice ->
        context.onSort(target.source, listOf(LibraryTagSortChange(target.field, target.name, next)))
    }
    AppSideSheet(onDismissRequest = onDismiss) {
        AppPanelHeader(
            title = target.name,
            subtitle = libraryTagSheetSubtitle(target, books.size, isNew = choice == null),
            onClose = { dismiss() },
        )
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            item(key = "why") {
                Text(
                    text = libraryTagSheetExplanation(target, guess, isNew = choice == null),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppLayout.inset),
                )
            }
            if (target.field == LibraryTagField.TAG) {
                item(key = "row") {
                    Column {
                        AppSheetLabel(text = "Browse shows it in")
                        AppSegmentedRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppLayout.inset),
                        ) {
                            LibraryTagRows.forEach { (kind, row) ->
                                AppSegment(
                                    selected = shown.kind == kind,
                                    onClick = {
                                        val region = if (kind == LibraryTagKind.PLACE) shown.region ?: guess?.region else null
                                        sort(LibraryTagChoice(kind, region))
                                    },
                                    label = { Text(text = row.label) },
                                )
                            }
                        }
                    }
                }
            }
            if (shown.kind == LibraryTagKind.PLACE) {
                item(key = "region") {
                    Column {
                        AppSheetLabel(text = "Region")
                        FlowRow(
                            modifier = Modifier.padding(horizontal = AppLayout.inset),
                            horizontalArrangement = Arrangement.spacedBy(RegionChipGap),
                            verticalArrangement = Arrangement.spacedBy(RegionChipGap),
                        ) {
                            LibraryPlaceRegion.entries.forEach { region ->
                                AppChip(
                                    selected = (shown.region ?: LibraryPlaceRegion.ELSEWHERE) == region,
                                    onClick = { sort(LibraryTagChoice(LibraryTagKind.PLACE, region)) },
                                    label = { Text(text = region.label) },
                                )
                            }
                        }
                    }
                }
            }
            if (books.isNotEmpty()) {
                item(key = "books") {
                    Column(modifier = Modifier.padding(top = AppLayout.headingGap)) {
                        AppSheetDivider()
                        AppSheetLabel(text = if (target.field == LibraryTagField.LOCATION) "Books set there" else "Books with this tag")
                    }
                }
                items(items = books.take(ShownBookCount), key = ReadableItem::id) { book ->
                    AppBookRow(
                        title = book.libraryPrimaryText(),
                        modifier = Modifier.padding(horizontal = AppLayout.inset),
                        cover = {
                            ResolvedLibraryCover(
                                item = book,
                                coverImageUrlProvider = context.coverImageUrlProvider,
                                isDownloaded = false,
                                isRead = false,
                                showReadBadge = false,
                                modifier = Modifier
                                    .width(AppBookRowDefaults.coverWidth)
                                    .aspectRatio(2f / 3f),
                            )
                        },
                        supporting = { AppBookRowLine(text = libraryTagBookLine(book, target)) },
                    )
                }
                if (onBrowse != null) {
                    item(key = "all") {
                        AppTextButton(
                            onClick = {
                                onBrowse(LibraryBrowseChip(shown.kind.browseRow, target.name))
                                dismiss()
                            },
                            // The button's own 12 inside makes its text start on the inset.
                            modifier = Modifier.padding(horizontal = AppLayout.headingGap),
                        ) {
                            Text(text = "All ${books.size} in Browse", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** The Browse row a kind of tag shows in. */
val LibraryTagKind.browseRow: LibraryBrowseRow
    get() = when (this) {
        LibraryTagKind.PLACE -> LibraryBrowseRow.SET_IN
        LibraryTagKind.ERA -> LibraryBrowseRow.WHEN
        LibraryTagKind.THEME -> LibraryBrowseRow.ABOUT
    }

private val LibraryTagRows = LibraryTagKind.entries.map { kind -> kind to kind.browseRow }

/** Where a name shows while nobody has sorted it: a tag in About, a Location in Elsewhere. */
internal fun libraryTagUnsorted(field: LibraryTagField): LibraryTagChoice = when (field) {
    LibraryTagField.TAG -> LibraryTagChoice(LibraryTagKind.THEME)
    LibraryTagField.LOCATION -> LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ELSEWHERE)
}

internal fun libraryTagSortGuess(target: LibraryTagSortTarget): LibraryTagChoice? = when (target.field) {
    LibraryTagField.TAG -> libraryTagGuess(target.name)
    LibraryTagField.LOCATION -> libraryPlaceRegionGuess(target.name)?.let { region -> LibraryTagChoice(LibraryTagKind.PLACE, region) }
}

/** "New tag · 28 books · Grimmory", "Location · 8 books · Kavita". */
internal fun libraryTagSheetSubtitle(target: LibraryTagSortTarget, books: Int, isNew: Boolean): String {
    val what = when (target.field) {
        LibraryTagField.TAG -> if (isNew) "New tag · " else ""
        LibraryTagField.LOCATION -> if (isNew) "New location · " else "Location · "
    }
    return "$what${bookCountLabel(books)} · ${target.source.displayName()}"
}

/** What the app guessed and why, and what happens while nobody has chosen. */
internal fun libraryTagSheetExplanation(target: LibraryTagSortTarget, guess: LibraryTagChoice?, isNew: Boolean): String =
    when (target.field) {
        LibraryTagField.TAG -> {
            val why = when (guess?.kind) {
                LibraryTagKind.PLACE ->
                    "The app guessed Set in · ${(guess.region ?: LibraryPlaceRegion.ELSEWHERE).label}, because ${target.name} is on its list of place names."
                LibraryTagKind.ERA -> "The app guessed When, because ${target.name} reads as a date or a period."
                else -> "The app has no guess for this tag."
            }
            val then = if (isNew) "It is in About until you choose." else "Your choice holds for every book with this tag."
            "$why $then"
        }
        LibraryTagField.LOCATION -> {
            val region = guess?.region
            val why = if (region != null) {
                "The app guessed ${region.label}."
            } else {
                "The app doesn't know the name, so it is in Elsewhere until you pick a region."
            }
            "${target.source.displayName()} lists this as a location, so it is a place. $why"
        }
    }

internal fun libraryTagBooks(items: List<ReadableItem>, target: LibraryTagSortTarget): List<ReadableItem> {
    val key = libraryTagKey(target.name)
    return items.filter { item ->
        item.sourceType == target.source && item.facetMetadata().let { facets ->
            val names = if (target.field == LibraryTagField.TAG) facets.tags else facets.locations
            names.any { name -> libraryTagKey(name) == key }
        }
    }
}

/** Who wrote it and its other tags, so you can tell what the tag means on it. */
private fun libraryTagBookLine(book: ReadableItem, target: LibraryTagSortTarget): String {
    val key = libraryTagKey(target.name)
    val others = book.facetMetadata().tags.filter { tag -> libraryTagKey(tag) != key }.take(OtherTagCount)
    return (listOfNotNull(book.librarySecondaryText()) + others).joinToString(" · ")
}

private val RegionChipGap = 8.dp
private const val ShownBookCount = 5
private const val OtherTagCount = 2
