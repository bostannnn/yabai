package com.example.unifiedreader

import android.content.Context
import android.content.SharedPreferences
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class AppActiveRouteSnapshot(
    val topLevel: TopLevelDestination,
    val selectedSource: SourceType,
    val kavitaLibrarySection: KavitaLibrarySection,
    val selectedKavitaSeriesKey: String? = null,
    val selectedStumpSeriesKey: String? = null,
    val selectedKavitaReadingListId: String? = null,
    val selectedKavitaCollectionId: String? = null,
    val readerItemId: String? = null,
    val readerFormat: FormatType? = null,
    val readerFallbackItem: ReadableItem? = null,
)

interface AppActiveRouteStore {
    fun restore(): AppActiveRouteSnapshot?

    fun save(snapshot: AppActiveRouteSnapshot)
}

@Singleton
class SharedPreferencesAppActiveRouteStore @Inject constructor(
    @param:ApplicationContext context: Context,
) : AppActiveRouteStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        APP_ACTIVE_ROUTE_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()
    private var latestSnapshot: AppActiveRouteSnapshot? = loadSnapshot()

    override fun restore(): AppActiveRouteSnapshot? = synchronized(lock) {
        latestSnapshot
    }

    override fun save(snapshot: AppActiveRouteSnapshot) {
        synchronized(lock) {
            if (latestSnapshot == snapshot) return
            if (!writeSnapshot(snapshot)) return
            latestSnapshot = snapshot
        }
    }

    private fun loadSnapshot(): AppActiveRouteSnapshot? {
        if (!preferences.getBoolean(KEY_HAS_SNAPSHOT, false)) return null
        val topLevel = preferences.enumValueOrNull<TopLevelDestination>(KEY_TOP_LEVEL) ?: return null
        val selectedSource = preferences.enumValueOrNull<SourceType>(KEY_SELECTED_SOURCE) ?: return null
        val kavitaLibrarySection = preferences.enumValueOrNull<KavitaLibrarySection>(
            KEY_KAVITA_LIBRARY_SECTION,
        ) ?: KavitaLibrarySection.Home
        val readerFormat = preferences.enumValueOrNull<FormatType>(KEY_READER_FORMAT)
        return AppActiveRouteSnapshot(
            topLevel = topLevel,
            selectedSource = selectedSource,
            kavitaLibrarySection = kavitaLibrarySection,
            selectedKavitaSeriesKey = preferences.getString(KEY_SELECTED_KAVITA_SERIES_KEY, null),
            selectedStumpSeriesKey = preferences.getString(KEY_SELECTED_STUMP_SERIES_KEY, null),
            selectedKavitaReadingListId = preferences.getString(KEY_SELECTED_KAVITA_READING_LIST_ID, null),
            selectedKavitaCollectionId = preferences.getString(KEY_SELECTED_KAVITA_COLLECTION_ID, null),
            readerItemId = preferences.getString(KEY_READER_ITEM_ID, null),
            readerFormat = readerFormat,
            readerFallbackItem = preferences.getString(KEY_READER_FALLBACK_ITEM, null).toReadableItemOrNull(),
        )
    }

    private fun writeSnapshot(
        snapshot: AppActiveRouteSnapshot,
    ): Boolean = preferences.edit()
        .putBoolean(KEY_HAS_SNAPSHOT, true)
        .putString(KEY_TOP_LEVEL, snapshot.topLevel.name)
        .putString(KEY_SELECTED_SOURCE, snapshot.selectedSource.name)
        .putString(KEY_KAVITA_LIBRARY_SECTION, snapshot.kavitaLibrarySection.name)
        .putNullableString(KEY_SELECTED_KAVITA_SERIES_KEY, snapshot.selectedKavitaSeriesKey)
        .putNullableString(KEY_SELECTED_STUMP_SERIES_KEY, snapshot.selectedStumpSeriesKey)
        .putNullableString(KEY_SELECTED_KAVITA_READING_LIST_ID, snapshot.selectedKavitaReadingListId)
        .putNullableString(KEY_SELECTED_KAVITA_COLLECTION_ID, snapshot.selectedKavitaCollectionId)
        .putNullableString(KEY_READER_ITEM_ID, snapshot.readerItemId)
        .putNullableString(KEY_READER_FORMAT, snapshot.readerFormat?.name)
        .putNullableString(KEY_READER_FALLBACK_ITEM, snapshot.readerFallbackItem?.toPersistedJson())
        .commit()
}

private inline fun <reified T : Enum<T>> SharedPreferences.enumValueOrNull(
    key: String,
): T? = getString(key, null)?.let { value ->
    runCatching { enumValueOf<T>(value) }.getOrNull()
}

private fun SharedPreferences.Editor.putNullableString(
    key: String,
    value: String?,
): SharedPreferences.Editor = if (value == null) {
    remove(key)
} else {
    putString(key, value)
}

private fun ReadableItem.toPersistedJson(): String = JSONObject()
    .put("id", id)
    .put("sourceType", sourceType.name)
    .put("libraryId", libraryId)
    .putNullable("remoteId", remoteId)
    .put("isLocalImport", isLocalImport)
    .putNullable("remoteFileId", remoteFileId)
    .putNullable("remoteFileType", remoteFileType)
    .put("title", title)
    .put("format", format.name)
    .put("kind", kind.name)
    .putNullable("pageCount", pageCount)
    .putNullable("coverUrl", coverUrl)
    .putNullable("seriesCoverUrl", seriesCoverUrl)
    .putNullable("description", description)
    .putNullable("addedAtEpochMillis", addedAtEpochMillis)
    .putNullable("issueCount", issueCount)
    .putNullable("bookMetadata", bookMetadata?.toJson())
    .putNullable("comicMetadata", comicMetadata?.toJson())
    .toString()

private fun String?.toReadableItemOrNull(): ReadableItem? = this?.let { encodedItem ->
    runCatching { JSONObject(encodedItem).toReadableItemOrNull() }.getOrNull()
}

private fun JSONObject.toReadableItemOrNull(): ReadableItem? {
    val id = optNullableString("id") ?: return null
    val sourceType = optNullableString("sourceType")?.enumValueOrNull<SourceType>() ?: return null
    val libraryId = optNullableString("libraryId") ?: return null
    val title = optNullableString("title") ?: return null
    val format = optNullableString("format")?.enumValueOrNull<FormatType>() ?: return null
    val kind = optNullableString("kind")?.enumValueOrNull<ReadableItemKind>() ?: format.defaultReadableItemKind()
    return ReadableItem(
        id = id,
        sourceType = sourceType,
        libraryId = libraryId,
        remoteId = optNullableString("remoteId"),
        isLocalImport = optBoolean("isLocalImport", false),
        remoteFileId = optNullableString("remoteFileId"),
        remoteFileType = optNullableString("remoteFileType"),
        title = title,
        format = format,
        kind = kind,
        pageCount = optNullableInt("pageCount"),
        coverUrl = optNullableString("coverUrl"),
        seriesCoverUrl = optNullableString("seriesCoverUrl"),
        description = optNullableString("description"),
        addedAtEpochMillis = optNullableLong("addedAtEpochMillis"),
        issueCount = optNullableInt("issueCount"),
        bookMetadata = optJSONObject("bookMetadata")?.toBookMetadata(),
        comicMetadata = optJSONObject("comicMetadata")?.toComicMetadata(),
    )
}

private fun BookMetadata.toJson(): JSONObject = JSONObject()
    .putNullable("author", author)
    .put("authors", authors.toJsonArray())
    .putNullable("seriesName", seriesName)
    .put("publishers", publishers.toJsonArray())
    .put("genres", genres.toJsonArray())
    .put("tags", tags.toJsonArray())
    .put("languages", languages.toJsonArray())

private fun JSONObject.toBookMetadata(): BookMetadata = BookMetadata(
    author = optNullableString("author"),
    authors = optStringList("authors"),
    seriesName = optNullableString("seriesName"),
    publishers = optStringList("publishers"),
    genres = optStringList("genres"),
    tags = optStringList("tags"),
    languages = optStringList("languages"),
)

private fun ComicMetadata.toJson(): JSONObject = JSONObject()
    .putNullable("series", series)
    .putNullable("issueTitle", issueTitle)
    .putNullable("volume", volume)
    .putNullable("issueNumber", issueNumber)
    .put("writers", writers.toJsonArray())
    .put("pencillers", pencillers.toJsonArray())
    .put("publishers", publishers.toJsonArray())
    .put("genres", genres.toJsonArray())
    .put("tags", tags.toJsonArray())
    .put("languages", languages.toJsonArray())

private fun JSONObject.toComicMetadata(): ComicMetadata = ComicMetadata(
    series = optNullableString("series"),
    issueTitle = optNullableString("issueTitle"),
    volume = optNullableString("volume"),
    issueNumber = optNullableString("issueNumber"),
    writers = optStringList("writers"),
    pencillers = optStringList("pencillers"),
    publishers = optStringList("publishers"),
    genres = optStringList("genres"),
    tags = optStringList("tags"),
    languages = optStringList("languages"),
)

private fun JSONObject.putNullable(
    key: String,
    value: Any?,
): JSONObject = if (value == null) {
    put(key, JSONObject.NULL)
} else {
    put(key, value)
}

private fun JSONObject.optNullableString(
    key: String,
): String? = takeUnless { isNull(key) }?.optString(key)?.takeIf(String::isNotBlank)

private fun JSONObject.optNullableInt(
    key: String,
): Int? = takeUnless { isNull(key) }?.optInt(key)

private fun JSONObject.optNullableLong(
    key: String,
): Long? = takeUnless { isNull(key) }?.optLong(key)

private fun JSONObject.optStringList(
    key: String,
): List<String> {
    val values = optJSONArray(key) ?: return emptyList()
    return List(values.length()) { index -> values.optString(index) }
        .filter(String::isNotBlank)
}

private fun List<String>.toJsonArray(): JSONArray = JSONArray().also { array ->
    forEach(array::put)
}

private inline fun <reified T : Enum<T>> String.enumValueOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()

private fun FormatType.defaultReadableItemKind(): ReadableItemKind = when (this) {
    FormatType.CBZ,
    FormatType.CBR -> ReadableItemKind.COMIC
    FormatType.PDF,
    FormatType.EPUB -> ReadableItemKind.BOOK
}

private const val APP_ACTIVE_ROUTE_PREFERENCES_NAME = "unified_reader_active_route"
private const val KEY_HAS_SNAPSHOT = "has_snapshot"
private const val KEY_TOP_LEVEL = "top_level"
private const val KEY_SELECTED_SOURCE = "selected_source"
private const val KEY_KAVITA_LIBRARY_SECTION = "kavita_library_section"
private const val KEY_SELECTED_KAVITA_SERIES_KEY = "selected_kavita_series_key"
private const val KEY_SELECTED_STUMP_SERIES_KEY = "selected_stump_series_key"
private const val KEY_SELECTED_KAVITA_READING_LIST_ID = "selected_kavita_reading_list_id"
private const val KEY_SELECTED_KAVITA_COLLECTION_ID = "selected_kavita_collection_id"
private const val KEY_READER_ITEM_ID = "reader_item_id"
private const val KEY_READER_FORMAT = "reader_format"
private const val KEY_READER_FALLBACK_ITEM = "reader_fallback_item"
