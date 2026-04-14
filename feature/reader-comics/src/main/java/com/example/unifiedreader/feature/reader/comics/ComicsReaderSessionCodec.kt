package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.enumValueOrNull
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

const val ComicsReaderModeKey = "readerMode"
const val ComicsReadingDirectionKey = "readingDirection"
const val ComicsPageFitModeKey = "pageFitMode"
const val ComicsSpreadModeKey = "spreadMode"
const val ComicsSplitWidePagesKey = "splitWidePages"
const val ComicsResumeAnchorPageKey = "resumeAnchorPage"
const val ComicsResumeSegmentKey = "resumeSegment"
const val ComicsResumeScrollOffsetRatioKey = "resumeScrollOffsetRatio"

fun restoreComicReaderPreferences(
    session: ReaderSessionSnapshot?,
): ComicReaderPreferencesSnapshot? = restoreComicReaderPreferences(
    preferences = session?.preferences.orEmpty(),
)

fun restoreComicReaderPreferences(
    preferences: Map<String, String>,
): ComicReaderPreferencesSnapshot? {
    val hasKnownPreference = preferences.containsKey(ComicsReaderModeKey) ||
        preferences.containsKey(ComicsReadingDirectionKey) ||
        preferences.containsKey(ComicsPageFitModeKey) ||
        preferences.containsKey(ComicsSpreadModeKey) ||
        preferences.containsKey(ComicsSplitWidePagesKey) ||
        preferences.containsKey(ComicsResumeAnchorPageKey) ||
        preferences.containsKey(ComicsResumeSegmentKey) ||
        preferences.containsKey(ComicsResumeScrollOffsetRatioKey)
    if (!hasKnownPreference) return null
    return ComicReaderPreferencesSnapshot(
        readerMode = preferences[ComicsReaderModeKey]
            ?.enumValueOrNull<ComicReaderMode>()
            ?: ComicReaderMode.Paged,
        readingDirection = preferences[ComicsReadingDirectionKey]
            ?.enumValueOrNull<ComicReadingDirection>()
            ?: ComicReadingDirection.LTR,
        pageFitMode = preferences[ComicsPageFitModeKey]
            ?.enumValueOrNull<ComicPageFitMode>()
            ?: ComicPageFitMode.FitPage,
        spreadMode = preferences[ComicsSpreadModeKey]
            ?.enumValueOrNull<ComicSpreadMode>()
            ?: ComicSpreadMode.Auto,
        splitWidePages = preferences[ComicsSplitWidePagesKey]
            ?.toBooleanStrictOrNull()
            ?: false,
        resumeAnchorPage = preferences[ComicsResumeAnchorPageKey]
            ?.toIntOrNull(),
        resumeSegment = preferences[ComicsResumeSegmentKey]
            ?.enumValueOrNull<ComicPageSegment>(),
        resumeScrollOffsetRatio = preferences[ComicsResumeScrollOffsetRatioKey]
            ?.toFloatOrNull()
            ?.let(::sanitizeComicResumeScrollOffsetRatio),
    )
}

fun restoreComicReadingDirectionOverride(
    session: ReaderSessionSnapshot?,
): ComicReadingDirection? = restoreComicReadingDirectionOverride(session?.preferences.orEmpty())

fun restoreComicReadingDirectionOverride(
    preferences: Map<String, String>,
): ComicReadingDirection? = preferences[ComicsReadingDirectionKey]
    ?.takeIf(String::isNotBlank)
    ?.enumValueOrNull<ComicReadingDirection>()

fun restoreComicReaderModeOverride(
    session: ReaderSessionSnapshot?,
): ComicReaderMode? = restoreComicReaderModeOverride(session?.preferences.orEmpty())

fun restoreComicReaderModeOverride(
    preferences: Map<String, String>,
): ComicReaderMode? = preferences[ComicsReaderModeKey]
    ?.takeIf(String::isNotBlank)
    ?.enumValueOrNull<ComicReaderMode>()

fun buildComicReaderPreferencesMap(
    preferences: ComicReaderPreferencesSnapshot,
): Map<String, String> = mapOf(
    ComicsReaderModeKey to preferences.readerMode.name,
    ComicsReadingDirectionKey to preferences.readingDirection.name,
    ComicsPageFitModeKey to preferences.pageFitMode.name,
    ComicsSpreadModeKey to preferences.spreadMode.name,
    ComicsSplitWidePagesKey to preferences.splitWidePages.toString(),
    ComicsResumeAnchorPageKey to preferences.resumeAnchorPage?.toString().orEmpty(),
    ComicsResumeSegmentKey to preferences.resumeSegment?.name.orEmpty(),
    ComicsResumeScrollOffsetRatioKey to preferences.resumeScrollOffsetRatio?.toString().orEmpty(),
)

fun buildComicReaderPreferencesPatch(
    readerMode: ComicReaderMode? = null,
    readingDirection: ComicReadingDirection? = null,
    pageFitMode: ComicPageFitMode? = null,
    spreadMode: ComicSpreadMode? = null,
    splitWidePages: Boolean? = null,
    resumeAnchorPage: Int? = null,
    resumeSegment: ComicPageSegment? = null,
    resumeScrollOffsetRatio: Float? = null,
): Map<String, String> = buildMap {
    readerMode?.let { put(ComicsReaderModeKey, it.name) }
    readingDirection?.let { put(ComicsReadingDirectionKey, it.name) }
    pageFitMode?.let { put(ComicsPageFitModeKey, it.name) }
    spreadMode?.let { put(ComicsSpreadModeKey, it.name) }
    splitWidePages?.let { put(ComicsSplitWidePagesKey, it.toString()) }
    resumeAnchorPage?.let { put(ComicsResumeAnchorPageKey, it.toString()) }
    if (resumeSegment != null) {
        put(ComicsResumeSegmentKey, resumeSegment.name)
    }
    if (resumeScrollOffsetRatio != null) {
        put(ComicsResumeScrollOffsetRatioKey, resumeScrollOffsetRatio.toString())
    }
}
