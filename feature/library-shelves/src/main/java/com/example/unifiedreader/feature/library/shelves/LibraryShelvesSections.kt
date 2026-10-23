package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibraryItemPageButton
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.isDownloaded
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** The three shelves as tabs, in their fixed order. */
internal enum class LibraryShelfTab(
    val label: String,
    val browserKind: LibraryShelfBrowserKind,
) {
    InProgress("In progress", LibraryShelfBrowserKind.ContinueReading),
    UpNext("Up next", LibraryShelfBrowserKind.NextInSeries),
    Finished("Finished", LibraryShelfBrowserKind.RecentlyFinished),
}

/**
 * The tabs that have something to show, with their counts. In progress leaves out the book the
 * reading card already shows ([readingCardItemId]).
 */
internal fun libraryShelfTabCounts(
    state: LibraryShelvesState,
    readingCardItemId: String?,
): List<Pair<LibraryShelfTab, Int>> = listOf(
    LibraryShelfTab.InProgress to state.continueReading.count { it.item.id != readingCardItemId },
    LibraryShelfTab.UpNext to state.nextInSeries.size,
    LibraryShelfTab.Finished to state.recentlyFinished.size,
).filter { (_, count) -> count > 0 }

/**
 * The shelves band: a row of pill tabs ("In progress 6") with See all at the end, over one strip
 * of shelf cards at the grid's cover size. Every strip has the same height, so switching tabs
 * never moves the grid under it.
 */
@Composable
internal fun LibraryShelfTabsBand(
    state: LibraryShelvesState,
    tabs: List<Pair<LibraryShelfTab, Int>>,
    selectedTab: LibraryShelfTab,
    onTabSelected: (LibraryShelfTab) -> Unit,
    readingCardItemId: String?,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    covers: ShelfCoverLayout,
    spacing: Dp,
    onItemSelected: (ReadableItem) -> Unit,
    onSeeAll: (LibraryShelfBrowserKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { (tab, count) ->
                    LibraryShelfTabPill(
                        label = tab.label,
                        count = count,
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                    )
                }
            }
            AppTextButton(onClick = { onSeeAll(selectedTab.browserKind) }) {
                Text(
                    text = "See all",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
        val slide = with(LocalDensity.current) { 12.dp.roundToPx() }
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(AppMotion.effects()) + slideInHorizontally(AppMotion.spatial<IntOffset>()) { slide }) togetherWith
                    fadeOut(tween(durationMillis = 90))
            },
            contentAlignment = Alignment.TopStart,
            label = "libraryShelfStrip",
            modifier = Modifier.padding(top = 14.dp),
        ) { tab ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                when (tab) {
                    LibraryShelfTab.InProgress -> state.continueReading
                        .filter { it.item.id != readingCardItemId }
                        .take(ShelfStripItemLimit)
                        .forEach { shelfItem ->
                            ShelfStripCard(
                                item = shelfItem.item,
                                supportingText = continueReadingSupportingText(shelfItem.item),
                                trailingText = shelfItem.progress.detailLabel,
                                accentColor = UnifiedReaderThemeTokens.colors.readerAccent,
                                progress = shelfItem.progress,
                                itemActionContext = itemActionContext,
                                coverImageUrlProvider = coverImageUrlProvider,
                                covers = covers,
                                onItemSelected = onItemSelected,
                            )
                        }
                    LibraryShelfTab.UpNext -> state.nextInSeries.take(ShelfStripItemLimit).forEach { shelfItem ->
                        ShelfStripCard(
                            item = shelfItem.item,
                            supportingText = shelfItem.item.librarySecondaryText() ?: shelfItem.item.title,
                            trailingText = "After ${shelfItem.currentItem.librarySecondaryText() ?: shelfItem.currentItem.title}",
                            accentColor = UnifiedReaderThemeTokens.colors.sourceAccent,
                            progress = itemActionContext.progressByItemId[shelfItem.item.id],
                            itemActionContext = itemActionContext,
                            coverImageUrlProvider = coverImageUrlProvider,
                            covers = covers,
                            onItemSelected = onItemSelected,
                        )
                    }
                    LibraryShelfTab.Finished -> state.recentlyFinished.take(ShelfStripItemLimit).forEach { shelfItem ->
                        ShelfStripCard(
                            item = shelfItem.item,
                            supportingText = shelfItem.item.librarySecondaryText() ?: shelfItem.progress.label,
                            trailingText = DateUtils.getRelativeTimeSpanString(
                                shelfItem.progress.lastReadAtEpochMillis,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ).toString(),
                            accentColor = UnifiedReaderThemeTokens.colors.syncAccent,
                            progress = shelfItem.progress,
                            itemActionContext = itemActionContext,
                            coverImageUrlProvider = coverImageUrlProvider,
                            covers = covers,
                            onItemSelected = onItemSelected,
                        )
                    }
                }
            }
        }
    }
}

/** A 34 dp pill tab with its count; the selected one is filled. */
@Composable
private fun LibraryShelfTabPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (selected) colors.surfaceContainerHigh else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) colors.onSurface else colors.onSurfaceVariant) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
        }
    }
}

/** Today's shelf card: the cover with a progress bar while started, the title, a line and an accent line. */
@Composable
private fun ShelfStripCard(
    item: ReadableItem,
    supportingText: String,
    trailingText: String,
    accentColor: Color,
    progress: ItemProgressSummary?,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    covers: ShelfCoverLayout,
    onItemSelected: (ReadableItem) -> Unit,
) {
    val isComplete = progress?.let { it.isFinished || it.fraction >= 0.999f } == true
    ShelfPreviewCard(
        item = item,
        title = item.libraryPrimaryText(),
        supportingText = supportingText,
        trailingText = trailingText,
        coverImageUrlProvider = coverImageUrlProvider,
        covers = covers,
        onClick = { onItemSelected(item) },
        accentColor = accentColor,
        menuContent = {
            LibraryItemPageButton(
                item = item,
                actionContext = itemActionContext,
                buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
            )
        },
        progressFraction = progress?.fraction?.takeIf { it > 0f && !isComplete },
        isDownloaded = itemActionContext.isDownloaded(item),
        isRead = isComplete,
    )
}

/** Cards per strip; the strip scrolls sideways and See all opens the whole shelf. */
internal const val ShelfStripItemLimit = 12

/** Covers prefetched per shelf ahead of the strip being shown. */
internal const val ShelfPreviewItemLimit = 4
