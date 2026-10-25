package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppButtonDefaults
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.theme.AppCoverColors
import java.time.ZoneId

/**
 * The card for the book being read: its cover, an [overline] ("Continue reading", with when it
 * was last read), the title and author, where the reader is (the chapter and its name, or the
 * page), the progress with [timeLeftMillis] when the journal knows the pace, a bar broken at the
 * chapters, and Continue plus Details. On tablets ([compact] false) the cover is [coverWidth]
 * (the grid's cell width) beside the text; on phones it is 96 dp, with the progress and the
 * buttons under it. Tinted with the cover's colours, or the theme accent for a cover without any.
 */
@Composable
fun LibraryReadingCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    overline: String,
    coverWidth: Dp,
    compact: Boolean,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    imageVariant: LibraryCoverImageVariant,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    continueLabel: String = "Continue",
    actionContext: LibraryItemActionContext = LibraryItemActionContext(),
    timeLeftMillis: Long? = null,
) {
    val lastReadAt = progress?.lastReadAtEpochMillis ?: 0L
    val headline = remember(overline, lastReadAt) {
        val day = libraryLastReadDay(lastReadAt, nowMillis = System.currentTimeMillis(), zone = ZoneId.systemDefault())
        listOfNotNull(overline, day?.text).joinToString(" · ")
    }
    // The cover's colours, the theme accent until they arrive (or for a cover without any).
    val scheme = MaterialTheme.colorScheme
    val tint = animateCoverColors(
        rememberLibraryCoverColors(item, coverImageUrlProvider) ?: AppCoverColors(
            tintStart = scheme.primaryContainer,
            tintEnd = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            track = scheme.onPrimaryContainer.copy(alpha = 0.16f),
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            accentContainer = scheme.primaryContainer,
            onAccentContainer = scheme.onPrimaryContainer,
        ),
    )
    val colors = LibraryReadingCardColors(
        content = tint.content,
        track = tint.track,
        accent = tint.accent,
        onAccent = tint.onAccent,
    )
    val cover = @Composable { width: Dp ->
        Box(
            modifier = Modifier
                .width(width)
                .shadow(elevation = 18.dp, shape = RoundedCornerShape(6.dp), clip = false),
        ) {
            VisibleResolvedLibraryCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                imageVariant = imageVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(LibraryCoverAspectRatio),
                emptyCoverContent = {
                    LibraryCoverPlaceholder(item = item, reserveTopBadge = false, reserveBottomBadge = false)
                },
            )
            progress?.takeIf { it.shouldShowCoverProgress }?.let { summary ->
                LibraryCoverProgressBar(
                    fraction = summary.fraction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }
    }
    val actions = @Composable { fillContinue: Boolean ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryReadingCardContinue(
                label = continueLabel,
                colors = colors,
                onClick = onContinue,
                modifier = if (fillContinue) Modifier.weight(1f) else Modifier,
            )
            LibraryReadingCardDetails(
                colors = colors,
                onClick = { actionContext.onOpenItemPage(item) },
            )
        }
    }

    val cardShape = RoundedCornerShape(LibraryReadingCardDefaults.cornerRadius)
    AppPanel(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .coverTint(tint),
        shape = cardShape,
        color = Color.Transparent,
        contentColor = colors.content,
    ) {
        if (compact) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    cover(LibraryReadingCardDefaults.compactCoverWidth)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = LibraryReadingCardDefaults.compactCoverWidth * 1.5f),
                    ) {
                        LibraryReadingCardHead(item, progress, headline, compact = true)
                    }
                }
                LibraryReadingCardProgress(progress, timeLeftMillis, colors, Modifier.padding(top = 16.dp))
                Box(modifier = Modifier.padding(top = 16.dp)) { actions(true) }
            }
        } else {
            // Given a height (beside the shelf tabs), the card fills it: the cover centres and the
            // progress and buttons stay at the bottom. Otherwise it is as tall as its cover.
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(LibraryReadingCardDefaults.padding),
                horizontalArrangement = Arrangement.spacedBy(LibraryReadingCardDefaults.padding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cover(coverWidth)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heightIn(min = max(coverWidth * 1.5f, LibraryReadingCardDefaults.minTextHeight)),
                ) {
                    LibraryReadingCardHead(item, progress, headline, compact = false)
                    Spacer(modifier = Modifier.weight(1f))
                    // At least this much under the place, so a two-line title doesn't run into the progress.
                    LibraryReadingCardProgress(progress, timeLeftMillis, colors, Modifier.padding(top = 12.dp))
                    Box(modifier = Modifier.padding(top = 16.dp)) { actions(false) }
                }
            }
        }
    }
}

@Composable
private fun LibraryReadingCardContinue(
    label: String,
    colors: LibraryReadingCardColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppButton(
        onClick = onClick,
        modifier = modifier.height(LibraryReadingCardDefaults.buttonHeight),
        colors = AppButtonDefaults.filledColors(containerColor = colors.accent, contentColor = colors.onAccent),
        contentPadding = LibraryReadingCardDefaults.buttonPadding,
    ) {
        Icon(
            imageVector = AppIcons.MenuBook,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(20.dp),
        )
        Text(text = label, style = LibraryReadingCardDefaults.buttonTextStyle())
    }
}

/** Details opens the item's book page. */
@Composable
private fun LibraryReadingCardDetails(
    colors: LibraryReadingCardColors,
    onClick: () -> Unit,
) {
    AppButton(
        onClick = onClick,
        modifier = Modifier.height(LibraryReadingCardDefaults.buttonHeight),
        colors = AppButtonDefaults.tonalColors(containerColor = colors.track, contentColor = colors.content),
        contentPadding = LibraryReadingCardDefaults.buttonPadding,
    ) {
        Text(text = "Details", style = LibraryReadingCardDefaults.buttonTextStyle())
    }
}

internal class LibraryReadingCardColors(
    val content: Color,
    /** The progress track and the tonal button. */
    val track: Color,
    val accent: Color,
    val onAccent: Color,
)

object LibraryReadingCardDefaults {
    val cornerRadius: Dp = 24.dp
    val padding: Dp = 22.dp
    val compactCoverWidth: Dp = 96.dp
    val buttonHeight: Dp = 44.dp
    val buttonPadding: PaddingValues = PaddingValues(horizontal = 20.dp)

    /** The text column never gets shorter than this, so small covers leave room for the buttons. */
    val minTextHeight: Dp = 214.dp

    @Composable
    internal fun buttonTextStyle() = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

/**
 * At this many grid columns or more the reading card spans half of them instead of the whole row:
 * beside the shelf tabs on the home, and on its own above a series' issues.
 */
const val LibraryReadingCardSideBySideColumns = 6

/** The width of [cells] grid cells and the gaps between them. */
private fun LibraryGridColumns.span(cells: Int, spacing: Dp): Dp = cellWidth * cells + spacing * (cells - 1)

/** The reading card's width in these columns, or null below [LibraryReadingCardSideBySideColumns] (the full row). */
fun LibraryGridColumns.readingCardWidth(spacing: Dp): Dp? =
    if (count >= LibraryReadingCardSideBySideColumns) span(LibraryReadingCardSideBySideColumns / 2, spacing) else null

/**
 * The top of a series: the reading card for [next], the issue being read or the next one to
 * start, with its cover at the grid's cell width ([gridMetrics]). On wide windows it is as wide as
 * the home's card, not the whole row.
 */
@Composable
fun LibrarySeriesReadingCard(
    next: LibrarySeriesNext,
    progress: ItemProgressSummary?,
    gridMetrics: LibraryGridMetrics,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    modifier: Modifier = Modifier,
    actionContext: LibraryItemActionContext = LibraryItemActionContext(),
    // An opened reading list names its own place ("Up next in this list · 3 of 5").
    overline: String? = null,
    timeLeftMillis: Long? = null,
) {
    val compact = isLibraryCompactWindow()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = with(LocalDensity.current) { libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics) }
        val cardWidth = columns.readingCardWidth(gridMetrics.spacing)
        LibraryReadingCard(
            item = next.item,
            progress = progress,
            overline = overline ?: if (next.inProgress) "Continue reading" else "Up next in this series",
            continueLabel = if (next.inProgress) "Continue" else "Start",
            coverWidth = columns.cellWidth,
            compact = compact,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            imageVariant = gridMetrics.coverImageVariant,
            onContinue = { onItemSelected(next.item) },
            actionContext = actionContext,
            timeLeftMillis = timeLeftMillis,
            modifier = if (cardWidth != null) Modifier.width(cardWidth) else Modifier,
        )
    }
}
