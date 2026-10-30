package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryPageHistogram
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.ui.components.AppHistogram
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppRangeSlider
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSliderScale
import com.example.unifiedreader.ui.components.AppSwitch
import com.example.unifiedreader.ui.components.AppTextField

/**
 * The filter sheet's Pages section: the range at the label's end, a histogram of the view's
 * lengths over a range slider, From and To fields, and, while some books have no page count, a
 * switch that keeps them in. The handles snap to [com.example.unifiedreader.core.model.LibraryPageStops]
 * and the range applies when a drag ends; a typed bound applies on Done or when its field loses focus.
 */
@Composable
internal fun LibraryPagesFilterSection(
    range: LibraryPageRange?,
    histogram: LibraryPageHistogram,
    onRangeChanged: (LibraryPageRange?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The handles while they move, and after a drag until the filter comes back with its range.
    var dragPositions by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    LaunchedEffect(range) { dragPositions = null }
    val positions = dragPositions ?: libraryPageSliderPositions(range)
    val shownRange = dragPositions?.let { libraryPageRangeAt(it, range) } ?: range
    Column(modifier = modifier.fillMaxWidth()) {
        AppSheetLabel(text = LibraryFilterPanel.Pages.label, value = libraryPageRangeText(shownRange))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppLayout.inset),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppHistogram(
                counts = histogram.bars,
                highlighted = libraryPageHighlightedBars(positions),
            )
            AppRangeSlider(
                values = positions,
                onValuesChange = { moved -> dragPositions = snapLibraryPagePositions(positions, moved) },
                valueRange = 0f..LibraryPagePositionEnd.toFloat(),
                onValueChangeFinished = {
                    dragPositions?.let { finished ->
                        val picked = libraryPageRangeAt(finished, range)
                        if (picked != range) onRangeChanged(picked)
                    }
                },
            )
            AppSliderScale(marks = LibraryPageScaleMarks)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryPageBoundField(
                    label = "From",
                    placeholder = "0",
                    bound = shownRange?.min,
                    onCommit = { min -> onRangeChanged(libraryPageRange(min, range?.max, range?.includeUnknown ?: false)) },
                    modifier = Modifier.weight(1f),
                )
                LibraryPageBoundField(
                    label = "To",
                    placeholder = "No limit",
                    bound = shownRange?.max,
                    onCommit = { max -> onRangeChanged(libraryPageRange(range?.min, max, range?.includeUnknown ?: false)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (histogram.unknown > 0 || range?.includeUnknown == true) {
            val includeUnknown = range?.includeUnknown == true
            AppSettingsRow(
                title = "Include unknown length",
                supporting = libraryUnknownLengthText(histogram.unknown),
                // Nothing is left out while every length is in.
                enabled = range != null,
                onClick = { range?.let { onRangeChanged(it.copy(includeUnknown = !includeUnknown)) } },
                showChevron = false,
                trailing = {
                    AppSwitch(checked = includeUnknown, onCheckedChange = null, enabled = range != null)
                },
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

/** One bound of the range: digits only; it applies on Done or when the field loses focus. */
@Composable
private fun LibraryPageBoundField(
    label: String,
    placeholder: String,
    bound: Int?,
    onCommit: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var text by remember(bound) { mutableStateOf(bound?.toString().orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    AppTextField(
        value = text,
        onValueChange = { typed -> text = libraryPageBoundInput(typed) },
        modifier = modifier.onFocusChanged { state ->
            if (focused && !state.isFocused) {
                val typed = parseLibraryPageBound(text)
                if (typed != bound) onCommit(typed)
            }
            focused = state.isFocused
        },
        singleLine = true,
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}
