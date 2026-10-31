package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseFacet
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.LibraryCoverActionCard
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryCoverProgressBar
import com.example.unifiedreader.feature.library.LibraryGridHeading
import com.example.unifiedreader.feature.library.LibraryGridHeadingText
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.LibraryItemPageButton
import com.example.unifiedreader.feature.library.isComplete
import com.example.unifiedreader.feature.library.isDownloaded
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.feature.library.shouldShowCoverProgress
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppChipDefaults
import com.example.unifiedreader.ui.components.AppEmptyState
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppTabPill
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.appCount

/** A row of the chip pane: its name over its chips, most books first, folded after a dozen. */
@Composable
internal fun LibraryBrowseChipGroup(
    row: LibraryBrowseRow,
    facets: List<LibraryBrowseFacet>,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
    modifier: Modifier = Modifier,
    onSortTag: ((LibraryBrowseChip) -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
        AppGroupLabel(text = row.label)
        LibraryBrowseChipFlow(row = row, facets = facets, picked = picked, onToggle = onToggle, onSortTag = onSortTag)
    }
}

/**
 * A row's chips with counts; the picked ones selected. "N more" unfolds the rest. A tag row's chip
 * opens the tag's sort sheet on a long press when [onSortTag] is given.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryBrowseChipFlow(
    row: LibraryBrowseRow,
    facets: List<LibraryBrowseFacet>,
    picked: List<LibraryBrowseChip>,
    onToggle: (LibraryBrowseChip) -> Unit,
    foldedCount: Int = FoldedChipCount,
    moreLabel: (Int) -> String = { hidden -> "$hidden more" },
    // Tells apart two flows of one row (Set in's regions), so each unfolds on its own.
    stateKey: String = "",
    onSortTag: ((LibraryBrowseChip) -> Unit)? = null,
) {
    val sortTag = onSortTag?.takeIf { row.holdsTags }
    var unfolded by rememberSaveable(row, stateKey) { mutableStateOf(false) }
    val shown = if (unfolded) facets else facets.take(foldedCount)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
        verticalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        shown.forEach { facet ->
            val chip = LibraryBrowseChip(row, facet.value)
            AppChip(
                selected = picked.any { it.sameAs(chip) },
                onClick = { onToggle(chip) },
                count = facet.count,
                label = { Text(text = facet.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                onLongClick = sortTag?.let { sort -> { sort(chip) } },
                onLongClickLabel = "Sort this tag",
            )
        }
        val hidden = facets.size - shown.size
        if (hidden > 0) {
            AppTextButton(onClick = { unfolded = true }, modifier = Modifier.height(AppChipDefaults.height)) {
                Text(text = moreLabel(hidden), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The picked chips (each one's × takes it off), Clear, and how many books have them all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryBrowsePickedHeader(
    view: LibraryBrowseView,
    actions: LibraryBrowseActions,
    belowChips: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            verticalArrangement = Arrangement.spacedBy(ChipGap),
        ) {
            view.picked.forEach { chip ->
                LibraryBrowsePickedChip(chip = chip, onRemove = { actions.onRemove(chip) })
            }
            AppTextButton(onClick = actions.onClear, modifier = Modifier.height(AppChipDefaults.height)) {
                Text(text = "Clear", fontWeight = FontWeight.SemiBold)
            }
        }
        belowChips?.invoke()
        LibraryGridHeadingText(heading = LibraryGridHeading(title = bookCountLabel(view.result.items.size), count = null))
        if (view.result.items.isEmpty()) {
            AppEmptyState(
                title = "No books have all of these",
                body = "Take a chip off to see more.",
            )
        }
    }
}

/** "About Espionage", the row's name quieter, with a × that takes it off. */
@Composable
private fun LibraryBrowsePickedChip(
    chip: LibraryBrowseChip,
    onRemove: () -> Unit,
) {
    val quiet = LocalContentColor.current.copy(alpha = 0.72f)
    AppChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = quiet, fontWeight = FontWeight.Medium)) { append(chip.row.label) }
                    append(" ")
                    append(chip.value)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = "Take off ${chip.value}",
                modifier = Modifier.size(AppChipDefaults.iconSize),
            )
        },
    )
}

/**
 * Browse on a phone or a portrait tablet: the picked chips, then the rows as tabs with the chosen
 * row's chips under them, then the count. The grid follows.
 */
@Composable
internal fun LibraryBrowseRowTabs(
    view: LibraryBrowseView,
    rows: List<LibraryBrowseRow>,
    actions: LibraryBrowseActions,
) {
    val rowsWithChips = rows.filter { row -> view.result.facets[row].orEmpty().isNotEmpty() }
    var chosenName by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = rowsWithChips.firstOrNull { it.name == chosenName } ?: rowsWithChips.firstOrNull()
    LibraryBrowsePickedHeader(
        view = view,
        actions = actions,
        belowChips = if (chosen == null) {
            null
        } else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(TabGap),
                    ) {
                        rowsWithChips.forEach { row ->
                            AppTabPill(
                                label = row.label,
                                selected = row == chosen,
                                onClick = { chosenName = row.name },
                            )
                        }
                    }
                    LibraryBrowseChipFlow(
                        row = chosen,
                        facets = view.result.facets[chosen].orEmpty(),
                        picked = view.picked,
                        onToggle = actions.onToggle,
                        onSortTag = actions.onSortTag,
                    )
                }
            }
        },
    )
}

/** A result's tile: the library's cover card, its "•••" opening the book page. */
@Composable
internal fun LibraryBrowseCover(
    item: ReadableItem,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    imageVariant: LibraryCoverImageVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = actionContext.progressByItemId[item.id]
    LibraryCoverActionCard(
        item = item,
        title = item.libraryPrimaryText(),
        supportingText = item.librarySecondaryText(),
        coverImageUrlProvider = coverImageUrlProvider,
        imageVariant = imageVariant,
        isDownloaded = actionContext.isDownloaded(item),
        isRead = progress?.isComplete == true,
        onClick = onClick,
        titleStyle = MaterialTheme.typography.titleSmall,
        metadataTopPadding = 8.dp,
        menuContent = {
            LibraryItemPageButton(
                item = item,
                actionContext = actionContext,
                buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
            )
        },
        modifier = modifier.fillMaxWidth(),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f),
        coverOverlay = {
            progress?.takeIf { it.shouldShowCoverProgress }?.let { shown ->
                LibraryCoverProgressBar(
                    fraction = shown.fraction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        },
    )
}

internal fun bookCountLabel(count: Int): String = if (count == 1) "1 book" else "${appCount(count)} books"

private val ChipGap = 8.dp
private val TabGap = 4.dp
private const val FoldedChipCount = 12
