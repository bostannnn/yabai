package com.example.unifiedreader.feature.journal

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppBookRow
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppProgressBar
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appSkeletonPulse
import com.example.unifiedreader.ui.components.currentAppWindowWidth

/**
 * What you read (Month): the books and comics given time in the period, longest first, each with
 * its time, its days and a bar against the longest; a finished one says so and offers Rate. The
 * ones only opened for a moment fold into one line at the end. While the journal's books are
 * still [loading], placeholder rows hold the panel's place.
 */
@Composable
internal fun JournalWhatYouReadPanel(
    overview: JournalPeriodOverview,
    loading: Boolean,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpenItem: (JournalItemSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = remember(overview) { overview.whatYouRead() }
    if (!loading && reading.read.isEmpty() && reading.opened.isEmpty()) return
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    var showAll by rememberSaveable(overview.periodStartEpochMillis) { mutableStateOf(false) }
    var showOpened by rememberSaveable(overview.periodStartEpochMillis) { mutableStateOf(false) }
    val longest = reading.read.maxOfOrNull(JournalReadItem::readingMillis) ?: 0L
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    JournalPanel(modifier = modifier.animateContentSize()) {
        JournalSectionHeader(
            title = "What you read",
            summary = if (compact) "Longest first" else "Longest first · tap one for its rating, review and sessions",
        )
        if (loading) {
            ReadItemPlaceholders(compact = compact)
            return@JournalPanel
        }
        Column(modifier = Modifier.padding(top = 6.dp)) {
            val shown = if (showAll) reading.read else reading.read.take(WhatYouReadRows)
            shown.forEachIndexed { index, item ->
                if (index > 0) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(divider))
                ReadItemRow(
                    item = item,
                    longestMillis = longest,
                    compact = compact,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    onOpen = { onOpenItem(item.summary) },
                )
            }
            if (reading.read.size > WhatYouReadRows) {
                AppTextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(text = if (showAll) "Show fewer" else "Show all ${reading.read.size}")
                }
            }
            if (reading.opened.isNotEmpty()) {
                if (shown.isNotEmpty()) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(divider))
                OpenedLine(
                    opened = reading.opened,
                    expanded = showOpened,
                    coverWidth = if (compact) 40.dp else 44.dp,
                    onToggle = { showOpened = !showOpened },
                )
                if (showOpened) {
                    reading.opened.forEach { item ->
                        OpenedItemRow(
                            item = item,
                            coverImageUrlProvider = coverImageUrlProvider,
                            onCoverVisible = onCoverVisible,
                            onOpen = { onOpenItem(item.summary) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadItemRow(
    item: JournalReadItem,
    longestMillis: Long,
    compact: Boolean,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpen: () -> Unit,
) {
    val summary = item.summary
    AppBookRow(
        title = summary.item.displayTitle,
        onClick = onOpen,
        contentPadding = PaddingValues(vertical = 11.dp),
        cover = {
            JournalCoverArt(
                item = summary.item,
                catalogItem = summary.catalogItem,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                modifier = Modifier
                    .size(width = if (compact) 40.dp else 44.dp, height = if (compact) 60.dp else 66.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        },
        supporting = {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.finishedAtEpochMillis?.let { finishedAt ->
                    AppBadge(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    ) {
                        Text(
                            text = "Finished ${formatShortDate(finishedAt)}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                val details = item.details()
                if (details.isNotBlank() && !(compact && item.finishedAtEpochMillis != null)) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (longestMillis > 0L && item.readingMillis > 0L) {
                AppProgressBar(
                    fraction = item.readingMillis.toFloat() / longestMillis,
                    height = 4.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.widthIn(min = if (compact) 72.dp else 96.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item.amount()?.let { amount ->
                Text(
                    text = amount,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
            if (item.needsRating) {
                AppTonalButton(onClick = onOpen, modifier = Modifier.height(34.dp)) { Text(text = "Rate") }
            } else if (item.readingDays > 0) {
                Text(
                    text = "on ${item.readingDays.counted("day")}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Rows of blocks in the shape of the books to come, breathing together. */
@Composable
private fun ReadItemPlaceholders(compact: Boolean) {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier = Modifier.padding(top = 6.dp).appSkeletonPulse()) {
        listOf(0.62f, 0.48f, 0.55f, 0.4f).forEach { fraction ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = if (compact) 40.dp else 44.dp, height = if (compact) 60.dp else 66.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(block),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(fraction).height(14.dp).clip(RoundedCornerShape(4.dp)).background(block))
                    Box(modifier = Modifier.fillMaxWidth(fraction * 0.6f).height(11.dp).clip(RoundedCornerShape(4.dp)).background(block))
                }
                Box(modifier = Modifier.size(width = 64.dp, height = 14.dp).clip(RoundedCornerShape(4.dp)).background(block))
            }
        }
    }
}

/** "Opened for a minute or two: Аут, Tropic of Cancer and 3 more", which lists them when tapped. */
@Composable
private fun OpenedLine(
    opened: List<JournalReadItem>,
    expanded: Boolean,
    coverWidth: Dp,
    onToggle: () -> Unit,
) {
    val names = opened.take(OpenedNamesShown).joinToString(", ") { item -> item.summary.item.displayTitle }
    val more = opened.size - OpenedNamesShown
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = if (expanded) "Hide" else "Show", onClick = onToggle)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(width = coverWidth, height = 24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = AppIcons.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 270f else 90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                    append("Opened for a minute or two: ")
                }
                append(names)
                if (more > 0) append(" and $more more")
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OpenedItemRow(
    item: JournalReadItem,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JournalCoverArt(
            item = item.summary.item,
            catalogItem = item.summary.catalogItem,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            modifier = Modifier
                .size(width = 28.dp, height = 42.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Text(
            text = item.summary.item.displayTitle,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.amount()?.let { amount ->
            Text(
                text = amount,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Its time, or its pages for reading done without a session (on another device). */
private fun JournalReadItem.amount(): String? = when {
    readingMillis > 0L -> formatJournalMinutes(readingMillis)
    pages > 0 -> pages.counted("page")
    else -> null
}

/** "Мирча Элиаде · Book", "Volume 3 · Comic". */
private fun JournalReadItem.details(): String = listOfNotNull(
    summary.item.displaySubtitle,
    when (summary.catalogItem?.kind ?: summary.item.itemKind) {
        ReadableItemKind.BOOK -> "Book"
        ReadableItemKind.COMIC -> "Comic"
    },
).joinToString(" · ")

private const val WhatYouReadRows = 8
private const val OpenedNamesShown = 4
