package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.applyLibraryFilters
import com.example.unifiedreader.core.model.applyLibrarySearch
import com.example.unifiedreader.core.model.applyLibrarySort
import java.math.BigDecimal
import java.util.Locale

fun buildVisibleLibraryItems(
    sourceItems: List<ReadableItem>,
    filterState: LibraryFilterState,
    searchQuery: String,
    sortMode: LibrarySortMode,
    downloadedRemoteItemIds: Set<String> = emptySet(),
    lastReadAtEpochMillisByItemId: Map<String, Long> = emptyMap(),
    forceSeriesIssueOrder: Boolean = false,
): List<ReadableItem> {
    val filteredItems = applyLibraryFilters(
        items = sourceItems,
        filters = filterState,
        downloadedRemoteItemIds = downloadedRemoteItemIds,
    )
    val searchedItems = applyLibrarySearch(
        items = filteredItems,
        query = searchQuery,
        tieBreaker = if (searchQuery.isNotBlank() && forceSeriesIssueOrder) {
            canonicalSeriesIssueComparator()
        } else {
            null
        },
    )
    if (searchQuery.isNotBlank()) {
        return searchedItems
    }
    return if (forceSeriesIssueOrder) {
        searchedItems.sortedInCanonicalSeriesIssueOrder()
    } else {
        applyLibrarySort(
            items = searchedItems,
            sortMode = sortMode,
            lastReadAtEpochMillisByItemId = lastReadAtEpochMillisByItemId,
        )
    }
}

internal fun List<ReadableItem>.sortedInCanonicalSeriesIssueOrder(): List<ReadableItem> = withIndex()
    .sortedWith(
        Comparator<IndexedValue<ReadableItem>> { left, right ->
            canonicalSeriesIssueComparator().compare(left.value, right.value)
        }
            .thenBy { indexed -> indexed.index },
    )
    .map(IndexedValue<ReadableItem>::value)

private fun canonicalSeriesIssueComparator(): Comparator<ReadableItem> =
    Comparator<ReadableItem> { left, right ->
        compareNaturalStrings(
            left.seriesIssueOrderKey(),
            right.seriesIssueOrderKey(),
        )
    }
        .thenComparator { left, right ->
            compareNaturalStrings(left.title, right.title)
        }
        .thenComparator { left, right ->
            compareNaturalStrings(
                left.comicMetadata?.issueTitle,
                right.comicMetadata?.issueTitle,
            )
        }

private fun ReadableItem.seriesIssueOrderKey(): String = listOfNotNull(
    comicMetadata?.volume?.trim()?.takeIf(String::isNotEmpty),
    comicMetadata?.issueNumber?.trim()?.takeIf(String::isNotEmpty),
).joinToString(" ").ifBlank {
    comicMetadata?.issueTitle?.trim()?.takeIf(String::isNotEmpty)
        ?: title.trim()
}

private fun compareNaturalStrings(
    left: String?,
    right: String?,
): Int {
    val normalizedLeft = left?.trim().orEmpty()
    val normalizedRight = right?.trim().orEmpty()
    if (normalizedLeft == normalizedRight) return 0
    if (normalizedLeft.isEmpty()) return 1
    if (normalizedRight.isEmpty()) return -1
    val leftTokens = normalizedLeft.naturalSortTokens()
    val rightTokens = normalizedRight.naturalSortTokens()
    val sharedLength = minOf(leftTokens.size, rightTokens.size)
    repeat(sharedLength) { index ->
        val comparison = leftTokens[index].compareTo(rightTokens[index])
        if (comparison != 0) {
            return comparison
        }
    }
    return leftTokens.size.compareTo(rightTokens.size)
}

private sealed interface NaturalSortToken : Comparable<NaturalSortToken> {
    data class Text(val value: String) : NaturalSortToken {
        override fun compareTo(other: NaturalSortToken): Int = when (other) {
            is Text -> value.compareTo(other.value, ignoreCase = true)
            is Number -> 1
        }
    }

    data class Number(val value: BigDecimal) : NaturalSortToken {
        override fun compareTo(other: NaturalSortToken): Int = when (other) {
            is Text -> -1
            is Number -> value.compareTo(other.value)
        }
    }
}

private fun String.naturalSortTokens(): List<NaturalSortToken> = naturalSortTokenRegex
    .findAll(lowercase(Locale.ROOT))
    .map { match ->
        val token = match.value
        token.toBigDecimalOrNull()?.let(NaturalSortToken::Number)
            ?: NaturalSortToken.Text(token)
    }
    .toList()

private val naturalSortTokenRegex = Regex("""\d+(?:\.\d+)?|[^\d]+""")
