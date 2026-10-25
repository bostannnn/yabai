package com.example.unifiedreader.core.model

/**
 * The year at the start of a server's date ("2021-05-04", "2021-05-04T00:00:00Z"), or null when
 * the date is missing or a placeholder: Kavita sends year 1 for a chapter with no release date.
 */
fun publishedYearOf(date: String?): Int? = date
    ?.trim()
    ?.take(4)
    ?.toIntOrNull()
    ?.takeIf(::isPlausiblePublishedYear)

/** A year a book could have come out in; servers use 0 or 1 for "unknown". */
fun isPlausiblePublishedYear(year: Int): Boolean = year in 1000..9999
