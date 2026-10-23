package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppChipDefaults
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppMenuDefaults
import com.example.unifiedreader.ui.components.AppMenuHeading
import com.example.unifiedreader.ui.components.AppMenuSwitchItem
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow

data class LibraryBrowseOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val enabled: Boolean = true,
)

data class LibraryBrowseOptionSection(
    val id: String,
    val title: String,
    val options: List<LibraryBrowseOption>,
)

data class LibraryBrowseLockedOption(
    val sectionTitle: String,
    val label: String,
)

@Composable
fun LibraryBrowseOptionsMenu(
    label: String,
    selected: Boolean,
    optionSections: List<LibraryBrowseOptionSection>,
    gridCellSizeDp: Int,
    fadeReadItems: Boolean,
    onOptionSelected: (sectionId: String, optionId: String) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    lockedOption: LibraryBrowseLockedOption? = null,
    showFadeReadItems: Boolean = true,
    showGridSizeOptions: Boolean = true,
    leadingIcon: ImageVector = AppIcons.Tune,
    leadingIconContentDescription: String? = null,
    viewSectionTitle: String = "View",
    fadeReadItemsLabel: String = "Fade read books",
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val coverSize = LibraryCoverSize.of(gridCellSizeDp)
    Box(modifier = modifier) {
        AppChip(
            selected = selected,
            onClick = { expanded = true },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = leadingIconContentDescription,
                    modifier = Modifier.size(AppChipDefaults.iconSize),
                )
            },
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        AppMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(LibraryBrowseOptionsMenuWidth),
        ) {
            if (lockedOption != null) {
                AppMenuHeading(text = lockedOption.sectionTitle)
                AppMenuChoiceItem(
                    text = lockedOption.label,
                    selected = true,
                    enabled = false,
                    onClick = {},
                )
            } else {
                optionSections.forEach { section ->
                    AppMenuHeading(text = section.title)
                    section.options.forEach { option ->
                        AppMenuChoiceItem(
                            text = option.label,
                            selected = option.selected,
                            enabled = option.enabled,
                            onClick = {
                                expanded = false
                                onOptionSelected(section.id, option.id)
                            },
                        )
                    }
                }
            }
            val hasOrderSection = lockedOption != null || optionSections.isNotEmpty()
            if (showFadeReadItems || showGridSizeOptions) {
                if (hasOrderSection) {
                    AppDivider(modifier = Modifier.padding(vertical = 6.dp))
                }
                AppMenuHeading(text = viewSectionTitle)
            }
            if (showFadeReadItems) {
                AppMenuSwitchItem(
                    text = fadeReadItemsLabel,
                    checked = fadeReadItems,
                    onCheckedChange = onFadeReadItemsChanged,
                )
            }
            if (showGridSizeOptions) {
                // A setting like the switch above: the menu stays open so the grid can be seen changing.
                Text(
                    text = "Cover size",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    modifier = Modifier.padding(
                        start = AppMenuDefaults.horizontalPadding,
                        end = AppMenuDefaults.horizontalPadding,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                )
                AppSegmentedRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                ) {
                    LibraryCoverSize.entries.forEach { size ->
                        AppSegment(
                            selected = size == coverSize,
                            onClick = {
                                if (size != coverSize) onGridCellSizeDpChanged(size.cellSizeDp)
                            },
                            label = { Text(text = size.label, maxLines = 1) },
                            modifier = Modifier.testTag(LibraryGridSizeOptionTagPrefix + size.cellSizeDp),
                        )
                    }
                }
            }
        }
    }
}

private val LibraryBrowseOptionsMenuWidth = 300.dp
