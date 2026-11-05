package com.example.unifiedreader.feature.reader.preferences

enum class ComicReadingDirection(
    val label: String,
    /** The direction in words, where there is room for them: settings, the book page, the reader sheet. */
    val wordsLabel: String,
) {
    LTR("LTR", "Left to right"),
    RTL("RTL", "Right to left"),
}
