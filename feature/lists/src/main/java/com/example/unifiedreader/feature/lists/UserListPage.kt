@file:OptIn(ExperimentalLayoutApi::class)

package com.example.unifiedreader.feature.lists

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.ui.components.AppBookRow
import com.example.unifiedreader.ui.components.AppBookRowDefaults
import com.example.unifiedreader.ui.components.AppBookRowLine
import com.example.unifiedreader.ui.components.AppBookRowNumber
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
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppSwitch
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.appGutter

/**
 * One list: its mosaic, name, description and facts, Continue and Edit, the Order chip and Hide
 * finished, then its books as rows, numbered when the list is in Your order. A book that left the
 * library keeps its row, greyed.
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
    // Keep the next 3 downloaded: its line, or null where the list can't keep books downloaded.
    keepDownloadedLine: String? = null,
    onSetKeepDownloaded: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    LaunchedEffect(scrolled) { onScrolledChanged(scrolled) }
    val next = remember(books) { books.nextToRead() }
    val numbered = list.isReadingOrder
    val shown = remember(books, list.hideFinished) {
        books.withIndex().filter { (_, book) -> !list.hideFinished || !book.isFinished }
    }
    val mixesSources = remember(books) { books.map { it.entry.sourceType }.distinct().size > 1 }
    val gutter = appGutter()
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = contentTopInset + AppLayout.contentTop,
            bottom = AppLayout.contentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
    ) {
        item(key = "header") {
            UserListHeader(
                list = list,
                books = books,
                next = next,
                cover = cover,
                onOpenBook = onOpenBook,
                onEdit = onEdit,
            )
        }
        if (keepDownloadedLine != null) {
            item(key = "keep-downloaded") {
                AppPanel(modifier = Modifier.fillMaxWidth()) {
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
        item(key = "books") {
            Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text(
                        text = when {
                            books.isNotEmpty() -> "Every book here is finished."
                            list.kind == UserListKind.Stalled -> "Nothing has stalled: every book you started was opened in the last three weeks."
                            list.kind == UserListKind.Smart -> "Nothing in your library matches these rules now."
                            list.kind == UserListKind.ServerMagicShelf -> "Nothing on ${list.server?.sourceType?.displayName()} matches these rules now."
                            list.kind == UserListKind.ServerCollection -> "Nothing in this collection that your library holds."
                            list.kind == UserListKind.ServerShelf -> "Nothing on this shelf yet. Add a ${list.server?.sourceType?.displayName()} book from its page with Add to list."
                            list.kind == UserListKind.ServerReadingList -> "Nothing here yet, or nothing your library holds. Add a ${list.server?.sourceType?.displayName()} issue from its page with Add to list."
                            else -> "Nothing here yet. Add a book from its page with Add to list, or select covers in the library."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AppPanel(modifier = Modifier.fillMaxWidth()) {
                        shown.forEachIndexed { rowIndex, (position, book) ->
                            if (rowIndex > 0) AppSettingsRowDivider()
                            UserListBookRow(
                                book = book,
                                number = if (numbered) position + 1 else null,
                                isNext = book == next && numbered,
                                showSource = mixesSources,
                                cover = cover,
                                onClick = { onOpenBook(book) },
                                isSeries = list.kind == UserListKind.ServerCollection,
                            )
                        }
                    }
                }
            }
        }
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
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.sectionGap), verticalAlignment = Alignment.Bottom) {
        val coverItems = books.take(4).map { book -> book.item ?: book.entry.asReadableItem() }
        Box(modifier = Modifier.width(132.dp)) {
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
                // A magic shelf whose rules the app can't read is changed in its server's web app.
                if (list.isEditable && (list.kind != UserListKind.ServerMagicShelf || list.smart != null)) {
                    AppTonalButton(onClick = onEdit) {
                        Icon(imageVector = AppIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (list.smart != null) "Edit rules" else "Edit")
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

@Composable
private fun UserListBookRow(
    book: UserListBook,
    number: Int?,
    isNext: Boolean,
    showSource: Boolean,
    cover: ListCover,
    onClick: () -> Unit,
    // A collection's row is a whole series, which opens in the library.
    isSeries: Boolean = false,
) {
    val item: ReadableItem = book.item ?: book.entry.asReadableItem()
    AppBookRow(
        title = book.item?.libraryBookTitle() ?: book.entry.title,
        cover = {
            Box(modifier = Modifier.width(AppBookRowDefaults.coverWidth)) {
                cover(item, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            }
        },
        onClick = if (book.isMissing) null else onClick,
        modifier = Modifier
            .padding(horizontal = AppLayout.inset)
            .alpha(
                when {
                    book.isMissing -> 0.55f
                    book.isFinished -> 0.7f
                    else -> 1f
                },
            ),
        leading = number?.let { { AppBookRowNumber(it) } },
        supporting = {
            AppBookRowLine(
                text = if (isSeries) book.seriesRowLine() else book.rowLine(showSource),
                color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            if (isSeries) {
                Icon(
                    imageVector = AppIcons.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (book.isFinished) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = "Finished",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
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
