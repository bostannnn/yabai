package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A labelled group of settings rows: a small caption above one [AppPanel]. Rows inside are
 * [AppSettingsRow]s separated by [AppSettingsRowDivider]s.
 */
@Composable
fun AppSettingsGroup(
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label != null) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSettingsDefaults.horizontalPadding),
            )
        }
        AppPanel(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * One row in an [AppSettingsGroup]: optional leading icon, a title with optional supporting
 * text (after a small [supportingIcon], and with [supportingContent] such as a progress bar
 * under it), then an optional value, a custom [trailing] slot, and a chevron when the row opens
 * something ([onClick] set and [showChevron] true). A [selected] row sits on `surfaceSelected` and
 * draws all of its content in `onSurfaceSelected`, so it reads in every theme (High Contrast's
 * selection is white). [supportingColor] overrides the supporting text's colour (an error, say).
 */
@Composable
fun AppSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    showChevron: Boolean = onClick != null,
    supportingColor: Color = Color.Unspecified,
    supportingIcon: ImageVector? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val contentAlpha = if (enabled) 1f else 0.5f
    val titleColor = if (selected) semanticColors.onSurfaceSelected else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (selected) {
        semanticColors.onSurfaceSelected.copy(alpha = AppSettingsDefaults.selectedSecondaryAlpha)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconColor = if (selected) semanticColors.onSurfaceSelected else MaterialTheme.colorScheme.primary
    val resolvedSupportingColor = supportingColor.takeOrElse { secondaryColor }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(semanticColors.surfaceSelected) else Modifier)
            .then(clickModifier)
            .heightIn(min = AppSettingsDefaults.rowMinHeight)
            .padding(
                start = AppSettingsDefaults.horizontalPadding,
                end = if (showChevron) 12.dp else AppSettingsDefaults.horizontalPadding - 4.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(AppSettingsDefaults.iconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor.copy(alpha = contentAlpha),
                modifier = Modifier.size(AppSettingsDefaults.iconSize),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor.copy(alpha = contentAlpha),
            )
            if (supporting != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (supportingIcon != null) {
                        Icon(
                            imageVector = supportingIcon,
                            contentDescription = null,
                            tint = resolvedSupportingColor.copy(alpha = contentAlpha),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = resolvedSupportingColor.copy(alpha = contentAlpha),
                    )
                }
            }
            supportingContent?.invoke(this)
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryColor.copy(alpha = contentAlpha),
                maxLines = 1,
            )
        }
        if (trailing != null) {
            trailing()
        }
        if (showChevron) {
            Icon(
                imageVector = AppIcons.NavigateNext,
                contentDescription = null,
                tint = secondaryColor.copy(alpha = contentAlpha),
            )
        }
    }
}

/** The hairline between two rows of a group, inset past the leading icon by default. */
@Composable
fun AppSettingsRowDivider(
    modifier: Modifier = Modifier,
    inset: Dp = AppSettingsDefaults.dividerInset,
) {
    AppDivider(
        modifier = modifier.padding(start = inset),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

object AppSettingsDefaults {
    val horizontalPadding: Dp = 20.dp
    val rowMinHeight: Dp = 64.dp
    val iconSize: Dp = 24.dp
    val iconGap: Dp = 16.dp

    /** Divider inset for rows that carry a leading icon. */
    val dividerInset: Dp = horizontalPadding + iconSize + iconGap

    /** Divider inset for rows without an icon. */
    val dividerInsetNoIcon: Dp = horizontalPadding

    /** Supporting text, value and chevron of a selected row: `onSurfaceSelected` at this alpha. */
    const val selectedSecondaryAlpha: Float = 0.72f
}
