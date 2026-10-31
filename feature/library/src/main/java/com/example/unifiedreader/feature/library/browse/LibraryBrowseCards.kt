package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibraryCover
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** What Browse's cards need to draw books: where their covers are and how far you have read them. */
@Immutable
internal class LibraryBrowseBooks(
    val coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    val progressByItemId: Map<String, ItemProgressSummary>,
)

/** A book's cover at a card's size: on a place card, in a list cover, at the start of a row. */
@Composable
internal fun LibraryBrowseSmallCover(
    item: ReadableItem,
    books: LibraryBrowseBooks,
    modifier: Modifier = Modifier,
) {
    LibraryCover(
        item = item,
        coverUrl = books.coverImageUrlProvider(item, LibraryCoverImageVariant.ShelfPreview),
        imageVariant = LibraryCoverImageVariant.ShelfPreview,
        showReadBadge = false,
        modifier = modifier.aspectRatio(2f / 3f),
    )
}

/**
 * A cover ringed in [ring]'s colour, for covers that overlap: the ring keeps them apart without a
 * shadow, since covers sit flat.
 */
@Composable
internal fun LibraryBrowseRingedCover(
    item: ReadableItem,
    books: LibraryBrowseBooks,
    width: Dp,
    ring: Color,
    modifier: Modifier = Modifier,
) {
    val radius = UnifiedReaderThemeTokens.presentation.coverCornerRadius
    Box(
        modifier = modifier
            .width(width + CoverRing * 2)
            .background(ring, RoundedCornerShape(radius + CoverRing))
            .padding(CoverRing),
    ) {
        LibraryBrowseSmallCover(item = item, books = books, modifier = Modifier.fillMaxWidth())
    }
}

/** The check on a picked card's corner. */
@Composable
internal fun LibraryBrowsePickedCheck(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(CheckSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(CheckIconSize),
        )
    }
}

/** A card's name and "536 books" under it, as the Lists screen's tiles have them, and an optional third line. */
@Composable
internal fun LibraryBrowseCardCaption(
    name: String,
    count: Int,
    modifier: Modifier = Modifier,
    extra: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = bookCountLabel(count),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        extra?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A value as one row of the 320 pane or a sheet: [leading] (a cover), the name and its count; picked,
 * it fills with `primaryContainer` and ends in a check. [indent] sets a city under its country, and
 * [middle] (When's standing books) sits between a [nameWidth]-wide name and the count. The fill
 * reaches [bleed] past the column on both sides, so the text stays on the column's edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryBrowseFacetRow(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    indent: Boolean = false,
    bleed: Dp = FacetRowBleed,
    onLongClick: (() -> Unit)? = null,
    middle: (@Composable () -> Unit)? = null,
    nameWidth: Dp = FacetNameWidth,
    height: Dp = if (indent) CityRowHeight else FacetRowHeight,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .browseBleed(bleed)
            .height(height)
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.controlCornerRadius))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { "Sort this tag" },
            )
            .semantics { this.selected = selected }
            .padding(start = if (indent) bleed + CityIndent else bleed, end = bleed),
        horizontalArrangement = Arrangement.spacedBy(FacetRowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (indent) 13.5.sp else 14.sp),
            color = if (indent && !selected) colors.onSurfaceVariant else content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (middle == null) Modifier.weight(1f) else Modifier.width(nameWidth),
        )
        if (middle != null) {
            Box(modifier = Modifier.weight(1f)) { middle() }
        }
        Text(
            text = appCount(count),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) content else colors.onSurfaceVariant,
        )
        if (selected) {
            Icon(imageVector = AppIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(CheckIconSize))
        }
    }
}

/**
 * Lets a rail or a row's fill run [by] past its column on both sides (a rail to the window edges);
 * the content's own padding brings it back to the column's line.
 */
internal fun Modifier.browseBleed(by: Dp): Modifier = layout { measurable, constraints ->
    val extra = (by * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

private val CoverRing = 2.dp
private val CheckSize = 28.dp
private val CheckIconSize = 18.dp
private val FacetRowHeight = 44.dp
private val CityRowHeight = 34.dp
private val CityIndent = 36.dp
private val FacetRowGap = 12.dp
private val FacetRowBleed = 12.dp
private val FacetNameWidth = 92.dp
