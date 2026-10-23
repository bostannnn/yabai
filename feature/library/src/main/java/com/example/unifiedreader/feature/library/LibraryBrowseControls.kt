package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppChipDefaults
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppMenuHeading
import com.example.unifiedreader.ui.components.AppMenuSwitchItem

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
    gridSizeOptions: List<LibraryGridSizeOption>,
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
    gridSizeSectionTitle: String = "Grid size",
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
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
            if (showFadeReadItems) {
                AppMenuHeading(text = viewSectionTitle)
                AppMenuSwitchItem(
                    text = fadeReadItemsLabel,
                    checked = fadeReadItems,
                    onCheckedChange = onFadeReadItemsChanged,
                )
            }
            if (showGridSizeOptions && gridSizeOptions.isNotEmpty()) {
                AppMenuHeading(text = gridSizeSectionTitle)
                gridSizeOptions.forEach { option ->
                    AppMenuChoiceItem(
                        modifier = Modifier.testTag(LibraryGridSizeOptionTagPrefix + option.cellSizeDp),
                        text = "${option.cellSizeDp}dp cards · ${option.columnCount} cols",
                        selected = option.cellSizeDp == sanitizedGridCellSizeDp,
                        onClick = {
                            expanded = false
                            if (option.cellSizeDp != sanitizedGridCellSizeDp) {
                                onGridCellSizeDpChanged(option.cellSizeDp)
                            }
                        },
                    )
                }
            }
        }
    }
}
