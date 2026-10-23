package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.appProgressFill

/**
 * The card for the book being read: its cover, an [overline] ("Continue reading"), the title
 * and author, the progress, and Continue plus Details. On tablets ([compact] false) the cover is
 * [coverWidth] (the grid's cell width) beside the text; on phones it is 96 dp with the buttons in
 * a row under it. Tinted with the theme accent until covers tint it (step 8.6).
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
    onDeleteImportedRequested: (ReadableItem) -> Unit = {},
) {
    val colors = LibraryReadingCardColors(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        accent = MaterialTheme.colorScheme.primary,
        onAccent = MaterialTheme.colorScheme.onPrimary,
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
            if (actionContext.hasMenuContent(item)) {
                LibraryReadingCardDetails(
                    item = item,
                    colors = colors,
                    actionContext = actionContext,
                    onDeleteImportedRequested = onDeleteImportedRequested,
                )
            }
        }
    }

    AppPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LibraryReadingCardDefaults.cornerRadius),
        color = colors.container,
        contentColor = colors.content,
    ) {
        if (compact) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    cover(LibraryReadingCardDefaults.compactCoverWidth)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(LibraryReadingCardDefaults.compactCoverWidth * 1.5f),
                    ) {
                        LibraryReadingCardText(item, progress, overline, colors, titleSize = 22, compact = true)
                    }
                }
                Box(modifier = Modifier.padding(top = 16.dp)) { actions(true) }
            }
        } else {
            Row(
                modifier = Modifier.padding(LibraryReadingCardDefaults.padding),
                horizontalArrangement = Arrangement.spacedBy(LibraryReadingCardDefaults.padding),
            ) {
                cover(coverWidth)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(max(coverWidth * 1.5f, LibraryReadingCardDefaults.minTextHeight)),
                ) {
                    LibraryReadingCardText(item, progress, overline, colors, titleSize = 30, compact = false)
                    Box(modifier = Modifier.padding(top = 16.dp)) { actions(false) }
                }
            }
        }
    }
}

/** The overline, title, author and progress; the progress sits at the bottom of the column. */
@Composable
private fun ColumnScope.LibraryReadingCardText(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    overline: String,
    colors: LibraryReadingCardColors,
    titleSize: Int,
    compact: Boolean,
) {
    Text(
        text = overline.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.em,
        ),
        color = LocalContentColor.current.copy(alpha = 0.72f),
        maxLines = 1,
    )
    Text(
        text = item.libraryPrimaryText(),
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = titleSize.sp,
            lineHeight = (titleSize + 4).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.01).em,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = if (compact) 6.dp else 8.dp),
    )
    item.librarySecondaryText()?.let { subtitle ->
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
            color = LocalContentColor.current.copy(alpha = 0.74f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (compact) 4.dp else 6.dp),
        )
    }
    Spacer(modifier = Modifier.weight(1f))
    val fraction = progress?.fraction?.coerceIn(0f, 1f) ?: 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val metaStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = progress?.let(::libraryReadingCardPositionLabel) ?: "Not started",
            style = metaStyle,
            color = LocalContentColor.current.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = progress?.percentLabel ?: "0%",
            style = metaStyle,
            color = LocalContentColor.current.copy(alpha = 0.82f),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .appProgressFill(fraction = fraction, progressColor = colors.accent, trackColor = colors.track),
    )
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
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
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

/** Details opens the item's menu until the book page (step 8.5) takes its place. */
@Composable
private fun RowScope.LibraryReadingCardDetails(
    item: ReadableItem,
    colors: LibraryReadingCardColors,
    actionContext: LibraryItemActionContext,
    onDeleteImportedRequested: (ReadableItem) -> Unit,
) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    Box {
        AppButton(
            onClick = { expanded = true },
            modifier = Modifier.height(LibraryReadingCardDefaults.buttonHeight),
            colors = ButtonDefaults.buttonColors(containerColor = colors.track, contentColor = colors.content),
            contentPadding = LibraryReadingCardDefaults.buttonPadding,
        ) {
            Text(text = "Details", style = LibraryReadingCardDefaults.buttonTextStyle())
        }
        LibraryItemMenuPopup(
            item = item,
            actionContext = actionContext,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            onDeleteImportedRequested = onDeleteImportedRequested,
        )
    }
}

/**
 * "Chapter 3 of 6" when the reader saved the chapter, "Page 47 of 73" when the page is known,
 * otherwise the summary's own label.
 */
internal fun libraryReadingCardPositionLabel(progress: ItemProgressSummary): String {
    val chapter = progress.chapter
    val chapterCount = progress.chapterCount
    if (chapter != null && chapterCount != null && chapterCount > 0) return "Chapter $chapter of $chapterCount"
    val pageCount = progress.pageCount?.takeIf { it > 0 } ?: return progress.label
    val page = (progress.currentPage ?: (progress.fraction * pageCount).toInt().coerceAtLeast(1))
        .coerceIn(1, pageCount)
    return "Page $page of $pageCount"
}

private class LibraryReadingCardColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color,
) {
    /** The progress track and the tonal button: the card's text color, faint. */
    val track: Color = content.copy(alpha = 0.16f)
}

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
 * The top of a series: the reading card for [next], the issue being read or the next one to
 * start, with its cover at the grid's cell width ([gridMetrics]).
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
    onDeleteImportedRequested: (ReadableItem) -> Unit = {},
) {
    val compact = isLibraryCompactWindow()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val coverWidth = with(LocalDensity.current) {
            libraryGridColumns(availableWidth = maxWidth, metrics = gridMetrics).cellWidth
        }
        LibraryReadingCard(
            item = next.item,
            progress = progress,
            overline = if (next.inProgress) "Continue reading" else "Up next in this series",
            continueLabel = if (next.inProgress) "Continue" else "Start",
            coverWidth = coverWidth,
            compact = compact,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            imageVariant = gridMetrics.coverImageVariant,
            onContinue = { onItemSelected(next.item) },
            actionContext = actionContext,
            onDeleteImportedRequested = onDeleteImportedRequested,
        )
    }
}
