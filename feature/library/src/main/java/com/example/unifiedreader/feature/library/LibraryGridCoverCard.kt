package com.example.unifiedreader.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
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
    // A series tile (Kavita or Stump): its issue count draws the stack and fills the meta line.
    seriesIssueCount: Int? = null,
    title: String = itemPrimaryText(item),
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onSelectionToggle: () -> Unit = {},
    onSelectionStart: (() -> Unit)? = null,
    menuContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val progressLabel = progress
        ?.takeIf { it.shouldShowCoverProgress }
        ?.percentLabel
    val seriesLabel = seriesIssueCount?.let { count -> if (count == 1) "1 issue" else "$count issues" }
    val metaLabel = listOfNotNull(seriesLabel, progressLabel).joinToString(" · ").ifEmpty { null }
    // A picked cover steps back a little inside an accent ring.
    val picked = isSelectionMode && isSelected
    val coverScale by animateFloatAsState(
        targetValue = if (picked) SelectedCoverScale else 1f,
        animationSpec = AppMotion.spatial(),
        label = "libraryGridCoverSelection",
    )
    val coverShape = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius)

    LibraryCoverActionCard(
        item = item,
        title = title,
        coverImageUrlProvider = coverImageUrlProvider,
        imageVariant = imageVariant,
        isDownloaded = isDownloaded,
        isRead = progress?.isComplete == true,
        fadeRead = fadeRead,
        onClick = if (isSelectionMode) onSelectionToggle else onClick,
        onLongClick = onSelectionStart.takeUnless { isSelectionMode },
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-grid-card-${item.id}")
            .then(if (isSelectionMode) Modifier.semantics { selected = isSelected } else Modifier),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(LibraryCoverAspectRatio)
            .then(if (seriesIssueCount != null && seriesIssueCount > 1) Modifier.librarySeriesStack() else Modifier)
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
            }
            .then(
                if (picked) {
                    Modifier.border(SelectedCoverRingWidth, MaterialTheme.colorScheme.primary, coverShape)
                } else {
                    Modifier
                },
            ),
        trailingText = metaLabel,
        menuContent = menuContent.takeUnless { isSelectionMode },
        emptyCoverContent = {
            LibraryCoverPlaceholder(
                item = item,
                reserveTopBadge = progress?.isComplete == true || isSelectionMode,
                reserveBottomBadge = rating != null || isDownloaded,
            )
        },
        coverOverlay = if (rating != null || isSelectionMode || progressLabel != null) {
            {
                progress?.takeIf { it.shouldShowCoverProgress }?.let { summary ->
                    LibraryCoverProgressBar(
                        fraction = summary.fraction,
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
        } else {
            null
        },
    )
}

private const val SelectedCoverScale = 0.94f
private val SelectedCoverRingWidth = 3.dp
