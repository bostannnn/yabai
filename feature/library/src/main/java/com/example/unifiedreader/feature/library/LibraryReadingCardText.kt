package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.appProgressFill

/**
 * The reading card's text, as tall as the cover: the [overline] in the cover's accent at its top,
 * the title, the [lines] under it, and at its bottom the bar with the percentage and the time left.
 * The title takes the largest size that fits its lines: 18 on two lines, down to 16. [tall] (the
 * narrow card beside the shelves): the overline may wrap to a second line, the title is 20 on up
 * to four, down to 18, and the percentage sits over a bar across the column instead of beside it.
 * The overline and title keep clear of the card's •••.
 */
@Composable
internal fun LibraryReadingCardText(
    title: String,
    overline: String,
    lines: List<String>,
    progress: ItemProgressSummary?,
    timeLeftMillis: Long?,
    colors: LibraryReadingCardColors,
    tall: Boolean,
    modifier: Modifier = Modifier,
) {
    val clearOfMenu = Modifier.padding(end = LibraryReadingCardDefaults.menuClearance)
    Column(modifier = modifier) {
        Text(
            text = overline.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            ),
            color = colors.accent,
            maxLines = if (tall) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = clearOfMenu,
        )
        BasicText(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = colors.content,
                fontWeight = FontWeight.Bold,
                lineHeight = 1.28.em,
                letterSpacing = (-0.01).em,
            ),
            maxLines = if (tall) 4 else 2,
            overflow = TextOverflow.Ellipsis,
            autoSize = if (tall) {
                TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 20.sp, stepSize = 2.sp)
            } else {
                TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 18.sp, stepSize = 2.sp)
            },
            modifier = clearOfMenu.padding(top = 4.dp),
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                color = colors.content.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        val fraction = progress?.fraction?.coerceIn(0f, 1f) ?: 0f
        val segments = libraryReadingCardSegments(progress)
        val label = @Composable {
            Text(
                text = libraryReadingCardBarLabel(progress, timeLeftMillis),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.content.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tall) {
            // The narrow card beside the shelves: the label over a bar across the column.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                label()
                LibraryReadingCardBar(fraction = fraction, segments = segments, colors = colors, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryReadingCardBar(fraction = fraction, segments = segments, colors = colors, modifier = Modifier.weight(1f))
                label()
            }
        }
    }
}

/** The 4 dp bar; with [segments] one piece per chapter (or page), 2 dp apart, each filled as far as the reader got into it. */
@Composable
private fun LibraryReadingCardBar(
    fraction: Float,
    segments: List<Float>?,
    colors: LibraryReadingCardColors,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(2.dp)
    if (segments == null) {
        Box(
            modifier = modifier
                .height(4.dp)
                .clip(shape)
                .appProgressFill(fraction = fraction, progressColor = colors.accent, trackColor = colors.track),
        )
        return
    }
    Row(
        modifier = modifier.height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.zipWithNext().forEach { (start, end) ->
            Box(
                modifier = Modifier
                    .weight(end - start)
                    .fillMaxHeight()
                    .clip(shape)
                    .appProgressFill(
                        fraction = ((fraction - start) / (end - start)).coerceIn(0f, 1f),
                        progressColor = colors.accent,
                        trackColor = colors.track,
                    ),
            )
        }
    }
}
