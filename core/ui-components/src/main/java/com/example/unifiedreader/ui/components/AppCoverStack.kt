package com.example.unifiedreader.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A series as a thing you can see: its first covers ([coverCount], three at most) fanned, its [name] under
 * them and a [line] ("41 · 12 read"). The caller draws each cover with [cover], filling the modifier it gets;
 * index 0 is the front one. Selected, the front cover gets a 2 `primary` ring.
 */
@Composable
fun AppCoverStack(
    name: String,
    line: String?,
    coverCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    val shape = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius)
    val shown = coverCount.coerceIn(1, AppCoverStackDefaults.maxCovers)
    Column(
        modifier = modifier
            .width(AppCoverStackDefaults.width)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected },
        verticalArrangement = Arrangement.spacedBy(AppCoverStackDefaults.gap),
    ) {
        Box(modifier = Modifier.size(AppCoverStackDefaults.artWidth, AppCoverStackDefaults.artHeight)) {
            // Back to front: the back covers turned and set towards the end.
            for (index in shown - 1 downTo 0) {
                val back = AppCoverStackDefaults.backLayers.getOrNull(index - 1)
                val coverModifier = Modifier
                    .offset(x = back?.offset ?: 0.dp, y = back?.top ?: AppCoverStackDefaults.frontTop)
                    .width(AppCoverStackDefaults.coverWidth)
                    .aspectRatio(2f / 3f)
                    .then(if (back != null) Modifier.rotate(back.degrees) else Modifier.shadow(AppCoverStackDefaults.frontElevation, shape))
                    .then(if (index == 0 && selected) Modifier.border(AppCoverStackDefaults.ring, MaterialTheme.colorScheme.primary, shape) else Modifier)
                cover(index, coverModifier)
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        line?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

object AppCoverStackDefaults {
    const val maxCovers: Int = 3
    val width: Dp = 96.dp
    val artWidth: Dp = 92.dp
    val artHeight: Dp = 118.dp
    val coverWidth: Dp = 64.dp
    val frontTop: Dp = 8.dp
    val frontElevation: Dp = 4.dp
    val ring: Dp = 2.dp
    val gap: Dp = 6.dp
    val rowGap: Dp = 16.dp

    internal data class BackLayer(val offset: Dp, val top: Dp, val degrees: Float)

    // The second cover 10 to the end and turned 2°, the third 20 and 6°.
    internal val backLayers: List<BackLayer> = listOf(
        BackLayer(offset = 10.dp, top = 4.dp, degrees = 2f),
        BackLayer(offset = 20.dp, top = 0.dp, degrees = 6f),
    )
}
