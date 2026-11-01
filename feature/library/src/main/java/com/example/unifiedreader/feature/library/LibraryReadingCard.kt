package com.example.unifiedreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.appPressScale
import com.example.unifiedreader.ui.theme.AppCoverColors
import com.example.unifiedreader.ui.theme.UnifiedReaderDefaultPresentationTokens
import java.time.ZoneId

/**
 * The card for the book being read, small: its cover and, as tall as the cover, an [overline]
 * ("Continue reading", with when it was last read) in the cover's accent, the title, the author and
 * where the reader is ([libraryReadingCardLines]; a [footnote] such as a reading order's "Then ..."
 * takes the place's spot), and the bar with the percentage and [timeLeftMillis] when the journal
 * knows the pace. A tap on the card continues the book ([onContinue], announced as
 * [continueLabel]); ••• opens its book page. On its own the card is
 * [LibraryReadingCardDefaults.height] tall; [tall] (beside the shelves, given their height) the cover
 * fills that height and the author and the place get a line each. Tinted with the cover's colours,
 * or the theme accent for a cover without any.
 */
@Composable
fun LibraryReadingCard(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    overline: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    imageVariant: LibraryCoverImageVariant,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    continueLabel: String = "Continue",
    actionContext: LibraryItemActionContext = LibraryItemActionContext(),
    timeLeftMillis: Long? = null,
    footnote: String? = null,
    tall: Boolean = false,
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
    val colors = LibraryReadingCardColors(content = tint.content, track = tint.track, accent = tint.accent)
    val author = item.librarySecondaryText()
    val lines = remember(author, progress, footnote, tall) { libraryReadingCardLines(author, progress, footnote, tall) }

    val cardShape = RoundedCornerShape(LibraryReadingCardDefaults.cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .appPressScale(interactionSource)
            .clip(cardShape)
            .coverTint(tint)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colors.content),
                onClickLabel = continueLabel,
                role = Role.Button,
                onClick = onContinue,
            ),
    ) {
        Row(
            modifier = Modifier
                .then(if (tall) Modifier.fillMaxHeight() else Modifier.height(LibraryReadingCardDefaults.height))
                .padding(LibraryReadingCardDefaults.padding),
            horizontalArrangement = Arrangement.spacedBy(LibraryReadingCardDefaults.coverGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(LibraryCoverAspectRatio, matchHeightConstraintsFirst = true)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(6.dp), clip = false),
            ) {
                VisibleResolvedLibraryCover(
                    item = item,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = onCoverVisible,
                    imageVariant = imageVariant,
                    modifier = Modifier.fillMaxSize(),
                    emptyCoverContent = {
                        LibraryCoverPlaceholder(item = item, reserveTopBadge = false, reserveBottomBadge = false)
                    },
                )
            }
            LibraryReadingCardText(
                title = item.libraryPrimaryText(),
                overline = headline,
                lines = lines,
                progress = progress,
                timeLeftMillis = timeLeftMillis,
                colors = colors,
                tall = tall,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        // In the card's top end corner: the 40 dp button draws the icon about 10 dp from both edges.
        AppIconButton(
            onClick = { actionContext.onOpenItemPage(item) },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = AppIcons.MoreHoriz,
                contentDescription = "Details",
                tint = colors.content.copy(alpha = 0.74f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal class LibraryReadingCardColors(
    val content: Color,
    /** The bar's track. */
    val track: Color,
    /** The overline and the bar's fill. */
    val accent: Color,
)

object LibraryReadingCardDefaults {
    val cornerRadius: Dp = UnifiedReaderDefaultPresentationTokens.cardCornerRadius
    val padding: Dp = AppLayout.inset

    /** On its own (above the shelves, at the top of a series): an 80 dp cover inside the inset. */
    val height: Dp = 160.dp
    val coverGap: Dp = 16.dp

    /** The overline and the title keep this clear of the •••. */
    internal val menuClearance: Dp = 28.dp
}

/**
 * At this many grid columns or more the reading card sits beside the shelf tabs on the home, over
 * [LibraryReadingCardBesideColumns] of them, and spans half of them above a series' issues.
 */
const val LibraryReadingCardSideBySideColumns = 6

/** Beside the shelves the card takes 2 of the 6 columns, about a third; the shelves take the rest. */
const val LibraryReadingCardBesideColumns = 2

/** The width of [cells] grid cells and the gaps between them. */
private fun LibraryGridColumns.span(cells: Int, spacing: Dp): Dp = cellWidth * cells + spacing * (cells - 1)

/** The card's width beside the home's shelves, or null below [LibraryReadingCardSideBySideColumns] (above them, the full row). */
fun LibraryGridColumns.readingCardBesideWidth(spacing: Dp): Dp? =
    if (count >= LibraryReadingCardSideBySideColumns) span(LibraryReadingCardBesideColumns, spacing) else null

/** The card's width above a series' issues: half the row from [LibraryReadingCardSideBySideColumns] columns, else null (the full row). */
fun LibraryGridColumns.readingCardWidth(spacing: Dp): Dp? =
    if (count >= LibraryReadingCardSideBySideColumns) span(LibraryReadingCardSideBySideColumns / 2, spacing) else null

/**
 * The top of a series: the reading card for [next], the issue being read or the next one to
 * start. On wide windows it spans half the row ([gridMetrics] gives the columns), nothing beside it.
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = with(LocalDensity.current) { libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics) }
        val cardWidth = columns.readingCardWidth(gridMetrics.spacing)
        LibraryReadingCard(
            item = next.item,
            progress = progress,
            overline = overline ?: if (next.inProgress) "Continue reading" else "Up next in this series",
            continueLabel = if (next.inProgress) "Continue" else "Start",
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            imageVariant = gridMetrics.coverImageVariant,
            onContinue = { onItemSelected(next.item) },
            actionContext = actionContext,
            timeLeftMillis = timeLeftMillis,
            modifier = if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth(),
        )
    }
}
