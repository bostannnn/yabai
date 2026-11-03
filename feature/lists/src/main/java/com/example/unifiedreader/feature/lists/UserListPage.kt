@file:OptIn(ExperimentalLayoutApi::class)

package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.unifiedreader.ui.components.AppRoundCheck
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ListRules
import com.example.unifiedreader.core.model.pill
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppListCover
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppScreenTitle
import com.example.unifiedreader.ui.components.AppSectionHeading
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSwitch
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.appGutter

/**
 * One list, as the library shows books: its mosaic, name, description and facts, Continue (or
 * Read) and its second button (Change filters, Add books or Edit), the Order chip and Hide
 * finished, then its books as covers in the library's grid ([cellSizeDp] wide at least), numbered
 * when the list is in Your order. A book that left the library keeps its cover, greyed.
 * [selection] holds the picked books while selecting ([canSelect]: a list you pick); a long-press
 * starts it.
 */
@Composable
fun UserListPage(
    list: UserList,
    books: List<UserListBook>,
    cover: ListCover,
    onOpenBook: (UserListBook) -> Unit,
    onSetOrder: (UserListOrder) -> Unit,
    onSetHideFinished: (Boolean) -> Unit,
    onEdit: () -> Unit,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cellSizeDp: Int = UserListPageDefaults.cellSizeDp,
    // Keep the next 3 downloaded: its line, or null where the list can't keep books downloaded.
    keepDownloadedLine: String? = null,
    onSetKeepDownloaded: (Boolean) -> Unit = {},
    // Add books in the library; null where books aren't added by hand.
    onAddBooks: (() -> Unit)? = null,
    canSelect: Boolean = false,
    selection: Set<String>? = null,
    onSelectionChange: (Set<String>?) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val scrolled by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    LaunchedEffect(scrolled) { onScrolledChanged(scrolled) }
    val next = remember(books) { books.nextToRead() }
    val numbered = list.isReadingOrder
    val shown = remember(books, list.hideFinished) {
        books.withIndex().filter { (_, book) -> !list.hideFinished || !book.isFinished }
    }
    val mixesSources = remember(books) { books.map { it.entry.sourceType }.distinct().size > 1 }
    val gutter = appGutter()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = cellSizeDp.dp),
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
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            UserListHeader(
                list = list,
                books = books,
                next = next,
                cover = cover,
                onOpenBook = onOpenBook,
                onEdit = onEdit,
                onAddBooks = onAddBooks,
                modifier = Modifier.padding(bottom = AppLayout.sectionGap - AppLayout.gridGap),
            )
        }
        if (keepDownloadedLine != null) {
            item(key = "keep-downloaded", span = { GridItemSpan(maxLineSpan) }) {
                AppPanel(modifier = Modifier.fillMaxWidth().padding(bottom = AppLayout.sectionGap - AppLayout.gridGap)) {
                    AppSettingsRow(
                        title = "Keep the next $KeepDownloadedCount downloaded",
                        supporting = keepDownloadedLine,
                        icon = AppIcons.Download,
                        onClick = { onSetKeepDownloaded(!list.keepNextDownloaded) },
                        showChevron = false,
                        trailing = {
                            AppSwitch(checked = list.keepNextDownloaded, onCheckedChange = onSetKeepDownloaded)
                        },
                    )
                }
            }
        }
        item(key = "books-heading", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                    AppSectionHeading(
                        text = if (list.kind == UserListKind.ServerCollection) "Series" else "Books",
                        count = shown.size.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    if (list.isEditable && list.orders.size > 1) {
                        UserListOrderChip(order = list.order, options = list.orders, onSetOrder = onSetOrder)
                    }
                    AppChip(
                        selected = list.hideFinished,
                        onClick = { onSetHideFinished(!list.hideFinished) },
                        label = { Text("Hide finished") },
                    )
                }
                if (shown.isEmpty()) {
                    UserListEmpty(list = list, books = books, onAddBooks = onAddBooks)
                }
            }
        }
        items(items = shown, key = { (position, book) -> "book:$position:${book.entry.itemId}" }) { (position, book) ->
            val itemId = book.entry.itemId
            UserListBookTile(
                book = book,
                number = if (numbered) position + 1 else null,
                isNext = book == next && numbered,
                showSource = mixesSources,
                cover = cover,
                isSeries = book.item?.isKavitaSeriesContainer() == true,
                selected = selection?.contains(itemId),
                onClick = {
                    when {
                        selection != null -> onSelectionChange(if (itemId in selection) selection - itemId else selection + itemId)
                        !book.isMissing -> onOpenBook(book)
                    }
                },
                onLongClick = if (canSelect && selection == null) ({ onSelectionChange(setOf(itemId)) }) else null,
            )
        }
    }
}

/** What an empty list says, and Add books for a list you pick. */
@Composable
private fun UserListEmpty(list: UserList, books: List<UserListBook>, onAddBooks: (() -> Unit)?) {
    val server = list.server?.sourceType?.displayName()
    AppPanel(modifier = Modifier.fillMaxWidth()) {
        AppSettingsRow(
            title = if (books.isNotEmpty()) "Every book here is finished" else "No books yet",
            supporting = when {
                books.isNotEmpty() -> "Hide finished is on."
                list.kind == UserListKind.Stalled -> "Nothing has stalled: every book you started was opened in the last three weeks."
                list.kind == UserListKind.Smart -> "Nothing in your library matches these filters now."
                list.kind == UserListKind.ServerMagicShelf -> "Nothing on $server matches these rules now."
                list.kind == UserListKind.ServerCollection -> "Nothing in this collection that your library holds."
                onAddBooks != null -> "Pick them in the library: they come straight here. Or long-press covers anywhere in the library and choose Add to list."
                else -> "Nothing here yet, or nothing your library holds."
            },
        )
    }
}

@Composable
private fun UserListHeader(
    list: UserList,
    books: List<UserListBook>,
    next: UserListBook?,
    cover: ListCover,
    onOpenBook: (UserListBook) -> Unit,
    onEdit: () -> Unit,
    onAddBooks: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AppLayout.sectionGap), verticalAlignment = Alignment.Bottom) {
        val coverItems = books.take(4).map { book -> book.item ?: book.entry.asReadableItem() }
        Box(modifier = Modifier.width(if (compact) UserListPageDefaults.mosaicWidthCompact else UserListPageDefaults.mosaicWidth)) {
            AppListCover(count = coverItems.size) { index, cellModifier -> cover(coverItems[index], cellModifier) }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = list.headerCaption(books),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppScreenTitle(text = list.name)
            list.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val rules = list.smart?.rules
            if (rules != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rules.pills().forEach { pill ->
                        AppChip(selected = false, onClick = { if (list.isEditable) onEdit() }, label = { Text(pill) })
                    }
                }
            } else if (list.kind == UserListKind.ServerMagicShelf) {
                Text(
                    text = "Its rules ask something the app can't show yet; change them in ${list.server?.sourceType?.displayName()}'s web app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (list.kind == UserListKind.ServerCollection) books.seriesSummaryLine() else books.summaryLine(),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Continue with a long title and Edit wrap onto two lines on a phone.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // A collection holds series: its rows open them, there is no next book to read.
                if (next != null && list.kind != UserListKind.ServerCollection) {
                    AppButton(onClick = { onOpenBook(next) }) {
                        Text(
                            text = if (next.progress?.fraction?.let { it > 0f } == true) "Continue" else "Read",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = " · ${next.item?.libraryBookTitle() ?: next.entry.title}",
                            maxLines = 1,
                        )
                    }
                }
                // A smart list changes its filters; a list you pick takes books from the library; a
                // collection (series) keeps its editor. A magic shelf whose rules the app can't read
                // is changed in its server's web app.
                when {
                    !list.isEditable -> Unit
                    list.smart != null -> AppTonalButton(onClick = onEdit) {
                        Icon(imageVector = AppIcons.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Change filters")
                    }
                    onAddBooks != null -> AppTonalButton(onClick = onAddBooks) {
                        Icon(imageVector = AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Add books")
                    }
                    list.kind != UserListKind.ServerMagicShelf -> AppTonalButton(onClick = onEdit) {
                        Icon(imageVector = AppIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Edit")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserListOrderChip(
    order: UserListOrder,
    options: List<UserListOrder>,
    onSetOrder: (UserListOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text(order.label) },
            leadingIcon = { Icon(imageVector = AppIcons.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        AppMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                AppMenuChoiceItem(
                    text = option.label,
                    selected = option == order,
                    onClick = {
                        expanded = false
                        onSetOrder(option)
                    },
                )
            }
        }
    }
}

/**
 * A book in a list as a cover, like the library's: its number on the cover in Your order, a check
 * when finished, the title and one line under it. Picking shows a round check on the cover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserListBookTile(
    book: UserListBook,
    number: Int?,
    isNext: Boolean,
    showSource: Boolean,
    cover: ListCover,
    isSeries: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val item: ReadableItem = book.item ?: book.entry.asReadableItem()
    val title = book.item?.libraryBookTitle() ?: book.entry.title
    Column(
        modifier = Modifier
            .alpha(
                when {
                    book.isMissing -> 0.55f
                    book.isFinished && selected == null -> 0.7f
                    else -> 1f
                },
            )
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.controlCornerRadius))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { if (selected != null) this.selected = selected },
        verticalArrangement = Arrangement.spacedBy(UserListPageDefaults.titleGap),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            cover(item, Modifier.fillMaxSize())
            if (number != null) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(UserListPageDefaults.badgeInset)
                        .background(Color.Black.copy(alpha = UserListPageDefaults.badgeAlpha), CircleShape)
                        .padding(horizontal = UserListPageDefaults.badgePaddingH, vertical = UserListPageDefaults.badgePaddingV),
                )
            }
            when {
                selected != null -> AppRoundCheck(
                    checked = selected,
                    ringColor = Color.White,
                    ringBackdrop = Color.Black.copy(alpha = UserListPageDefaults.ringBackdropAlpha),
                    modifier = Modifier.align(Alignment.TopEnd).padding(UserListPageDefaults.badgeInset),
                )
                book.isFinished -> Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UserListPageDefaults.badgeInset)
                        .size(UserListPageDefaults.checkSize)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = "Finished",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(UserListPageDefaults.checkIconSize),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = (if (isSeries) book.seriesRowLine() else book.rowLine(showSource)).let { line -> if (isNext) "Next · $line" else line },
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

object UserListPageDefaults {
    /** The narrowest a book's cover gets in a list, where the library's own size isn't given. */
    const val cellSizeDp: Int = 140
    val mosaicWidth: Dp = 96.dp
    val mosaicWidthCompact: Dp = 72.dp
    val titleGap: Dp = 6.dp
    val badgeInset: Dp = 8.dp
    val badgePaddingH: Dp = 7.dp
    val badgePaddingV: Dp = 2.dp
    const val badgeAlpha: Float = 0.62f
    const val ringBackdropAlpha: Float = 0.25f
    val checkSize: Dp = 24.dp
    val checkIconSize: Dp = 16.dp
}

/** A series row's line in a collection: "24 issues · Finished", "12 issues · 40%". */
private fun UserListBook.seriesRowLine(): String {
    if (isMissing) return "Not in your library"
    val issues = item?.issueCount?.let { count -> if (count == 1) "1 issue" else "$count issues" }
    val reading = when {
        isFinished -> "Finished"
        (progress?.fraction ?: 0f) > 0f -> progress?.percentLabel
        else -> null
    }
    return listOfNotNull(issues, reading).joinToString(" · ").ifEmpty { "Series" }
}

/** A collection's facts: "9 series · 212 issues". */
private fun List<UserListBook>.seriesSummaryLine(): String {
    val series = if (size == 1) "1 series" else "$size series"
    val issues = sumOf { it.item?.issueCount ?: 0 }
    return if (issues > 0) "$series · $issues issues" else series
}

/** "Your list · Kavita and Local", "Built in", "Smart list · updates itself", "Grimmory shelf · on the server". */
private fun UserList.headerCaption(books: List<UserListBook>): String = when (kind) {
    UserListKind.Stalled -> "Updates itself · every source"
    UserListKind.Smart -> "Smart list · updates itself"
    UserListKind.ServerMagicShelf -> "${server?.sourceType?.displayName().orEmpty()} magic shelf · updates itself"
    UserListKind.WantToRead -> "Built in · on this device"
    UserListKind.ServerShelf, UserListKind.ServerReadingList, UserListKind.ServerCollection -> {
        val noun = when (kind) {
            UserListKind.ServerShelf -> "shelf"
            UserListKind.ServerCollection -> "collection"
            else -> "reading list"
        }
        val what = "${server?.sourceType?.displayName().orEmpty()} $noun"
        if (isEditable) "$what · on the server" else "$what · someone else's, read-only"
    }
    UserListKind.User -> {
        val sources = books.map { it.entry.sourceType.displayName() }.distinct()
        when (sources.size) {
            0 -> "Your list · on this device"
            1 -> "Your list · ${sources.single()}"
            else -> "Your list · ${sources.dropLast(1).joinToString(", ")} and ${sources.last()}"
        }
    }
}

/** The line under a row's title: where it is in its series or who wrote it, where you are, and its source when the list mixes them. */
private fun UserListBook.rowLine(showSource: Boolean): String {
    if (isMissing) return "Not in your library"
    val book = item ?: return entry.title
    val context = book.libraryPrimaryText().takeUnless { it == book.libraryBookTitle() } ?: entry.secondaryText
    val reading = when {
        isFinished -> "Finished"
        (progress?.fraction ?: 0f) > 0f -> progress?.pageLine() ?: progress?.percentLabel
        else -> null
    }
    return listOfNotNull(context, reading, book.sourceType.displayName().takeIf { showSource })
        .joinToString(" · ")
        .ifEmpty { book.format.name }
}

/** "Page 4 of 74", from the page the reader saved, else the share read. */
private fun ItemProgressSummary.pageLine(): String? {
    val count = pageCount?.takeIf { it > 0 } ?: return null
    val page = (currentPage ?: (fraction.coerceIn(0f, 1f) * count).roundToInt()).coerceIn(1, count)
    return "Page $page of $count"
}

/** The rules as the page's pills: each rule, then each group as one pill ("Any of: Genre: Poetry · Pages under 100"). */
internal fun ListRules.pills(): List<String> = rules.map { it.pill() } + groups.filter { it.rules.isNotEmpty() }.map { group ->
    (if (group.matchAll) "All of: " else "Any of: ") + group.rules.joinToString(" · ") { it.pill() }
}
