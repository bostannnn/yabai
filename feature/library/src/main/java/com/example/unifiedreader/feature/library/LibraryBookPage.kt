package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppChoiceRow
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.appProgressFill
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything about one book in a side sheet (a bottom sheet on phones): its cover, Continue,
 * Mark finished and the file action, its progress, how it opens, its description, genres and
 * facts, and rows of more in its series, more by its author and similar books. [relatedPool] is
 * where those rows come from (the books of its source); a cover there shows that book's page.
 * A genre or tag filters the library when [onFilterByGenre] / [onFilterByTag] are given.
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
    onFilterByGenre: ((String) -> Unit)? = null,
    onFilterByTag: ((String) -> Unit)? = null,
    // "See all" on the series row; given the page's book.
    onSeeAllInSeries: ((ReadableItem) -> Unit)? = null,
) {
    val progress = actionContext.progressByItemId[item.id]
    // Hundreds or thousands of books to compare, so not on the main thread.
    val related by produceState(LibraryBookPageRelated(), item.id, relatedPool) {
        value = withContext(Dispatchers.Default) { libraryBookPageRelated(item, relatedPool) }
    }
    val text = remember(item, progress, related.seriesPosition) {
        libraryBookPageText(item, progress, related.seriesPosition)
    }
    var confirmRemove by remember(item.id) { mutableStateOf(false) }
    // A cover in the rows swaps the page's book; the new one starts at its top.
    val listState = remember(item.id) { LazyListState() }
    AppSideSheet(onDismissRequest = onDismiss, width = LibraryBookPageWidth) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "top:${item.id}") {
                LibraryBookPageTop(
                    item = item,
                    text = text,
                    progress = progress,
                    actionContext = actionContext,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onOpen = {
                        onOpen(item)
                        dismiss()
                    },
                    onRemoveImported = { confirmRemove = true },
                    onClose = { dismiss() },
                )
            }
            val direction = actionContext.seriesReadingDirectionFor(item)
            val mode = actionContext.seriesReaderModeFor(item)
            if (direction != null || mode != null) {
                item(key = "reading") {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        LibraryBookPageHeading(text = "Reading")
                        direction?.let { selected ->
                            AppChoiceRow(
                                label = "Direction",
                                options = ComicReadingDirection.entries,
                                selected = selected,
                                onSelected = { picked -> actionContext.onSeriesDirectionSelected(item, picked) },
                                optionLabel = ComicReadingDirection::pageLabel,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                        mode?.let { selected ->
                            AppChoiceRow(
                                label = "Open as",
                                options = ComicReaderMode.entries,
                                selected = selected,
                                onSelected = { picked -> actionContext.onSeriesReaderModeSelected(item, picked) },
                                optionLabel = ComicReaderMode::label,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
            item(key = "about") {
                LibraryBookPageAbout(
                    text = text,
                    onFilterByGenre = onFilterByGenre?.let { filter -> { genre: String -> filter(genre); dismiss() } },
                    onFilterByTag = onFilterByTag?.let { filter -> { tag: String -> filter(tag); dismiss() } },
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
        }
    }
    if (confirmRemove) {
        LibraryDeleteImportedItemDialog(
            item = item,
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
    progress: ItemProgressSummary?,
    actionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onOpen: () -> Unit,
    onRemoveImported: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // The cover's own colour arrives with 8.6; until then the page's top sits on a quiet tint.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp)) {
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
                        .padding(start = 16.dp, end = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = text.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 26.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    text.seriesLine?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    text.creators?.let { creators ->
                        Text(
                            text = creators,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            AppButton(
                onClick = onOpen,
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
            val actions = bookPageActions(item, progress, actionContext, onRemoveImported)
            if (actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    actions.forEach { action -> LibraryBookPageTonalAction(action) }
                }
            }
            if (progress != null && text.progressLine != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = text.progressLine,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.onSurfaceVariant,
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
                            color = colors.onSurfaceVariant,
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
                            progressColor = colors.primary,
                            trackColor = colors.onSurface.copy(alpha = 0.12f),
                        ),
                )
            }
        }
        AppIconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(imageVector = AppIcons.Close, contentDescription = "Close book page")
        }
    }
}

private class BookPageAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
private fun bookPageActions(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    actionContext: LibraryItemActionContext,
    onRemoveImported: () -> Unit,
): List<BookPageAction> {
    val read = when (libraryBookReadAction(item, progress)) {
        LibraryBookReadAction.MarkFinished ->
            BookPageAction(label = "Mark finished", icon = AppIcons.Check) { actionContext.onSetReadStatus(item, true) }
        LibraryBookReadAction.MarkUnread ->
            BookPageAction(label = "Mark unread", icon = AppIcons.History) { actionContext.onSetReadStatus(item, false) }
        null -> null
    }
    val files = libraryBookFileActions(item, actionContext).map { action ->
        when (action) {
            LibraryBookFileAction.RemoveImported ->
                BookPageAction(label = "Remove file", icon = AppIcons.Delete, onClick = onRemoveImported)
            LibraryBookFileAction.Downloading ->
                BookPageAction(label = "Downloading", icon = AppIcons.HourglassTop, enabled = false, onClick = {})
            LibraryBookFileAction.Download ->
                BookPageAction(label = "Download", icon = AppIcons.Download) { actionContext.onDownloadItem(item) }
            LibraryBookFileAction.RemoveDownload ->
                BookPageAction(label = "Remove download", icon = AppIcons.Delete) { actionContext.onDeleteDownloadedItem(item) }
        }
    }
    return listOfNotNull(read) + files
}

@Composable
private fun RowScope.LibraryBookPageTonalAction(action: BookPageAction) {
    AppTonalButton(
        onClick = action.onClick,
        enabled = action.enabled,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryBookPageAbout(
    text: LibraryBookPageText,
    onFilterByGenre: ((String) -> Unit)?,
    onFilterByTag: ((String) -> Unit)?,
) {
    var expanded by rememberSaveable(text.description) { mutableStateOf(false) }
    // "More" only when four lines do not hold it all.
    var overflows by remember(text.description) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        LibraryBookPageHairline()
        LibraryBookPageHeading(text = "About")
        text.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                maxLines = if (expanded) Int.MAX_VALUE else DescriptionCollapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout -> if (!expanded) overflows = layout.hasVisualOverflow },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (overflows || expanded) {
                AppTextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(text = if (expanded) "Less" else "More", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        val chips = text.genres.map { genre -> genre to onFilterByGenre } + text.tags.map { tag -> tag to onFilterByTag }
        if (chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.distinctBy { (value, _) -> value.lowercase() }.forEach { (value, filter) ->
                    AppChip(
                        selected = false,
                        onClick = { filter?.invoke(value) },
                        enabled = filter != null,
                        modifier = Modifier.height(LibraryBookPageChipHeight),
                        label = { Text(text = value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            text.facts.forEach { (label, value) ->
                Row {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(LibraryBookPageFactLabelWidth),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
        LibraryBookPageHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryBookPageHeading(text = title, modifier = Modifier.weight(1f))
            onSeeAll?.let { seeAll ->
                AppTextButton(onClick = seeAll, modifier = Modifier.padding(top = 6.dp)) {
                    Text(text = "See all", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val coverWidth: Dp = (maxWidth - 40.dp - RelatedCoverGap * 3) / 4
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
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
                            .semantics { contentDescription = book.title }
                            .clickable { onShowBook(book) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryBookPageHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun LibraryBookPageHairline() {
    AppDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** The page reads the direction out in full; the reader's own control keeps "LTR". */
private val ComicReadingDirection.pageLabel: String
    get() = when (this) {
        ComicReadingDirection.LTR -> "Left to right"
        ComicReadingDirection.RTL -> "Right to left"
    }

private val LibraryBookPageWidth = 440.dp
private val LibraryBookPageCoverWidth = 118.dp
private val LibraryBookPageChipHeight = 30.dp
private val LibraryBookPageFactLabelWidth = 110.dp
private val RelatedCoverGap = 12.dp
private const val DescriptionCollapsedLines = 4
