package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuItem
import com.example.unifiedreader.ui.components.AppTextField

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
fun LibraryBrowseSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Search library",
    clearContentDescription: String = "Clear search",
) {
    AppTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.libraryAccentTextFieldTreatment(selected = query.isNotEmpty()),
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                AppIconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = clearContentDescription,
                    )
                }
            }
        } else {
            null
        },
        label = { Text(text = label) },
        shape = libraryAccentTextFieldShape(),
    )
}

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
    leadingIcon: ImageVector = Icons.Default.Tune,
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
            modifier = Modifier.libraryAccentChipTreatment(selected = selected),
            colors = libraryAccentFilterChipColors(),
            shape = libraryAccentFilterChipShape(),
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = leadingIconContentDescription,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
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
            var needsDivider = false
            if (lockedOption != null) {
                LibraryBrowseDropdownSectionHeader(text = lockedOption.sectionTitle)
                AppMenuItem(
                    text = { Text(text = lockedOption.label) },
                    enabled = false,
                    onClick = {},
                )
                needsDivider = true
            } else {
                optionSections.forEachIndexed { index, section ->
                    if (index > 0) {
                        AppDivider()
                    }
                    LibraryBrowseDropdownSectionHeader(text = section.title)
                    section.options.forEach { option ->
                        AppMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            enabled = option.enabled,
                            onClick = {
                                expanded = false
                                onOptionSelected(section.id, option.id)
                            },
                        )
                    }
                    needsDivider = true
                }
            }
            if (needsDivider && (showFadeReadItems || (showGridSizeOptions && gridSizeOptions.isNotEmpty()))) {
                AppDivider()
            }
            if (showFadeReadItems) {
                LibraryBrowseDropdownSectionHeader(text = viewSectionTitle)
                AppMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = fadeReadItems,
                                onCheckedChange = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = fadeReadItemsLabel,
                                fontWeight = if (fadeReadItems) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    },
                    onClick = {
                        onFadeReadItemsChanged(!fadeReadItems)
                    },
                )
                if (showGridSizeOptions && gridSizeOptions.isNotEmpty()) {
                    AppDivider()
                }
            }
            if (showGridSizeOptions && gridSizeOptions.isNotEmpty()) {
                LibraryBrowseDropdownSectionHeader(text = gridSizeSectionTitle)
                gridSizeOptions.forEach { option ->
                    AppMenuItem(
                        modifier = Modifier.testTag(LibraryGridSizeOptionTagPrefix + option.cellSizeDp),
                        text = {
                            Text(
                                text = "${option.cellSizeDp}dp cards · ${option.columnCount} cols",
                                fontWeight = if (option.cellSizeDp == sanitizedGridCellSizeDp) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        },
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

@Composable
private fun LibraryBrowseDropdownSectionHeader(
    text: String,
) {
    AppMenuItem(
        text = {
            Text(
                text = text,
                fontWeight = FontWeight.Medium,
            )
        },
        onClick = {},
        enabled = false,
    )
}
