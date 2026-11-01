package com.example.unifiedreader.feature.library.tags

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryTagEntry
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppChipDefaults
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppSearchField
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsDefaults
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTabPill
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings › Tags, inside the settings page's column: the sources as a segmented row; New, the tags
 * nobody has sorted (rows with the app's guess, or grouped by it with Accept all when there are many,
 * as the first time); then the sorted tags under Set in, When and About. A tap opens the tag's sheet.
 */
@Composable
fun LibraryTagsSettingsContent(
    source: SourceType,
    onSourceChanged: (SourceType) -> Unit,
) {
    val context = LocalLibraryTagSorting.current
    val sorting = context.sortingFor(source)
    var target by remember { mutableStateOf<LibraryTagSortTarget?>(null) }
    val open = { entry: LibraryTagEntry -> target = LibraryTagSortTarget(source, entry.field, entry.name) }
    AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
        SourceType.entries.forEach { sourceType ->
            AppSegment(
                selected = sourceType == source,
                onClick = { onSourceChanged(sourceType) },
                label = { Text(text = sourceType.displayName()) },
            )
        }
    }
    val model by produceState<LibraryTagsPageModel?>(null, context.items, source, sorting) {
        value = sorting?.let { known -> withContext(Dispatchers.Default) { libraryTagsPageModel(context.items, source, known) } }
    }
    val shown = model ?: return
    if (shown.entries.isEmpty()) {
        Text(
            text = "No ${source.displayName()} book has tags yet. Filters puts each tag in Set in, When or About once you sort it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (shown.new.isNotEmpty()) {
        if (shown.reviewsGuesses) {
            LibraryTagsGuessReview(
                source = source,
                entries = shown.new,
                onAcceptAll = { context.onSort(source, shown.new.map(LibraryTagEntry::acceptChange)) },
                onOpen = open,
            )
        } else {
            LibraryTagsNewRows(
                entries = shown.new,
                onAccept = { entry -> context.onSort(source, listOf(entry.acceptChange())) },
                onOpen = open,
            )
        }
    }
    if (shown.sorted.isNotEmpty()) {
        LibraryTagsSorted(entries = shown.sorted, onOpen = open)
    }
    target?.let { open ->
        LibraryTagSortSheet(target = open, onDismiss = { target = null })
    }
}

/** The first time: every tag under the app's guess, one line saying what happens, and Accept all. */
@Composable
private fun LibraryTagsGuessReview(
    source: SourceType,
    entries: List<LibraryTagEntry>,
    onAcceptAll: () -> Unit,
    onOpen: (LibraryTagEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
        Text(
            text = "${source.displayName()} sends tags as one list. Filters shows places under Set in and times under When, " +
                "so the app has guessed which of your tags are which. Check the guesses and tap any it got wrong. " +
                "Until you accept them, every tag stays in About.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            AppButton(onClick = onAcceptAll) { Text(text = "Accept all ${appCount(entries.size)} tags") }
            Text(
                text = "You can move any tag again later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
    val places = libraryTagsByRegion(entries) { entry -> entry.proposed }
    if (places.isNotEmpty()) {
        LibraryTagsSection(label = "Looks like a place", count = tagCountLabel(places.sumOf { it.second.size })) {
            places.forEach { (region, regionEntries) ->
                LibraryTagsLabelledChips(label = region.label, entries = regionEntries, onOpen = onOpen, foldedCount = RegionFoldedCount)
            }
        }
    }
    val (onTimeline, periods) = libraryTagsByTime(entries.filter { it.proposed.kind == LibraryTagKind.ERA })
    if (onTimeline.isNotEmpty() || periods.isNotEmpty()) {
        LibraryTagsSection(label = "Looks like a time", count = tagCountLabel(onTimeline.size + periods.size)) {
            if (onTimeline.isNotEmpty()) LibraryTagsLabelledChips(label = "On the timeline", entries = onTimeline, onOpen = onOpen)
            if (periods.isNotEmpty()) LibraryTagsLabelledChips(label = "Periods", entries = periods, onOpen = onOpen)
        }
    }
    val about = entries.filter { it.proposed.kind == LibraryTagKind.THEME }
    if (about.isNotEmpty()) {
        LibraryTagsSection(label = "No guess: stays in About", count = tagCountLabel(about.size)) {
            LibraryTagsChips(entries = about, onOpen = onOpen, foldedCount = AboutFoldedCount)
        }
    }
}

/** A few tags waiting: one row each with the app's guess and one action. */
@Composable
private fun LibraryTagsNewRows(
    entries: List<LibraryTagEntry>,
    onAccept: (LibraryTagEntry) -> Unit,
    onOpen: (LibraryTagEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
        LibraryTagsLabel(label = "New", count = tagCountLabel(entries.size))
        AppPanel(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                if (index > 0) AppSettingsRowDivider(inset = AppSettingsDefaults.horizontalPadding)
                AppSettingsRow(
                    title = entry.name,
                    supporting = libraryTagNewRowLine(entry),
                    onClick = { onOpen(entry) },
                    showChevron = false,
                    trailing = {
                        AppTextButton(
                            onClick = {
                                if (entry.guess == null && entry.field == LibraryTagField.LOCATION) onOpen(entry) else onAccept(entry)
                            },
                        ) {
                            Text(text = libraryTagNewRowAction(entry), fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            }
        }
    }
}

/** The sorted tags as Set in, When and About tabs, with a field that finds one by name. */
@Composable
private fun LibraryTagsSorted(
    entries: List<LibraryTagEntry>,
    onOpen: (LibraryTagEntry) -> Unit,
) {
    var tabName by rememberSaveable { mutableStateOf(LibraryTagKind.PLACE.name) }
    var query by rememberSaveable { mutableStateOf("") }
    val tab = LibraryTagKind.entries.firstOrNull { it.name == tabName } ?: LibraryTagKind.PLACE
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
        val tabs = @Composable {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TabGap),
            ) {
                LibraryTagKind.entries.forEach { kind ->
                    AppTabPill(
                        label = kind.browseRow.label,
                        selected = kind == tab,
                        onClick = { tabName = kind.name },
                        count = entries.count { it.shownAs.kind == kind },
                    )
                }
            }
        }
        val find = @Composable { modifier: Modifier ->
            AppSearchField(query = query, onQueryChange = { query = it }, placeholder = "Find a tag", modifier = modifier)
        }
        if (compact) {
            tabs()
            find(Modifier.fillMaxWidth())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                Row(modifier = Modifier.weight(1f)) { tabs() }
                find(Modifier.width(FindFieldWidth))
            }
        }
        val shown = entries.filter { entry ->
            entry.shownAs.kind == tab && (query.isBlank() || entry.name.contains(query.trim(), ignoreCase = true))
        }
        when (tab) {
            LibraryTagKind.PLACE -> LibraryTagsRegionPanels(entries = shown, columns = if (compact) 1 else 2, onOpen = onOpen)
            LibraryTagKind.ERA -> {
                val (onTimeline, periods) = libraryTagsByTime(shown)
                if (onTimeline.isNotEmpty() || periods.isNotEmpty()) {
                    AppPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(AppLayout.inset), verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                            if (onTimeline.isNotEmpty()) LibraryTagsLabelledChips(label = "On the timeline", entries = onTimeline, onOpen = onOpen)
                            if (periods.isNotEmpty()) LibraryTagsLabelledChips(label = "Periods", entries = periods, onOpen = onOpen)
                        }
                    }
                }
            }
            LibraryTagKind.THEME -> LibraryTagsChips(entries = shown, onOpen = onOpen, foldedCount = AboutFoldedCount)
        }
    }
}

/** Set in's regions as panels in [columns], each stacking at its own height. */
@Composable
private fun LibraryTagsRegionPanels(
    entries: List<LibraryTagEntry>,
    columns: Int,
    onOpen: (LibraryTagEntry) -> Unit,
) {
    val regions = libraryTagsByRegion(entries) { entry -> entry.shownAs }
    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
        repeat(columns) { column ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                regions.filterIndexed { index, _ -> index % columns == column }.forEach { (region, regionEntries) ->
                    AppPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(AppLayout.inset), verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                            LibraryTagsLabel(label = region.label, count = if (regionEntries.size == 1) "1 place" else "${appCount(regionEntries.size)} places")
                            LibraryTagsChips(entries = regionEntries, onOpen = onOpen, foldedCount = RegionPanelFoldedCount)
                        }
                    }
                }
            }
        }
    }
}

/** A group label with its count, then a panel holding [content]. */
@Composable
private fun LibraryTagsSection(
    label: String,
    count: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
        LibraryTagsLabel(label = label, count = count)
        AppPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AppLayout.inset), verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                content()
            }
        }
    }
}

@Composable
private fun LibraryTagsLabel(label: String, count: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap), verticalAlignment = Alignment.CenterVertically) {
        AppGroupLabel(text = label)
        Text(text = count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A sheet-style label over (Compact) or beside (wider) its chips: a region, the timeline, the periods. */
@Composable
private fun LibraryTagsLabelledChips(
    label: String,
    entries: List<LibraryTagEntry>,
    onOpen: (LibraryTagEntry) -> Unit,
    foldedCount: Int = Int.MAX_VALUE,
) {
    val text = @Composable { modifier: Modifier ->
        Text(
            text = "$label ${appCount(entries.size)}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
    if (currentAppWindowWidth() == AppWindowWidth.Compact) {
        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
            text(Modifier)
            LibraryTagsChips(entries = entries, onOpen = onOpen, foldedCount = foldedCount)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            text(Modifier.width(LabelColumnWidth).padding(top = AppLayout.headingGap))
            Column(modifier = Modifier.weight(1f)) {
                LibraryTagsChips(entries = entries, onOpen = onOpen, foldedCount = foldedCount)
            }
        }
    }
}

/** Tags as chips with their book counts, most first, folded after [foldedCount]; a tap opens one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryTagsChips(
    entries: List<LibraryTagEntry>,
    onOpen: (LibraryTagEntry) -> Unit,
    foldedCount: Int,
) {
    var unfolded by rememberSaveable(entries.size) { mutableStateOf(false) }
    val shown = if (unfolded) entries else entries.take(foldedCount)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
        verticalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        shown.forEach { entry ->
            AppChip(
                selected = false,
                onClick = { onOpen(entry) },
                count = entry.count,
                label = { Text(text = entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
        val hidden = entries.size - shown.size
        if (hidden > 0) {
            AppTextButton(onClick = { unfolded = true }, modifier = Modifier.height(AppChipDefaults.height)) {
                Text(text = "${appCount(hidden)} more", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun tagCountLabel(count: Int): String = if (count == 1) "1 tag" else "${appCount(count)} tags"

private val ChipGap = 8.dp
private val TabGap = 4.dp
private val FindFieldWidth = 240.dp
private val LabelColumnWidth = 150.dp
private const val RegionFoldedCount = 8
private const val RegionPanelFoldedCount = 12
private const val AboutFoldedCount = 24
