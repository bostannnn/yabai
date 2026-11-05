package com.example.unifiedreader.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.AppCoverShape
import com.example.unifiedreader.ui.components.AppRoundCheck
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun GridCoverCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    rating: LibraryRatingBadgeState?,
    isDownloaded: Boolean,
    fadeRead: Boolean,
    imageVariant: LibraryCoverImageVariant,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    // A series tile (Kavita or Stump): its issue count draws the stack (a comic's pile) and fills the meta line.
    seriesIssueCount: Int? = null,
    // The tile's length while the grid is sorted by pages; it takes the meta line's place.
    lengthLabel: String? = null,
    title: String = itemPrimaryText(item),
    showTitle: Boolean = true,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit = {},
    onSelectionStart: (() -> Unit)? = null,
    menuContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val progressLabel = progress?.tileLabel
    val progressBarFraction = progress?.tileBarFraction
    val seriesLabel = seriesIssueCount?.let { count -> if (count == 1) "1 issue" else "$count issues" }
    val metaLabel = lengthLabel ?: listOfNotNull(seriesLabel, progressLabel).joinToString(" · ").ifEmpty { null }
    // A picked cover steps back a little inside an accent ring.
    val picked = isSelectionMode && isSelected
    val coverScale by animateFloatAsState(
        targetValue = if (picked) SelectedCoverScale else 1f,
        animationSpec = AppMotion.spatial(),
        label = "libraryGridCoverSelection",
    )
    // The ring fades with the scale instead of snapping on and off.
    val pickedRingColor by animateColorAsState(
        targetValue = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
        animationSpec = AppMotion.effects(),
        label = "libraryGridCoverRing",
    )
    val coverShape = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius)
    // A book series shows two edges raised above its cover; a comic series lies as a pile under it.
    val isSeries = seriesIssueCount != null && seriesIssueCount > 1
    val comicPile = isSeries && item.isComic
    // The badges, the progress strip and the check follow the cover, which gives up the pile's room.
    val overlayInset = if (comicPile) AppCoverShape.pileDepth else 0.dp

    LibraryCoverActionCard(
        item = item,
        title = title,
        showTitle = showTitle,
        coverImageUrlProvider = coverImageUrlProvider,
        imageVariant = imageVariant,
        isDownloaded = isDownloaded,
        isRead = progress?.isComplete == true,
        fadeRead = fadeRead,
        showReadBadge = !isSelectionMode,
        onClick = if (isSelectionMode) onSelectionToggle else onClick,
        onLongClick = onSelectionStart.takeUnless { isSelectionMode },
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-grid-card-${item.id}")
            .then(if (isSelectionMode) Modifier.semantics { selected = isSelected } else Modifier),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(LibraryCoverAspectRatio)
            .then(if (isSeries && !comicPile) Modifier.librarySeriesStack() else Modifier)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
            }
            .then(
                if (pickedRingColor.alpha > 0f) {
                    Modifier.border(SelectedCoverRingWidth, pickedRingColor, coverShape)
                } else {
                    Modifier
                },
            ),
        trailingText = metaLabel,
        reserveTrailingLine = true,
        menuContent = menuContent.takeUnless { isSelectionMode },
        coverPile = comicPile,
        emptyCoverContent = {
            LibraryCoverPlaceholder(
                item = item,
                reserveTopBadge = progress?.isComplete == true || isSelectionMode,
                reserveBottomBadge = rating != null || isDownloaded,
            )
        },
        coverOverlay = if (rating != null || isSelectionMode || progressBarFraction != null) {
            {
                Box(modifier = Modifier.matchParentSize().padding(end = overlayInset, bottom = overlayInset)) {
                    progressBarFraction?.let { fraction ->
                        LibraryCoverProgressBar(
                            fraction = fraction,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                        )
                    }
                    rating?.let { state ->
                        LibraryRatingBadge(
                            state = state,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp),
                        )
                    }
                    if (isSelectionMode) {
                        // The whole cover toggles; the check only shows the state.
                        AppRoundCheck(
                            checked = isSelected,
                            ringColor = Color.White.copy(alpha = 0.92f),
                            ringBackdrop = Color.Black.copy(alpha = 0.4f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                        )
                    }
                }
            }
        } else {
            null
        },
    )
}

private const val SelectedCoverScale = 0.94f
private val SelectedCoverRingWidth = 3.dp
