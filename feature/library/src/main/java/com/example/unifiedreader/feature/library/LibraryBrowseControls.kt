package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppChipDefaults
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppMenuDefaults
import com.example.unifiedreader.ui.components.AppMenuHeading
import com.example.unifiedreader.ui.components.AppMenuSwitchItem
import com.example.unifiedreader.ui.components.AppSlider
import kotlin.math.roundToInt

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
    // Just the icon, for a top bar: the label becomes its description.
    iconOnly: Boolean = false,
    // Cover shape (All the same / As printed) under Cover size; null leaves it out.
    onCoverAsPrintedChanged: ((Boolean) -> Unit)? = null,
) {
    val coverAsPrinted = LocalLibraryCoverAsPrinted.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val coverSize = LibraryCoverSize.of(gridCellSizeDp)
    Box(modifier = modifier) {
        if (iconOnly) {
            AppIconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = "Sort and view: $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
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
        }
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
                // A setting like the switch above: the menu stays open, and the grid follows the slider as it moves.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppMenuDefaults.horizontalPadding, end = AppMenuDefaults.horizontalPadding, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Cover size", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp))
                    Text(
                        text = coverSize.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AppSlider(
                    value = coverSize.ordinal.toFloat(),
                    onValueChange = { position ->
                        val picked = LibraryCoverSize.entries[position.roundToInt().coerceIn(0, LibraryCoverSize.entries.lastIndex)]
                        if (picked != coverSize) onGridCellSizeDpChanged(picked.cellSizeDp)
                    },
                    valueRange = 0f..LibraryCoverSize.entries.lastIndex.toFloat(),
                    // The stops between the two ends.
                    steps = LibraryCoverSize.entries.size - 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppMenuDefaults.horizontalPadding, end = AppMenuDefaults.horizontalPadding, bottom = 8.dp)
                        .testTag(LibraryGridSizeSliderTag)
                        .semantics {
                            contentDescription = "Cover size"
                            stateDescription = coverSize.label
                        },
                )
                if (onCoverAsPrintedChanged != null) {
                    Text(
                        text = "Cover shape",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        modifier = Modifier.padding(start = AppMenuDefaults.horizontalPadding, end = AppMenuDefaults.horizontalPadding, top = CoverShapeLabelTop),
                    )
                    AppSegmentedRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = AppMenuDefaults.horizontalPadding, end = AppMenuDefaults.horizontalPadding, top = CoverShapeRowTop, bottom = CoverShapeRowBottom),
                    ) {
                        AppSegment(selected = !coverAsPrinted, onClick = { onCoverAsPrintedChanged(false) }, label = { Text(text = "All the same") })
                        AppSegment(selected = coverAsPrinted, onClick = { onCoverAsPrintedChanged(true) }, label = { Text(text = "As printed") })
                    }
                }
            }
        }
    }
}

private val LibraryBrowseOptionsMenuWidth = 300.dp
private val CoverShapeLabelTop = 4.dp
private val CoverShapeRowTop = 8.dp
private val CoverShapeRowBottom = 12.dp
