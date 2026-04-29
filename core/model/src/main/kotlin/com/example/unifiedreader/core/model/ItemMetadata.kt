package com.example.unifiedreader.core.model

import java.util.Locale

data class BookMetadata(
    val author: String? = null,
    val authors: List<String> = emptyList(),
    val seriesName: String? = null,
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

data class ComicMetadata(
    val series: String? = null,
    val issueTitle: String? = null,
    val volume: String? = null,
    val issueNumber: String? = null,
    val writers: List<String> = emptyList(),
    val pencillers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

data class ReadableItemFacetMetadata(
    val authors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val pencillers: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

fun BookMetadata.primaryAuthor(): String? = normalizedMetadataValues(
    listOf(author) + authors,
).firstOrNull()

fun BookMetadata.normalizedAuthors(): List<String> = normalizedMetadataValues(
    listOf(author) + authors,
)

fun ReadableItem.facetMetadata(): ReadableItemFacetMetadata = ReadableItemFacetMetadata(
    authors = bookMetadata?.normalizedAuthors().orEmpty(),
    writers = normalizedMetadataValues(comicMetadata?.writers.orEmpty()),
    pencillers = normalizedMetadataValues(comicMetadata?.pencillers.orEmpty()),
    series = normalizedMetadataValues(
        listOfNotNull(
            comicMetadata?.series,
            bookMetadata?.seriesName,
        ),
    ),
    publishers = normalizedMetadataValues(
        bookMetadata?.publishers.orEmpty() + comicMetadata?.publishers.orEmpty(),
    ),
    genres = normalizedMetadataValues(
        bookMetadata?.genres.orEmpty() + comicMetadata?.genres.orEmpty(),
    ),
    tags = normalizedMetadataValues(
        bookMetadata?.tags.orEmpty() + comicMetadata?.tags.orEmpty(),
    ),
    languages = normalizedMetadataValues(
        bookMetadata?.languages.orEmpty() + comicMetadata?.languages.orEmpty(),
    ),
)

fun Iterable<ReadableItem>.aggregateFacetMetadata(): ReadableItemFacetMetadata = ReadableItemFacetMetadata(
    authors = normalizedMetadataValues(flatMap { item -> item.facetMetadata().authors }),
    writers = normalizedMetadataValues(flatMap { item -> item.facetMetadata().writers }),
    pencillers = normalizedMetadataValues(flatMap { item -> item.facetMetadata().pencillers }),
    series = normalizedMetadataValues(flatMap { item -> item.facetMetadata().series }),
    publishers = normalizedMetadataValues(flatMap { item -> item.facetMetadata().publishers }),
    genres = normalizedMetadataValues(flatMap { item -> item.facetMetadata().genres }),
    tags = normalizedMetadataValues(flatMap { item -> item.facetMetadata().tags }),
    languages = normalizedMetadataValues(flatMap { item -> item.facetMetadata().languages }),
)

internal fun normalizedMetadataValues(
    values: Iterable<String?>,
): List<String> = values
    .mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
    .distinctBy { value -> value.lowercase(Locale.ROOT) }
