package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingListSummary
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppListCover
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppProgressBar
import com.example.unifiedreader.ui.components.AppSectionHeading
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.components.appGutter
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** Draws a book's cover filling the modifier it is given (the library's cover, with its cache). */
typealias ListCover = @Composable (item: ReadableItem, modifier: Modifier) -> Unit

/**
 * Every list in one place: the reading order under way as a card, Your lists (Want to read,
 * Stalled, then the user's), then Kavita's reading lists. [lists] come in the screen's order
 * ([orderedForScreen]); [searchQuery] narrows them by name or by a book in them.
 */
@Composable
fun ListsScreen(
    lists: List<UserList>,
    underWay: UserListUnderWay?,
    kavitaLists: List<ReadingListSummary>,
    itemsById: Map<String, ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
    searchQuery: String,
    cellSizeDp: Int,
    cover: ListCover,
    onOpenList: (UserList) -> Unit,
    onContinue: (UserListUnderWay) -> Unit,
    onOpenKavitaList: (ReadingListSummary) -> Unit,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = searchQuery.trim()
    val shownLists = remember(lists, query) {
        if (query.isEmpty()) lists else lists.filter { list -> list.matches(query) }
    }
    val shownKavitaLists = remember(kavitaLists, query) {
        if (query.isEmpty()) kavitaLists else kavitaLists.filter { it.title.contains(query, ignoreCase = true) }
    }
    val gridState = rememberLazyGridState()
    val scrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    LaunchedEffect(scrolled) { onScrolledChanged(scrolled) }
    val gutter = appGutter()
    // A heading after tiles sits a section gap below them; the grid's own gap is part of it.
    val sectionTop = AppLayout.sectionGap - AppLayout.gridGap
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cellSizeDp.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = contentTopInset + AppLayout.contentTop,
            bottom = AppLayout.contentBottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
        verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
    ) {
        if (underWay != null && query.isEmpty()) {
            item(key = "under-way", span = { GridItemSpan(maxLineSpan) }) {
                ListUnderWayCard(
                    underWay = underWay,
                    cover = cover,
                    onContinue = { onContinue(underWay) },
                    onOpen = { onOpenList(underWay.list) },
                    modifier = Modifier.padding(bottom = sectionTop),
                )
            }
        }
        item(key = "heading:own", span = { GridItemSpan(maxLineSpan) }) {
            AppSectionHeading(text = "Your lists", count = shownLists.size.toString())
        }
        items(shownLists, key = { list -> "list:${list.id}" }) { list ->
            UserListTile(
                name = list.name,
                caption = list.tileCaption(progressByItemId),
                coverItems = list.sortedEntries().take(4).map { entry ->
                    itemsById[entry.itemId] ?: entry.asReadableItem()
                },
                updatesItself = list.kind == UserListKind.Stalled,
                cover = cover,
                onClick = { onOpenList(list) },
            )
        }
        if (shownKavitaLists.isNotEmpty()) {
            item(key = "heading:kavita", span = { GridItemSpan(maxLineSpan) }) {
                AppSectionHeading(
                    text = "Kavita reading lists",
                    count = shownKavitaLists.size.toString(),
                    modifier = Modifier.padding(top = sectionTop),
                )
            }
            items(shownKavitaLists, key = { summary -> "kavita:${summary.id}" }) { summary ->
                KavitaListTile(summary = summary, cover = cover, onClick = { onOpenKavitaList(summary) })
            }
        }
        if (query.isNotEmpty() && shownLists.isEmpty() && shownKavitaLists.isEmpty()) {
            item(key = "no-match", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "No list or book in a list matches “$query”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The reading order under way: what is next, how far you are, and Continue. A tap elsewhere opens the list. */
@Composable
private fun ListUnderWayCard(
    underWay: UserListUnderWay,
    cover: ListCover,
    onContinue: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = underWay.next
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    AppPanel(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AppLayout.inset),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(88.dp)) {
                cover(next.item ?: next.entry.asReadableItem(), Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Your order · ${underWay.position} of ${underWay.count}",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = underWay.list.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Next: ${next.item?.libraryBookTitle() ?: next.entry.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                AppProgressBar(
                    fraction = underWay.finishedCount.toFloat() / underWay.count.coerceAtLeast(1),
                    modifier = Modifier.fillMaxWidth(),
                )
                // On a phone Continue goes under the text, which needs the whole width.
                if (compact) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AppButton(onClick = onContinue) {
                        Text(text = "Continue", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!compact) {
                AppButton(onClick = onContinue) {
                    Text(text = "Continue", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun UserListTile(
    name: String,
    caption: String,
    coverItems: List<ReadableItem>,
    updatesItself: Boolean,
    cover: ListCover,
    onClick: () -> Unit,
) {
    ListTileFrame(name = name, caption = caption, onClick = onClick) {
        Box {
            AppListCover(count = coverItems.size) { index, cellModifier -> cover(coverItems[index], cellModifier) }
            if (updatesItself) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = AppIcons.Tune, contentDescription = "Updates itself", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** A Kavita reading list: its own cover (Kavita draws a collage) and its issue count. */
@Composable
private fun KavitaListTile(
    summary: ReadingListSummary,
    cover: ListCover,
    onClick: () -> Unit,
) {
    val coverItem = remember(summary) {
        ReadableItem(
            id = summary.id,
            sourceType = SourceType.KAVITA,
            libraryId = summary.id,
            title = summary.title,
            format = FormatType.CBZ,
            coverUrl = summary.coverUrl,
        )
    }
    ListTileFrame(
        name = summary.title,
        caption = if (summary.itemCount == 1) "1 issue" else "${summary.itemCount} issues",
        onClick = onClick,
    ) {
        cover(coverItem, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
    }
}

@Composable
private fun ListTileFrame(
    name: String,
    caption: String,
    onClick: () -> Unit,
    coverContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        coverContent()
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun UserList.matches(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        entries.any { entry ->
            entry.title.contains(query, ignoreCase = true) ||
                entry.primaryText?.contains(query, ignoreCase = true) == true
        }
