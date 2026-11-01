package com.example.unifiedreader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A list as a row, for a page that holds many lists (a place on Lists, Lists' search): its
 * [cover] (the list's [AppListCover], drawn [AppListRowDefaults.coverWidth] wide into the
 * modifier it gets), the [name] in 15 sp SemiBold with any [highlight] in the accent, and one
 * [line] under it. A reading order with something read and something left passes [progress]
 * for a bar under the line. At the end: `keep` when [pinned], `tune` when it [updatesItself],
 * then the chevron.
 *
 * [onLongClick] opens the list's menu; the row sits on `surfaceContainerHighest` while [pressed]
 * (its menu is open). While several are being selected, [selected] is non-null: a round check
 * leads the row, the chevron goes, and a picked row sits on `surfaceSelected`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListRow(
    name: String,
    line: String,
    cover: @Composable (Modifier) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    progress: Float? = null,
    pinned: Boolean = false,
    updatesItself: Boolean = false,
    selected: Boolean? = null,
    pressed: Boolean = false,
    highlight: String? = null,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val picked = selected == true
    val background = when {
        picked -> semanticColors.surfaceSelected
        pressed -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> Color.Transparent
    }
    val content = if (picked) semanticColors.onSurfaceSelected else MaterialTheme.colorScheme.onSurface
    val secondary = if (picked) {
        semanticColors.onSurfaceSelected.copy(alpha = AppListRowDefaults.selectedSecondaryAlpha)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accent = MaterialTheme.colorScheme.primary
    val title = remember(name, highlight, accent) { highlighted(name, highlight, accent) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppListRowDefaults.minHeight)
            .background(background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            )
            .semantics { if (selected != null) this.selected = selected }
            .padding(AppListRowDefaults.contentPadding),
        horizontalArrangement = Arrangement.spacedBy(AppListRowDefaults.coverGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            if (selected != null) AppRoundCheck(checked = selected)
            Box(modifier = Modifier.width(AppListRowDefaults.coverWidth)) {
                cover(Modifier.fillMaxWidth())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppBookRowLine(text = line, color = secondary)
                if (progress != null) {
                    AppProgressBar(
                        fraction = progress,
                        height = AppListRowDefaults.progressHeight,
                        modifier = Modifier
                            .padding(top = AppListRowDefaults.progressTop)
                            .widthIn(max = AppListRowDefaults.progressMaxWidth)
                            .fillMaxWidth(),
                    )
                }
            }
            if (pinned) {
                Icon(imageVector = AppIcons.Keep, contentDescription = "Pinned", tint = secondary, modifier = Modifier.size(AppListRowDefaults.markSize))
            }
            if (updatesItself) {
                Icon(imageVector = AppIcons.Tune, contentDescription = "Updates itself", tint = secondary, modifier = Modifier.size(AppListRowDefaults.markSize))
            }
            if (selected == null) {
                Icon(imageVector = AppIcons.NavigateNext, contentDescription = null, tint = secondary)
            }
        }
    }
}

/** [text] with every case-insensitive occurrence of [query] in [color], for a search's matches. */
private fun highlighted(text: String, query: String?, color: Color): AnnotatedString {
    val needle = query?.trim().orEmpty()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var start = text.indexOf(needle, ignoreCase = true)
        while (start >= 0) {
            addStyle(SpanStyle(color = color), start, start + needle.length)
            start = text.indexOf(needle, startIndex = start + needle.length, ignoreCase = true)
        }
    }
}

/**
 * The line between two rows in a panel of `AppListRow`s or `AppBookRow`s: it starts under the
 * text, past the cover, so the covers read as one column.
 */
@Composable
fun AppListRowDivider(modifier: Modifier = Modifier) {
    AppSettingsRowDivider(modifier = modifier, inset = AppListRowDefaults.dividerInset)
}

object AppListRowDefaults {
    val minHeight: Dp = 76.dp
    val coverWidth: Dp = 40.dp
    val coverGap: Dp = 14.dp
    val contentPadding = PaddingValues(start = AppLayout.inset, end = 12.dp, top = 8.dp, bottom = 8.dp)
    val markSize: Dp = 18.dp
    val progressHeight: Dp = 4.dp
    val progressTop: Dp = 6.dp
    val progressMaxWidth: Dp = 220.dp
    const val selectedSecondaryAlpha: Float = 0.72f

    /** Where the divider between two rows starts: under the text, past the cover. */
    val dividerInset: Dp = AppLayout.inset + coverWidth + coverGap

    /** Above the first row and below the last in the panel that holds rows. */
    val panelPadding: Dp = 4.dp

    /**
     * The panel's shape for a run of rows split over several lazy items: the theme's card
     * corners on the [top] of the first piece and the [bottom] of the last, square between.
     */
    @Composable
    fun segmentShape(top: Boolean, bottom: Boolean): Shape {
        val radius = UnifiedReaderThemeTokens.presentation.cardCornerRadius
        return RoundedCornerShape(
            topStart = if (top) radius else 0.dp,
            topEnd = if (top) radius else 0.dp,
            bottomStart = if (bottom) radius else 0.dp,
            bottomEnd = if (bottom) radius else 0.dp,
        )
    }
}
