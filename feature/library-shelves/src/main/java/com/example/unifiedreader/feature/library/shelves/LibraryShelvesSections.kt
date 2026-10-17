package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.library.LibraryAnchoredItemMenu
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.hasMenuContent
import com.example.unifiedreader.feature.library.isDownloaded
import com.example.unifiedreader.feature.library.menu.LibraryItemMenuButtonStyle
import com.example.unifiedreader.ui.theme.UnifiedReaderBorderTreatment
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderBorderTreatment
import com.example.unifiedreader.ui.theme.unifiedReaderSurfacePattern

@Composable
internal fun ContinueReadingShelfSection(
    items: List<ContinueReadingShelfItem>,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    previewContentEnabled: Boolean,
    coverLoadingEnabled: Boolean = true,
    covers: ShelfCoverLayout,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val previewItems = items.take(ShelfPreviewItemLimit)
    ShelfSectionCard(
        title = "Continue Reading",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        accentColor = semanticColors.readerAccent,
        accentContainerColor = semanticColors.readerAccentContainer,
        accentContentColor = semanticColors.onReaderAccentContainer,
        modifier = modifier,
    ) {
        if (previewContentEnabled) {
            ShelfPreviewRow {
                previewItems.forEach { shelfItem ->
                    ContinueReadingCarouselCard(
                        shelfItem = shelfItem,
                        itemActionContext = itemActionContext,
                        coverImageUrlProvider = coverImageUrlProvider,
                        coverLoadingEnabled = coverLoadingEnabled,
                        covers = covers,
                        onClick = { onItemSelected(shelfItem.item) },
                        expandedMenuItemId = expandedMenuItemId,
                        onExpandedMenuChange = onExpandedMenuChange,
                        onDeleteImportedRequested = onDeleteImportedRequested,
                    )
                }
            }
        } else {
            ShelfPreviewPlaceholderRow(coverWidth = covers.width)
        }
    }
}

@Composable
internal fun NextInSeriesShelfSection(
    items: List<NextInSeriesShelfItem>,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    previewContentEnabled: Boolean,
    coverLoadingEnabled: Boolean = true,
    covers: ShelfCoverLayout,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val previewItems = items.take(ShelfPreviewItemLimit)
    ShelfSectionCard(
        title = "Up Next",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        accentColor = semanticColors.sourceAccent,
        accentContainerColor = semanticColors.sourceAccentContainer,
        accentContentColor = semanticColors.onSourceAccentContainer,
        modifier = modifier,
    ) {
        if (previewContentEnabled) {
            ShelfPreviewRow {
                previewItems.forEach { shelfItem ->
                    val hasMenuContent = itemActionContext.hasMenuContent(shelfItem.item)
                    ShelfPreviewCard(
                        item = shelfItem.item,
                        title = shelfItem.item.libraryPrimaryText(),
                        supportingText = shelfItem.item.librarySecondaryText()
                            ?: shelfItem.item.title,
                        trailingText = "After ${shelfItem.currentItem.librarySecondaryText() ?: shelfItem.currentItem.title}",
                        coverImageUrlProvider = coverImageUrlProvider,
                        coverLoadingEnabled = coverLoadingEnabled,
                        covers = covers,
                        onClick = { onItemSelected(shelfItem.item) },
                        accentColor = semanticColors.sourceAccent,
                        menuContent = if (hasMenuContent) {
                            {
                                LibraryAnchoredItemMenu(
                                    item = shelfItem.item,
                                    actionContext = itemActionContext,
                                    expanded = expandedMenuItemId == shelfItem.item.id,
                                    onExpandedChange = { expanded ->
                                        onExpandedMenuChange(shelfItem.item, expanded)
                                    },
                                    onDeleteImportedRequested = onDeleteImportedRequested,
                                    buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
                                )
                            }
                        } else {
                            null
                        },
                        isDownloaded = itemActionContext.isDownloaded(shelfItem.item),
                        isRead = itemActionContext.progressByItemId[shelfItem.item.id]?.isShelfComplete == true,
                    )
                }
            }
        } else {
            ShelfPreviewPlaceholderRow(coverWidth = covers.width)
        }
    }
}

@Composable
internal fun RecentlyFinishedShelfSection(
    items: List<RecentlyFinishedShelfItem>,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    previewContentEnabled: Boolean,
    coverLoadingEnabled: Boolean = true,
    covers: ShelfCoverLayout,
    onItemSelected: (ReadableItem) -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val previewItems = items.take(ShelfPreviewItemLimit)
    ShelfSectionCard(
        title = "Recently Finished",
        count = items.size,
        showSeeAll = items.size > SeeAllThreshold,
        onSeeAll = onSeeAll,
        accentColor = semanticColors.syncAccent,
        accentContainerColor = semanticColors.syncAccentContainer,
        accentContentColor = semanticColors.onSyncAccentContainer,
        modifier = modifier,
    ) {
        if (previewContentEnabled) {
            ShelfPreviewRow {
                previewItems.forEach { shelfItem ->
                    val hasMenuContent = itemActionContext.hasMenuContent(shelfItem.item)
                    ShelfPreviewCard(
                        item = shelfItem.item,
                        title = shelfItem.item.libraryPrimaryText(),
                        supportingText = shelfItem.item.librarySecondaryText()
                            ?: shelfItem.progress.label,
                        trailingText = DateUtils.getRelativeTimeSpanString(
                            shelfItem.progress.lastReadAtEpochMillis,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString(),
                        coverImageUrlProvider = coverImageUrlProvider,
                        coverLoadingEnabled = coverLoadingEnabled,
                        covers = covers,
                        onClick = { onItemSelected(shelfItem.item) },
                        accentColor = semanticColors.syncAccent,
                        menuContent = if (hasMenuContent) {
                            {
                                LibraryAnchoredItemMenu(
                                    item = shelfItem.item,
                                    actionContext = itemActionContext,
                                    expanded = expandedMenuItemId == shelfItem.item.id,
                                    onExpandedChange = { expanded ->
                                        onExpandedMenuChange(shelfItem.item, expanded)
                                    },
                                    onDeleteImportedRequested = onDeleteImportedRequested,
                                    buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        } else {
            ShelfPreviewPlaceholderRow(coverWidth = covers.width)
        }
    }
}

@Composable
private fun ShelfPreviewRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ShelfSectionCard(
    title: String,
    count: Int,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit,
    accentColor: Color,
    accentContainerColor: Color,
    accentContentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val presentation = UnifiedReaderThemeTokens.presentation
    val personality = UnifiedReaderThemeTokens.personality
    val borderWidth = when (personality.borderTreatment) {
        UnifiedReaderBorderTreatment.Double,
        UnifiedReaderBorderTreatment.Ink -> 2.dp
        else -> 1.dp
    }
    val borderAlpha = when (personality.borderTreatment) {
        UnifiedReaderBorderTreatment.Dashed -> 0.14f
        UnifiedReaderBorderTreatment.Ink -> 0.18f
        UnifiedReaderBorderTreatment.Double -> 0.12f
        else -> 0.08f
    }
    Card(
        modifier = modifier.unifiedReaderBorderTreatment(
            treatment = personality.borderTreatment,
            color = accentColor,
            cornerRadius = presentation.cardCornerRadius,
            width = borderWidth,
            alpha = borderAlpha,
        ),
        shape = RoundedCornerShape(presentation.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = UnifiedReaderThemeTokens.colors.surfaceRaised,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .unifiedReaderSurfacePattern(
                    treatment = personality.surfaceTreatment,
                    accentColor = accentColor,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(presentation.accentRailWidth)
                    .background(accentColor),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = presentation.shelfCardHorizontalPadding,
                        vertical = presentation.shelfCardVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ShelfSectionHeaderMinHeight),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(presentation.accentRailWidth)
                                .height(34.dp)
                                .clip(RoundedCornerShape(presentation.chipCornerRadius))
                                .background(accentColor),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = shelfCountLabel(count),
                                style = MaterialTheme.typography.labelSmall,
                                color = accentContentColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(presentation.chipCornerRadius))
                                    .background(accentContainerColor)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (showSeeAll) {
                        TextButton(onClick = onSeeAll) {
                            Text(
                                text = "See all",
                                color = accentColor,
                            )
                        }
                    }
                }
                content()
            }
        }
    }
}

@Composable
private fun ContinueReadingCarouselCard(
    shelfItem: ContinueReadingShelfItem,
    itemActionContext: LibraryItemActionContext,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverLoadingEnabled: Boolean,
    covers: ShelfCoverLayout,
    onClick: () -> Unit,
    expandedMenuItemId: String?,
    onExpandedMenuChange: (ReadableItem, Boolean) -> Unit,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    val hasMenuContent = itemActionContext.hasMenuContent(shelfItem.item)
    val semanticColors = UnifiedReaderThemeTokens.colors
    ShelfPreviewCard(
        item = shelfItem.item,
        title = shelfItem.item.libraryPrimaryText(),
        supportingText = continueReadingSupportingText(shelfItem.item),
        trailingText = shelfItem.progress.detailLabel,
        coverImageUrlProvider = coverImageUrlProvider,
        coverLoadingEnabled = coverLoadingEnabled,
        covers = covers,
        onClick = onClick,
        accentColor = semanticColors.readerAccent,
        menuContent = if (hasMenuContent) {
            {
                LibraryAnchoredItemMenu(
                    item = shelfItem.item,
                    actionContext = itemActionContext,
                    expanded = expandedMenuItemId == shelfItem.item.id,
                    onExpandedChange = { expanded ->
                        onExpandedMenuChange(shelfItem.item, expanded)
                    },
                    onDeleteImportedRequested = onDeleteImportedRequested,
                    buttonStyle = LibraryItemMenuButtonStyle.CompactInline,
                )
            }
        } else {
            null
        },
        progressFraction = shelfItem.progress.fraction,
        isDownloaded = itemActionContext.isDownloaded(shelfItem.item),
        isRead = shelfItem.progress.isShelfComplete,
    )
}

private val com.example.unifiedreader.feature.library.ItemProgressSummary.isShelfComplete: Boolean
    get() = isFinished || fraction >= 0.999f

private fun shelfCountLabel(
    count: Int,
): String = when (count) {
    1 -> "1 item"
    else -> "$count items"
}

private val ShelfSectionHeaderMinHeight = 48.dp
internal const val ShelfPreviewItemLimit = 4
private const val SeeAllThreshold = 4
