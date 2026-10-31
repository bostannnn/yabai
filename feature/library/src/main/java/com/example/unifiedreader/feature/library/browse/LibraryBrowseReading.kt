package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseFacet
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryBrowseLabel
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.ui.components.AppGroupLabel
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppPanelDefaults
import com.example.unifiedreader.ui.components.AppProgressBar
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appCount
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import kotlin.math.roundToInt

/**
 * Your reading on the landing page: the books in progress, last read first, with how far you are,
 * beside Finished and Not started with a few of their covers. A panel picks its status; a book in
 * progress opens.
 */
@Composable
internal fun LibraryBrowseReadingPanels(
    facets: List<LibraryBrowseFacet>,
    inProgress: List<ReadableItem>,
    statusBooks: Map<String, List<ReadableItem>>,
    books: LibraryBrowseBooks,
    onToggle: (LibraryBrowseChip) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
) {
    val counts = facets.associate { facet -> facet.value to facet.count }
    val progressLabel = LibraryReadingStatus.IN_PROGRESS.libraryBrowseLabel
    val stats = listOf(LibraryReadingStatus.FINISHED, LibraryReadingStatus.NOT_STARTED)
        .map { status -> status.libraryBrowseLabel }
        .filter { label -> (counts[label] ?: 0) > 0 }
    val progressPanel: (@Composable (Modifier) -> Unit)? = counts[progressLabel]?.takeIf { inProgress.isNotEmpty() }?.let { count ->
        { modifier ->
            LibraryBrowseInProgressPanel(
                count = count,
                inProgress = inProgress,
                books = books,
                onPick = { onToggle(LibraryBrowseChip(LibraryBrowseRow.READING, progressLabel)) },
                onItemSelected = onItemSelected,
                modifier = modifier,
            )
        }
    }
    val statPanel: @Composable (String, Modifier) -> Unit = { label, modifier ->
        LibraryBrowseStatusPanel(
            label = label,
            count = counts[label] ?: 0,
            covers = statusBooks[label].orEmpty(),
            books = books,
            onClick = { onToggle(LibraryBrowseChip(LibraryBrowseRow.READING, label)) },
            modifier = modifier,
        )
    }
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    when {
        progressPanel == null -> Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            stats.forEach { label -> statPanel(label, Modifier.weight(1f)) }
        }
        compact -> Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            progressPanel(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                stats.forEach { label -> statPanel(label, Modifier.weight(1f)) }
            }
        }
        else -> Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
        ) {
            progressPanel(Modifier.weight(ProgressPanelWeight))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
            ) {
                stats.forEach { label -> statPanel(label, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun LibraryBrowseInProgressPanel(
    count: Int,
    inProgress: List<ReadableItem>,
    books: LibraryBrowseBooks,
    onPick: () -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    modifier: Modifier,
) {
    AppPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(AppLayout.inset),
            verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onPick),
                horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppGroupLabel(text = LibraryReadingStatus.IN_PROGRESS.libraryBrowseLabel)
                Text(
                    text = appCount(count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            inProgress.forEach { item ->
                val fraction = books.progressByItemId[item.id]?.fraction ?: 0f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = { onItemSelected(item) }),
                    horizontalArrangement = Arrangement.spacedBy(BookRowGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryBrowseSmallCover(item = item, books = books, modifier = Modifier.width(BookCoverWidth))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.libraryPrimaryText(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.librarySecondaryText()?.let { byline ->
                            Text(
                                text = byline,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = ProgressTop),
                            horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppProgressBar(fraction = fraction, height = ProgressHeight, modifier = Modifier.weight(1f))
                            Text(
                                text = "${(fraction * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryBrowseStatusPanel(
    label: String,
    count: Int,
    covers: List<ReadableItem>,
    books: LibraryBrowseBooks,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    AppPanel(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(AppLayout.inset),
            horizontalArrangement = Arrangement.spacedBy(BookRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (covers.isNotEmpty()) {
                Box(modifier = Modifier.width(StackWidth)) {
                    covers.take(StackCount).forEachIndexed { index, item ->
                        LibraryBrowseRingedCover(
                            item = item,
                            books = books,
                            width = BookCoverWidth,
                            ring = AppPanelDefaults.containerColor,
                            modifier = Modifier
                                .offset(x = StackStep * index)
                                .zIndex((StackCount - index).toFloat()),
                        )
                    }
                }
            }
            Column {
                Text(text = appCount(count), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val ProgressPanelWeight = 1.55f
private const val StackCount = 3
private val BookCoverWidth = 36.dp
private val BookRowGap = 12.dp
private val ProgressTop = 6.dp
private val ProgressHeight = 4.dp
private val StackStep = 12.dp
private val StackWidth = 64.dp
