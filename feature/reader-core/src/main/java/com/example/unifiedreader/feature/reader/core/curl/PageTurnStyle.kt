package com.example.unifiedreader.feature.reader.core.curl

/** How a paged reader turns pages; saved by name. */
enum class PageTurnStyle(
    val label: String,
) {
    Slide("Slide"),
    Curl("Curl"),
}
