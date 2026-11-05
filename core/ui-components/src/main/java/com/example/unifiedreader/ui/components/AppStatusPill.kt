package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What a state means, which picks the dot's and the label's colours. */
enum class AppStatusTone { Good, Neutral, Problem }

/**
 * A state as a dot and a word, with no container: a server's "Connected" / "Offline" /
 * "Checking", the sync queue's count. An 8 dp dot, 8 before the label in 14 Medium; 28 dp tall so
 * it lines up with the controls beside it. [AppStatusTone.Good] is the dot in `primary` with
 * `onSurface` text, [AppStatusTone.Neutral] a ring in `outline` with `onSurfaceVariant` text (a
 * ring, because some themes' `outline` sits close to `primary`), [AppStatusTone.Problem] both in `error`.
 */
@Composable
fun AppStatusPill(
    label: String,
    tone: AppStatusTone,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (dot: Color, text: Color) = when (tone) {
        AppStatusTone.Good -> scheme.primary to scheme.onSurface
        AppStatusTone.Neutral -> scheme.outline to scheme.onSurfaceVariant
        AppStatusTone.Problem -> scheme.error to scheme.error
    }
    Row(
        modifier = modifier.heightIn(min = AppStatusPillDefaults.height),
        horizontalArrangement = Arrangement.spacedBy(AppStatusPillDefaults.dotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppStatusDot(color = dot, hollow = tone == AppStatusTone.Neutral)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = text,
            maxLines = 1,
        )
    }
}

object AppStatusPillDefaults {
    val height: Dp = 28.dp
    val dotSize: Dp = 8.dp
    val dotGap: Dp = 8.dp
    val ringWidth: Dp = 1.5.dp
}

/** The 8 dp state dot: filled, or a ring for a neutral state. Also before an [AppSettingsRow]'s line. */
@Composable
internal fun AppStatusDot(
    color: Color,
    hollow: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(AppStatusPillDefaults.dotSize)
            .then(
                if (hollow) {
                    Modifier.border(AppStatusPillDefaults.ringWidth, color, CircleShape)
                } else {
                    Modifier.background(color, CircleShape)
                },
            ),
    )
}
