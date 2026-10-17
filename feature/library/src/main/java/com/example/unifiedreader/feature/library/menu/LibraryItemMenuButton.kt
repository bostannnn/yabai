package com.example.unifiedreader.feature.library.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppTonalIconButton

enum class LibraryItemMenuButtonStyle {
    Tonal,
    CompactOverlay,
    CompactInline,
}

@Composable
fun LibraryItemMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LibraryItemMenuButtonStyle = LibraryItemMenuButtonStyle.Tonal,
) {
    when (style) {
        LibraryItemMenuButtonStyle.Tonal -> TonalLibraryItemMenuButton(
            onClick = onClick,
            modifier = modifier,
        )
        LibraryItemMenuButtonStyle.CompactOverlay -> CompactOverlayLibraryItemMenuButton(
            onClick = onClick,
            modifier = modifier,
        )
        LibraryItemMenuButtonStyle.CompactInline -> CompactInlineLibraryItemMenuButton(
            onClick = onClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun TonalLibraryItemMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More actions",
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CompactOverlayLibraryItemMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)

    Box(
        modifier = modifier
            .size(36.dp)
            .semantics {
                contentDescription = "More actions"
                role = Role.Button
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .drawWithCache {
                val cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                val dotRadius = 1.6.dp.toPx()
                val dotSpacing = 5.2.dp.toPx()
                val centerX = size.width / 2f
                val middleY = size.height / 2f
                onDrawBehind {
                    drawRoundRect(
                        color = containerColor,
                        cornerRadius = cornerRadius,
                    )
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(centerX, middleY - dotSpacing),
                    )
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(centerX, middleY),
                    )
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(centerX, middleY + dotSpacing),
                    )
                }
            }
    ) {}
}

@Composable
private fun CompactInlineLibraryItemMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .size(32.dp)
            .semantics {
                contentDescription = "More actions"
                role = Role.Button
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .drawWithCache {
                val dotRadius = 1.45.dp.toPx()
                val dotSpacing = 4.6.dp.toPx()
                val centerX = size.width / 2f
                val middleY = size.height / 2f
                onDrawBehind {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(centerX - dotSpacing, middleY),
                    )
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(centerX, middleY),
                    )
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(centerX + dotSpacing, middleY),
                    )
                }
            },
    ) {}
}
