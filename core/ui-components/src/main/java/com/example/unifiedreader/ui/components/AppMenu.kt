package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A popup menu anchored to its parent: `surfaceContainerHigh` with 16 dp corners. */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    shape: Shape = RoundedCornerShape(AppMenuDefaults.cornerRadius),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = shape,
        containerColor = containerColor,
        content = content,
    )
}

/** A 44 dp menu row. */
@Composable
fun AppMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = AppMenuDefaults.itemPadding,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.height(AppMenuDefaults.itemHeight),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
    )
}

/** A section title inside a menu ("Sort by", "View"): plain text, not a disabled row. */
@Composable
fun AppMenuHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(
                start = AppMenuDefaults.horizontalPadding,
                end = AppMenuDefaults.horizontalPadding,
                top = 12.dp,
                bottom = 4.dp,
            )
            .semantics { heading() },
    )
}

/** One choice of a menu section: a check column, and the current choice checked in the accent and bold. */
@Composable
fun AppMenuChoiceItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(AppMenuDefaults.checkColumnWidth)) {
                    if (selected) {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = text,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        enabled = enabled,
    )
}

/** An on/off setting in a menu: the label, and an [AppSwitch] at the end. The menu stays open. */
@Composable
fun AppMenuSwitchItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
            }
        },
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        enabled = enabled,
    )
}

object AppMenuDefaults {
    val cornerRadius: Dp = 16.dp
    val itemHeight: Dp = 44.dp
    val horizontalPadding: Dp = 18.dp
    val itemPadding: PaddingValues = PaddingValues(horizontal = horizontalPadding)

    /** The column a choice's check sits in, so checked and unchecked labels line up. */
    val checkColumnWidth: Dp = 20.dp
}
