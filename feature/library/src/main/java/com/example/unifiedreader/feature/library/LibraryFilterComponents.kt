package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppDialog
import com.example.unifiedreader.ui.components.AppDialogCardDefaults
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppListDialog
import com.example.unifiedreader.ui.components.AppSearchField
import com.example.unifiedreader.ui.components.AppSearchFieldDefaults

internal fun searchableFacetDisplayValues(
    values: List<String>,
    selectedValues: Set<String>,
    valueKey: (String) -> String,
    valueLabel: (String) -> String = { it },
    letterKey: (String) -> String = { it },
): List<String> = (values + selectedValues)
    .distinctBy(valueKey)
    // Keys are computed once per value, not once per comparison: the author key in particular
    // is string work, and a few thousand values sorted on every recomposition was a visible hitch.
    .map { value ->
        val label = valueLabel(value)
        SearchableFacetSortEntry(value = value, label = label, letterKey = letterKey(label))
    }
    .sortedWith(searchableFacetDisplayComparator)
    .map(SearchableFacetSortEntry::value)

private class SearchableFacetSortEntry(
    val value: String,
    val label: String,
    val letterKey: String,
)

private val searchableFacetDisplayComparator: Comparator<SearchableFacetSortEntry> =
    Comparator { left, right ->
        left.letterKey.compareTo(right.letterKey, ignoreCase = true).takeIf { it != 0 }
            ?: left.label.compareTo(right.label, ignoreCase = true).takeIf { it != 0 }
            ?: left.value.compareTo(right.value, ignoreCase = true)
    }
