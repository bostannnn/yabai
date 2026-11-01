package com.example.unifiedreader.data.kavita

import kotlinx.serialization.Serializable

/**
 * What Kavita keeps on a series itself (`/api/Series/metadata`): its summary, and the genres, tags and
 * people edited on the series or matched from a metadata provider. The series list carries none of
 * these, and a book's own carry only what its file says.
 */
data class KavitaSeriesMetadata(
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val pencillers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

/** The series with its metadata added to what it already has. */
fun KavitaSeriesEntry.withMetadata(metadata: KavitaSeriesMetadata): KavitaSeriesEntry = copy(
    genres = genres.plusDistinct(metadata.genres),
    tags = tags.plusDistinct(metadata.tags),
    writers = writers.plusDistinct(metadata.writers),
    pencillers = pencillers.plusDistinct(metadata.pencillers),
    publishers = publishers.plusDistinct(metadata.publishers),
    languages = languages.plusDistinct(metadata.languages),
)

@Serializable
internal data class KavitaSeriesMetadataDto(
    val summary: String? = null,
    val genres: List<KavitaGenreTagDto>? = null,
    val tags: List<KavitaTagDto>? = null,
    val writers: List<KavitaPersonDto>? = null,
    val pencillers: List<KavitaPersonDto>? = null,
    val publishers: List<KavitaPersonDto>? = null,
    val language: String? = null,
)

internal fun KavitaSeriesMetadataDto.toMetadata(): KavitaSeriesMetadata = KavitaSeriesMetadata(
    summary = summary?.takeIf(String::isNotBlank),
    genres = genres.toFacetNames(),
    tags = tags.toTagNames(),
    writers = writers.toPersonNames(),
    pencillers = pencillers.toPersonNames(),
    publishers = publishers.toPublisherNames(),
    languages = language.toFacetNames(),
)

internal fun List<String>.plusDistinct(other: List<String>): List<String> =
    (this + other).distinctBy(String::lowercase)
