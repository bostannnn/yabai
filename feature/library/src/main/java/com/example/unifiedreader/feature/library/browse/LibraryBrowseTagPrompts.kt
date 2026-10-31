package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryTagEntry
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.facetMetadata
import com.example.unifiedreader.core.model.libraryTagEntries
import com.example.unifiedreader.core.model.libraryTagKey
import com.example.unifiedreader.feature.library.tags.LibraryTagSortTarget
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.components.currentAppWindowWidth

/** What Browse's landing page says about tags nobody has sorted yet. */
@Immutable
internal sealed interface LibraryBrowseTagPrompt {
    /** Nothing is under Set in or When yet, and the app has guesses to check. */
    data class CheckGuesses(val places: Int, val times: Int) : LibraryBrowseTagPrompt

    /** Some tags are sorted; [count] new ones wait in About. */
    data class NewTags(val count: Int) : LibraryBrowseTagPrompt
}

internal fun libraryBrowseTagPrompt(items: List<ReadableItem>, sorting: LibraryTagSorting): LibraryBrowseTagPrompt? {
    val waiting = libraryTagEntries(items, sorting).filter(LibraryTagEntry::isNew)
    if (!sorting.hasPlacesOrTimes) {
        val places = waiting.count { it.field == LibraryTagField.TAG && it.guess?.kind == LibraryTagKind.PLACE }
        val times = waiting.count { it.guess?.kind == LibraryTagKind.ERA }
        if (places + times > 0) return LibraryBrowseTagPrompt.CheckGuesses(places, times)
    }
    val anySorted = sorting.tags.isNotEmpty() || sorting.locationRegions.isNotEmpty()
    return if (anySorted && waiting.isNotEmpty()) LibraryBrowseTagPrompt.NewTags(waiting.size) else null
}

/** Set in, When and About hold tags, so their chips open the tag's sort sheet on a long press. */
internal val LibraryBrowseRow.holdsTags: Boolean
    get() = this == LibraryBrowseRow.SET_IN || this == LibraryBrowseRow.WHEN || this == LibraryBrowseRow.ABOUT

/**
 * The name a held chip stands for: a Set in chip that is no place tag of these books but one of
 * their Locations is that Location; every other tag chip is a tag. Null for rows that hold no tags.
 */
internal fun libraryBrowseSortTarget(
    source: SourceType,
    items: List<ReadableItem>,
    chip: LibraryBrowseChip,
    sorting: LibraryTagSorting,
): LibraryTagSortTarget? {
    if (!chip.row.holdsTags) return null
    val key = libraryTagKey(chip.value)
    if (chip.row == LibraryBrowseRow.SET_IN) {
        val placeTag = sorting.kindOf(chip.value) == LibraryTagKind.PLACE &&
            items.any { item -> item.facetMetadata().tags.any { libraryTagKey(it) == key } }
        if (!placeTag && items.any { item -> item.facetMetadata().locations.any { libraryTagKey(it) == key } }) {
            return LibraryTagSortTarget(source, LibraryTagField.LOCATION, chip.value)
        }
    }
    return LibraryTagSortTarget(source, LibraryTagField.TAG, chip.value)
}

/** Before any tag is a place or a time: what the app guessed, Not now, and Check the guesses. */
@Composable
internal fun LibraryBrowseCheckGuessesPanel(
    prompt: LibraryBrowseTagPrompt.CheckGuesses,
    onCheck: () -> Unit,
    onNotNow: () -> Unit,
) {
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    AppPanel(modifier = Modifier.fillMaxWidth()) {
        val text = @Composable { modifier: Modifier ->
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                AppGroupLabel(text = "Places and times")
                Text(
                    text = libraryBrowseGuessesLine(prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val buttons = @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap), verticalAlignment = Alignment.CenterVertically) {
                AppTextButton(onClick = onNotNow) { Text(text = "Not now", fontWeight = FontWeight.SemiBold) }
                AppButton(onClick = onCheck) { Text(text = "Check the guesses") }
            }
        }
        if (compact) {
            Column(modifier = Modifier.padding(AppLayout.inset), verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                text(Modifier)
                buttons()
            }
        } else {
            Row(
                modifier = Modifier.padding(AppLayout.inset),
                horizontalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                text(Modifier.weight(1f))
                buttons()
            }
        }
    }
}

internal fun libraryBrowseGuessesLine(prompt: LibraryBrowseTagPrompt.CheckGuesses): String {
    val guessed = listOfNotNull(
        prompt.places.takeIf { it > 0 }?.let { "${appCount(it)} of your tags are places" },
        prompt.times.takeIf { it > 0 }?.let { "${appCount(it)} are times" },
    ).joinToString(" and ")
    return "The app guessed that $guessed. Check its guesses once, and Browse shows your books by country under Set in " +
        "and on a shelf under When."
}

/** Under About's heading, once some tags are sorted: how many new ones wait there, and Sort. */
@Composable
internal fun LibraryBrowseNewTagsLine(
    prompt: LibraryBrowseTagPrompt.NewTags,
    onSort: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (prompt.count == 1) {
                "1 new tag is in About until you sort it"
            } else {
                "${appCount(prompt.count)} new tags are in About until you sort them"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onSort != null) {
            AppTextButton(onClick = onSort) { Text(text = "Sort", fontWeight = FontWeight.SemiBold) }
        }
    }
}
