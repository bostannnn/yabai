package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth

/**
 * One of the journal's panels: the kit's raised panel with the journal's padding. In a row of the
 * journal's grid it fills the height the row gives it; elsewhere it is as tall as its content.
 */
@Composable
internal fun JournalPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = JournalPanelPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppPanel(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(horizontal = contentPadding, vertical = JournalPanelVerticalPadding),
            content = content,
        )
    }
}

/** The journal panels' side padding: the kit's panel inset at every width. */
internal val JournalPanelPadding: Dp = AppLayout.inset

private val JournalPanelVerticalPadding = 20.dp

/** A panel's 17 sp heading and the one line under it, with an optional action at the end. */
@Composable
internal fun JournalSectionHeader(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
                fontWeight = FontWeight.SemiBold,
            )
            summary?.takeIf(String::isNotBlank)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * The period at a glance, one panel: reading time, the days you read, pages, what was finished,
 * and books against comics by time. In a row with hairlines between them, two by two on phones.
 */
@Composable
internal fun JournalSummaryPanel(
    overview: JournalPeriodOverview,
    summary: JournalSummary,
) {
    val cells = buildList<@Composable (Modifier) -> Unit> {
        if (summary.readingMillis > 0L) {
            add { modifier ->
                SummaryFigure(formatJournalReadingTime(summary.readingMillis), "Reading time", summary.paceLine(), modifier)
            }
        }
        add { modifier -> SummaryFigure(summary.readingDays.counted("day"), "You read", summary.daysLine(overview.period), modifier) }
        if (summary.pages > 0) {
            add { modifier -> SummaryFigure(formatJournalCount(summary.pages), "Pages", summary.pagesLine(), modifier) }
        }
        add { modifier -> SummaryFigure(summary.finished.toString(), "Finished", summary.finishedLine(), modifier) }
        if (summary.bookMillis + summary.comicMillis > 0L) {
            add { modifier -> BooksAndComicsFigure(summary.bookMillis, summary.comicMillis, modifier) }
        }
    }
    val width = currentAppWindowWidth()
    val compact = width == AppWindowWidth.Compact
    // All in a row on a wide tablet, rows of three on a narrower one, two by two on phones; a short
    // last row's cells widen, so every row ends flush.
    val perRow = when (width) {
        AppWindowWidth.Compact -> 2
        AppWindowWidth.Medium -> 3
        AppWindowWidth.Expanded -> cells.size
    }
    val hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    JournalPanel(contentPadding = 0.dp) {
        cells.chunked(perRow).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(hairline))
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { index, cell ->
                    if (index > 0) Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(hairline))
                    cell(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 12.dp else 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryFigure(
    value: String,
    label: String,
    line: String?,
    modifier: Modifier = Modifier,
) {
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = figureText(value),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = if (compact) 26.sp else 30.sp,
                lineHeight = if (compact) 32.sp else 36.sp,
                fontFeatureSettings = "tnum",
                letterSpacing = (-0.01).em,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        line?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A figure with its units smaller than its numbers: "3 h 24 min", "9 days". */
private fun figureText(value: String): AnnotatedString = buildAnnotatedString {
    value.forEach { char ->
        if (char.isLetter()) {
            withStyle(SpanStyle(fontSize = 0.66.em)) { append(char) }
        } else {
            append(char)
        }
    }
}

/** Books against comics by reading time: a split bar and each side's time. */
@Composable
private fun BooksAndComicsFigure(
    bookMillis: Long,
    comicMillis: Long,
    modifier: Modifier = Modifier,
) {
    val bookColor = MaterialTheme.colorScheme.primary
    val comicColor = MaterialTheme.colorScheme.tertiary
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Books and comics",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (bookMillis > 0L) {
                Box(modifier = Modifier.weight(bookMillis.toFloat()).height(10.dp).clip(CircleShape).background(bookColor))
            }
            if (comicMillis > 0L) {
                Box(modifier = Modifier.weight(comicMillis.toFloat()).height(10.dp).clip(CircleShape).background(comicColor))
            }
        }
        Column {
            if (bookMillis > 0L) MixLegend("Books", formatJournalMinutes(bookMillis))
            if (comicMillis > 0L) MixLegend("Comics", formatJournalMinutes(comicMillis))
        }
    }
}

@Composable
private fun MixLegend(
    kind: String,
    time: String,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(kind) }
            append(" $time")
        },
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * When you read (Month): the time per hour of the day as 24 small bars, the hours most of it
 * falls in named in the heading's line and drawn in the warm accent.
 */
@Composable
internal fun JournalWhenYouReadPanel(
    buckets: List<JournalTimeBucket>,
    modifier: Modifier = Modifier,
    // In a row taller than the chart needs, the bars grow into the room.
    stretch: Boolean = false,
) {
    val maxMillis = buckets.maxOfOrNull(JournalTimeBucket::totalMillis)?.takeIf { it > 0L } ?: return
    val window = journalReadingHours(buckets)
    val inWindow = { hour: Int -> window != null && (hour - window.first + 24) % 24 < window.second }
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(title = "When you read", summary = journalReadingHoursLabel(buckets))
        BarChart(
            values = buckets.map(JournalTimeBucket::totalMillis),
            maxValue = maxMillis,
            labels = buckets.map { bucket -> bucket.label.takeIf { bucket.key % 6 == 0 } },
            highlighted = buckets.map { bucket -> inWindow(bucket.key) },
            highlightColor = MaterialTheme.colorScheme.tertiary,
            height = 72.dp,
            gap = 4.dp,
            description = "Reading by hour of the day. ${journalReadingHoursLabel(buckets)}",
            stretch = stretch,
            modifier = Modifier.padding(top = 16.dp).then(if (stretch) Modifier.weight(1f) else Modifier),
        )
    }
}

/**
 * Pages per day (Month) or per month (Year): bars from a shared baseline, the busiest one in the
 * accent and named in the heading's line. Pages count from where the reading started, so a jump
 * doesn't make a day.
 */
@Composable
internal fun JournalPagesPanel(
    overview: JournalPeriodOverview,
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
) {
    val bars = remember(overview) { overview.pagesBars() }
    val busiest = bars.maxByOrNull(JournalPagesBar::pages)?.takeIf { it.pages > 0 } ?: return
    val month = overview.period == JournalPeriod.MONTH
    val line = "Most ${if (month) "on" else "in"} ${busiest.name}: ${busiest.pages.counted("page")}"
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(title = if (month) "Pages per day" else "Pages per month", summary = line)
        BarChart(
            values = bars.map { bar -> bar.pages.toLong() },
            maxValue = busiest.pages.toLong(),
            labels = bars.mapIndexed { index, bar -> bar.label.takeIf { !month || index % 7 == 0 } },
            highlighted = bars.map { bar -> bar === busiest },
            highlightColor = MaterialTheme.colorScheme.primary,
            height = 72.dp,
            gap = if (month) 3.dp else 8.dp,
            description = "${if (month) "Pages per day" else "Pages per month"}. $line",
            stretch = stretch,
            modifier = Modifier.padding(top = 16.dp).then(if (stretch) Modifier.weight(1f) else Modifier),
        )
    }
}

/**
 * Reading time by month (Year): twelve bars, the busiest month in the accent and named in the
 * heading's line. Months still to come stay empty.
 */
@Composable
internal fun JournalTimeByMonthPanel(
    buckets: List<JournalTimeBucket>,
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
) {
    val busiest = journalBusiestMonth(buckets) ?: return
    val maxMillis = buckets.maxOf(JournalTimeBucket::totalMillis)
    JournalPanel(modifier = modifier) {
        JournalSectionHeader(title = "Reading time by month", summary = "Most in ${longMonthName(busiest)}")
        BarChart(
            values = buckets.map(JournalTimeBucket::totalMillis),
            maxValue = maxMillis,
            labels = buckets.map(JournalTimeBucket::label),
            highlighted = buckets.map { bucket -> bucket.key == busiest },
            highlightColor = MaterialTheme.colorScheme.primary,
            height = 72.dp,
            gap = 8.dp,
            description = "Reading time by month, most in ${longMonthName(busiest)}",
            stretch = stretch,
            modifier = Modifier.padding(top = 16.dp).then(if (stretch) Modifier.weight(1f) else Modifier),
        )
    }
}

/**
 * Bars from a shared baseline; an empty slot keeps a 2 dp stub so the scale reads. [height] tall,
 * or at least that and as tall as its panel lets it when [stretch].
 */
@Composable
private fun BarChart(
    values: List<Long>,
    maxValue: Long,
    labels: List<String?>,
    highlighted: List<Boolean>,
    highlightColor: Color,
    height: Dp,
    gap: Dp,
    description: String,
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
) {
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val stubColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (stretch) Modifier.weight(1f).heightIn(min = height) else Modifier.height(height)),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                val fraction = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (value > 0L) Modifier.fillMaxHeight(fraction).heightIn(min = 3.dp) else Modifier.height(2.dp))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                        .background(
                            when {
                                value <= 0L -> stubColor
                                highlighted[index] -> highlightColor
                                else -> barColor
                            },
                        ),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            labels.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label.orEmpty(),
                        // A label may be wider than its bar; it spills over the empty neighbours.
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
