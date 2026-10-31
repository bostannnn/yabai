@file:OptIn(ExperimentalLayoutApi::class)

package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppButtonDefaults
import com.example.unifiedreader.ui.components.AppChoiceRow
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppScreenBackHandler
import com.example.unifiedreader.ui.components.AppSheetDivider
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.LocalAppScreenBackGesture
import com.example.unifiedreader.ui.components.appProgressFill
import com.example.unifiedreader.ui.theme.AppCoverColors
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything about one book in a side sheet (a bottom sheet on phones): its cover, Continue,
 * Mark finished and Download, its progress, how it opens, its description, genres and facts,
 * rows of more in its series, more by its author and similar books, and at the end the quiet
 * row that removes it. [relatedPool] is
 * where those rows come from (the books of its source); a cover there shows that book's page.
 * A tag or genre opens Browse with it picked when [onBrowse] is given.
 * [onBack], when given, returns to the book this page was opened from (Back, or the arrow).
 */
@Composable
fun LibraryBookPage(
    item: ReadableItem,
    relatedPool: List<ReadableItem>,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onOpen: (ReadableItem) -> Unit,
    onShowBook: (ReadableItem) -> Unit,
    onDeleteImported: (ReadableItem) -> Unit,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    onBrowse: ((LibraryBrowseChip) -> Unit)? = null,
    // "See all" on the series row; given the page's book.
    onSeeAllInSeries: ((ReadableItem) -> Unit)? = null,
) {
    val progress = actionContext.progressByItemId[item.id]
    // Hundreds or thousands of books to compare, so not on the main thread.
    val related by produceState(LibraryBookPageRelated(), item.id, relatedPool) {
        value = withContext(Dispatchers.Default) { libraryBookPageRelated(item, relatedPool) }
    }
    val description = rememberLibraryBookDescription(item)
    val text = remember(item, progress, related.seriesPosition, description.text) {
        libraryBookPageText(item, progress, related.seriesPosition, description = description.text)
    }
    // The top takes the cover's colours; a quiet tint until they arrive or for a cover without any.
    val scheme = MaterialTheme.colorScheme
    val tint = animateCoverColors(
        rememberLibraryCoverColors(item, coverImageUrlProvider) ?: AppCoverColors(
            tintStart = scheme.surfaceContainerHigh,
            tintEnd = scheme.surfaceContainerHigh,
            content = scheme.onSurface,
            track = scheme.onSurface.copy(alpha = 0.12f),
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            accentContainer = scheme.primaryContainer,
            onAccentContainer = scheme.onPrimaryContainer,
        ),
    )
    var confirmRemove by remember(item.id) { mutableStateOf(false) }
    // A cover in the rows swaps the page's book; the new one starts at its top.
    val listState = remember(item.id) { LazyListState() }
    AppSideSheet(onDismissRequest = onDismiss) {
        // Back steps to the previous book before it closes the sheet. Not the screen host's
        // gesture: the library under the sheet stays put.
        CompositionLocalProvider(LocalAppScreenBackGesture provides null) {
            AppScreenBackHandler(enabled = onBack != null) { onBack?.invoke() }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "top:${item.id}") {
                LibraryBookPageTop(
                    item = item,
                    text = text,
                    tint = tint,
                    progress = progress,
                    actionContext = actionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onOpen = {
                        onOpen(item)
                        dismiss()
                    },
                    onClose = { dismiss() },
                    onBack = onBack,
                )
            }
            val direction = actionContext.seriesReadingDirectionFor(item)
            val mode = actionContext.seriesReaderModeFor(item)
            if (direction != null || mode != null) {
                item(key = "reading") {
                    // AppChoiceRow pads itself 20 dp, in line with the heading and About.
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        AppSheetLabel(text = "Reading")
                        direction?.let { selected ->
                            AppChoiceRow(
                                label = "Direction",
                                options = ComicReadingDirection.entries,
                                selected = selected,
                                onSelected = { picked -> actionContext.onSeriesDirectionSelected(item, picked) },
                                optionLabel = ComicReadingDirection::pageLabel,
                            )
                        }
                        mode?.let { selected ->
                            AppChoiceRow(
                                label = "Open as",
                                options = ComicReaderMode.entries,
                                selected = selected,
                                onSelected = { picked -> actionContext.onSeriesReaderModeSelected(item, picked) },
                                optionLabel = ComicReaderMode::label,
                            )
                        }
                    }
                }
            }
            val lists = actionContext.listsContaining(item)
            if (lists.isNotEmpty()) {
                item(key = "lists") {
                    LibraryBookPageLists(lists) { listId ->
                        actionContext.onOpenList(listId)
                        dismiss()
                    }
                }
            }
            item(key = "about") {
                LibraryBookPageAbout(
                    text = text,
                    descriptionLoading = description.loading,
                    onBrowse = onBrowse?.let { browse -> { chip: LibraryBrowseChip -> browse(chip); dismiss() } },
                )
            }
            related.seriesName?.takeIf { related.moreInSeries.isNotEmpty() }?.let { series ->
                item(key = "series") {
                    LibraryBookPageRow(
                        title = "More in $series",
                        books = related.moreInSeries,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onShowBook = onShowBook,
                        onSeeAll = onSeeAllInSeries?.let { seeAll -> { seeAll(item); dismiss() } },
                    )
                }
            }
            related.authorName?.let { author ->
                item(key = "author") {
                    LibraryBookPageRow(
                        title = "More by $author",
                        books = related.moreByAuthor,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onShowBook = onShowBook,
                    )
                }
            }
            if (related.similar.isNotEmpty()) {
                item(key = "similar") {
                    LibraryBookPageRow(
                        title = "Similar",
                        books = related.similar,
                        coverImageUrlProvider = coverImageUrlProvider,
                        onShowBook = onShowBook,
                    )
                }
            }
            // Removing is at the end, out of the way of reading.
            val removals = libraryBookFileActions(item, actionContext).filter { it.isRemoval }
            if (removals.isNotEmpty()) {
                item(key = "remove") {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        AppSheetDivider()
                        removals.forEach { action ->
                            LibraryBookPageRemoveRow(
                                action = action,
                                onClick = when (action) {
                                    LibraryBookFileAction.RemoveImported -> { -> confirmRemove = true }
                                    else -> { -> actionContext.onDeleteDownloadedItem(item) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirmRemove) {
        LibraryDeleteImportedItemDialog(
            item = item,
            coverImageUrlProvider = coverImageUrlProvider,
            onDismiss = { confirmRemove = false },
            onConfirmDelete = { removed ->
                confirmRemove = false
                onDeleteImported(removed)
                onDismiss()
            },
        )
    }
}

@Composable
private fun LibraryBookPageTop(
    item: ReadableItem,
    text: LibraryBookPageText,
    tint: AppCoverColors,
    progress: ItemProgressSummary?,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val secondaryText = tint.content.copy(alpha = SecondaryTextAlpha)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .coverTint(tint),
    ) {
        Column(modifier = Modifier.padding(bottom = AppLayout.inset)) {
            // Back and Close where every sheet has them; the book's title is the content under them.
            CompositionLocalProvider(LocalContentColor provides tint.content) {
                AppPanelHeader(
                    title = null,
                    onBack = onBack,
                    onClose = onClose,
                    backContentDescription = "Previous book",
                    closeContentDescription = "Close book page",
                )
            }
            Column(modifier = Modifier.padding(horizontal = AppLayout.inset)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val coverShape = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius)
                    LibraryCover(
                        item = item,
                        coverUrl = coverImageUrlProvider(item, LibraryCoverImageVariant.GridLarge),
                        imageVariant = LibraryCoverImageVariant.GridLarge,
                        modifier = Modifier
                            .width(LibraryBookPageCoverWidth)
                            .aspectRatio(LibraryCoverAspectRatio)
                            .shadow(elevation = 10.dp, shape = coverShape, clip = false),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = text.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 26.sp,
                                lineHeight = 30.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = tint.content,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        text.seriesLine?.let { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                color = secondaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        text.creators?.let { creators ->
                            Text(
                                text = creators,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                color = secondaryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                AppButton(
                    onClick = onOpen,
                    colors = AppButtonDefaults.filledColors(containerColor = tint.accent, contentColor = tint.onAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Icon(imageVector = AppIcons.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = text.primaryAction,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                val actions = bookPageActions(item, progress, actionContext)
                if (actions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    // Three pills (Mark finished, Download, Add to list) can be wider than the sheet.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        actions.forEach { action -> LibraryBookPageTonalAction(action, tint) }
                    }
                }
                if (progress != null && text.progressLine != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = text.progressLine,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                            color = secondaryText,
                            modifier = Modifier.weight(1f),
                        )
                        text.progressPages?.let { pages ->
                            Text(
                                text = pages,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = secondaryText,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .appProgressFill(
                                fraction = progress.fraction,
                                progressColor = tint.accent,
                                trackColor = tint.track,
                            ),
                    )
                }
            }
        }
    }
}

private class BookPageAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** Mark finished or unread, Download, and Add to list; removing a file is the row at the page's end. */
@Composable
private fun bookPageActions(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    actionContext: LibraryItemActionContext,
): List<BookPageAction> {
    val read = when (libraryBookReadAction(item, progress)) {
        LibraryBookReadAction.MarkFinished ->
            BookPageAction(label = "Mark finished", icon = AppIcons.Check) { actionContext.onSetReadStatus(item, true) }
        LibraryBookReadAction.MarkUnread ->
            BookPageAction(label = "Mark unread", icon = AppIcons.History) { actionContext.onSetReadStatus(item, false) }
        null -> null
    }
    val files = libraryBookFileActions(item, actionContext).mapNotNull { action ->
        when (action) {
            LibraryBookFileAction.Downloading ->
                BookPageAction(label = "Downloading", icon = AppIcons.HourglassTop, enabled = false, onClick = {})
            LibraryBookFileAction.Download ->
                BookPageAction(label = "Download", icon = AppIcons.Download) { actionContext.onDownloadItem(item) }
            LibraryBookFileAction.RemoveImported,
            LibraryBookFileAction.RemoveDownload -> null
        }
    }
    val addToList = actionContext.onAddToList?.let { onAddToList ->
        BookPageAction(label = "Add to list", icon = AppIcons.Checklist) { onAddToList(item) }
    }
    return listOfNotNull(read) + files + listOfNotNull(addToList)
}

/**
 * A quiet 48 dp row at the end of the page: "Remove from library" in the error colour (it asks
 * first), or "Remove download" in the text colour (its toast has Undo).
 */
@Composable
private fun LibraryBookPageRemoveRow(
    action: LibraryBookFileAction,
    onClick: () -> Unit,
) {
    val removesBook = action == LibraryBookFileAction.RemoveImported
    val color = if (removesBook) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = AppIcons.Delete, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            text = if (removesBook) "Remove from library" else "Remove download",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
private fun RowScope.LibraryBookPageTonalAction(action: BookPageAction, tint: AppCoverColors) {
    AppTonalButton(
        onClick = action.onClick,
        enabled = action.enabled,
        colors = AppButtonDefaults.tonalColors(containerColor = tint.track, contentColor = tint.content),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier
            .weight(1f)
            .height(42.dp),
    ) {
        Icon(imageVector = action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = action.label,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** A row of covers, four across with 12 dp between them; a cover shows that book's page. */
@Composable
private fun LibraryBookPageRow(
    title: String,
    books: List<ReadableItem>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onShowBook: (ReadableItem) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppSheetDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSheetLabel(text = title, modifier = Modifier.weight(1f))
            onSeeAll?.let { seeAll ->
                AppTextButton(onClick = seeAll, modifier = Modifier.padding(top = 6.dp)) {
                    Text(text = "See all", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val coverWidth: Dp = (maxWidth - AppLayout.inset * 2 - RelatedCoverGap * 3) / 4
            LazyRow(
                contentPadding = PaddingValues(horizontal = AppLayout.inset),
                horizontalArrangement = Arrangement.spacedBy(RelatedCoverGap),
            ) {
                items(books, key = ReadableItem::id) { book ->
                    LibraryCover(
                        item = book,
                        coverUrl = coverImageUrlProvider(book, LibraryCoverImageVariant.Grid),
                        imageVariant = LibraryCoverImageVariant.Grid,
                        modifier = Modifier
                            .width(coverWidth)
                            .aspectRatio(LibraryCoverAspectRatio)
                            .semantics { contentDescription = book.libraryDisplayLabel() }
                            .clickable { onShowBook(book) },
                    )
                }
            }
        }
    }
}

/** The page reads the direction out in full; the reader's own control keeps "LTR". */
private val ComicReadingDirection.pageLabel: String
    get() = when (this) {
        ComicReadingDirection.LTR -> "Left to right"
        ComicReadingDirection.RTL -> "Right to left"
    }

private const val SecondaryTextAlpha = 0.74f
private val LibraryBookPageCoverWidth = 118.dp

private val RelatedCoverGap = 12.dp
