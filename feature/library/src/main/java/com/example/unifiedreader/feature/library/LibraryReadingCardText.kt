package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.ui.components.appProgressFill

/**
 * The top of the reading card's text: the [overline] ("Continue reading · yesterday"), the title,
 * the author, and where the reader is ([libraryReadingCardPlace]).
 */
@Composable
internal fun LibraryReadingCardHead(
    item: ReadableItem,
    progress: ItemProgressSummary?,
    overline: String,
    compact: Boolean,
) {
    Column {
        Text(
            text = overline.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            ),
            color = LocalContentColor.current.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val titleSize = if (compact) 22 else 30
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
        libraryReadingCardPlace(progress)?.let { place ->
            Column(modifier = Modifier.padding(top = if (compact) 14.dp else 20.dp)) {
                place.label?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = if (compact) 2.dp else 3.dp),
                    )
                }
                Text(
                    text = place.line,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (compact) 16.sp else 19.sp,
                        lineHeight = if (compact) 21.sp else 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The progress over the card's bar ("47% · Page 47 of 73", and "About 25 min left" when the
 * journal knows the pace) and the bar, broken at the chapters (or a short comic's pages).
 */
@Composable
internal fun LibraryReadingCardProgress(
    progress: ItemProgressSummary?,
    timeLeftMillis: Long?,
    colors: LibraryReadingCardColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val metaStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            val metaColor = LocalContentColor.current.copy(alpha = 0.82f)
            Text(
                text = libraryReadingCardProgressLabel(progress),
                style = metaStyle,
                color = metaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            timeLeftMillis?.let { millis ->
                Text(
                    text = libraryReadingTimeLeftLabel(millis),
                    style = metaStyle,
                    color = metaColor,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        LibraryReadingCardBar(
            fraction = progress?.fraction?.coerceIn(0f, 1f) ?: 0f,
            segments = libraryReadingCardSegments(progress),
            colors = colors,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The 6 dp bar; with [segments] one piece per chapter (or page), 3 dp apart, each filled as far as the reader got into it. */
@Composable
private fun LibraryReadingCardBar(
    fraction: Float,
    segments: List<Float>?,
    colors: LibraryReadingCardColors,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(3.dp)
    if (segments == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(shape)
                .appProgressFill(fraction = fraction, progressColor = colors.accent, trackColor = colors.track),
        )
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
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
