package com.example.unifiedreader.feature.library

import android.content.SharedPreferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import java.util.Base64
import java.util.Locale

internal fun Preferences.toLibraryBrowsePreferences(): LibraryBrowsePreferences = LibraryBrowsePreferences(
    filtersBySource = buildMap {
        SourceType.entries.forEach { sourceType ->
            val filters = decodeFilterState(sourceType)
            if (!filters.isEmpty) {
                put(sourceType, filters)
            }
        }
    },
    sortsBySource = buildMap {
        SourceType.entries.forEach { sourceType ->
            val rawValue = this@toLibraryBrowsePreferences[sortModeKey(sourceType)] ?: return@forEach
            val sortMode = rawValue.toEnumOrNull<LibrarySortMode>() ?: return@forEach
            if (sortMode != defaultLibrarySortMode) {
                put(sourceType, sortMode)
            }
        }
    },
)

internal fun MutablePreferences.writeFilterState(
    sourceType: SourceType,
    filters: LibraryFilterState,
) {
    writeEncodedValues(formatFilterKey(sourceType), filters.formats.map(FormatType::name))
    writeEncodedValues(librariesFilterKey(sourceType), filters.libraries)
    writeEncodedValues(fileLocationsFilterKey(sourceType), filters.fileLocations.map(LibraryFileLocation::name))
    writeEncodedValues(authorsFilterKey(sourceType), filters.authors)
    writeEncodedValues(seriesFilterKey(sourceType), filters.series)
}

internal fun MutablePreferences.writeSortMode(
    sourceType: SourceType,
    sortMode: LibrarySortMode,
) {
    val key = sortModeKey(sourceType)
    if (sortMode == defaultLibrarySortMode) {
        remove(key)
    } else {
        this[key] = sortMode.name
    }
}

internal fun loadStartupLibraryBrowsePreferences(
    startupPreferences: SharedPreferences,
): LibraryBrowsePreferences = LibraryBrowsePreferences(
    filtersBySource = buildMap {
        SourceType.entries.forEach { sourceType ->
            val filters = LibraryFilterState(
                formats = decodeEnumValues<FormatType>(startupPreferences.getString(formatFilterStorageKey(sourceType), null)),
                libraries = decodeStringValues(startupPreferences.getString(librariesFilterStorageKey(sourceType), null)),
                fileLocations = decodeEnumValues<LibraryFileLocation>(
                    startupPreferences.getString(fileLocationsFilterStorageKey(sourceType), null),
                ),
                authors = decodeStringValues(startupPreferences.getString(authorsFilterStorageKey(sourceType), null)),
                series = decodeStringValues(startupPreferences.getString(seriesFilterStorageKey(sourceType), null)),
            )
            if (!filters.isEmpty) {
                put(sourceType, filters)
            }
        }
    },
    sortsBySource = buildMap {
        SourceType.entries.forEach { sourceType ->
            val rawValue = startupPreferences.getString(sortModeStorageKey(sourceType), null) ?: return@forEach
            val sortMode = rawValue.toEnumOrNull<LibrarySortMode>() ?: return@forEach
            if (sortMode != defaultLibrarySortMode) {
                put(sourceType, sortMode)
            }
        }
    },
)

internal fun writeStartupLibraryBrowsePreferences(
    startupPreferences: SharedPreferences,
    preferences: LibraryBrowsePreferences,
) {
    startupPreferences.edit().apply {
        SourceType.entries.forEach { sourceType ->
            val filters = preferences.filtersBySource[sourceType] ?: LibraryFilterState()
            writeEncodedValues(formatFilterStorageKey(sourceType), filters.formats.map(FormatType::name))
            writeEncodedValues(librariesFilterStorageKey(sourceType), filters.libraries)
            writeEncodedValues(fileLocationsFilterStorageKey(sourceType), filters.fileLocations.map(LibraryFileLocation::name))
            writeEncodedValues(authorsFilterStorageKey(sourceType), filters.authors)
            writeEncodedValues(seriesFilterStorageKey(sourceType), filters.series)

            val sortMode = preferences.sortsBySource[sourceType]
            if (sortMode == null || sortMode == defaultLibrarySortMode) {
                remove(sortModeStorageKey(sourceType))
            } else {
                putString(sortModeStorageKey(sourceType), sortMode.name)
            }
        }
        commit()
    }
}

private fun Preferences.decodeFilterState(
    sourceType: SourceType,
): LibraryFilterState = LibraryFilterState(
    formats = decodeEnumValues<FormatType>(this[formatFilterKey(sourceType)]),
    libraries = decodeStringValues(this[librariesFilterKey(sourceType)]),
    fileLocations = decodeEnumValues<LibraryFileLocation>(this[fileLocationsFilterKey(sourceType)]),
    authors = decodeStringValues(this[authorsFilterKey(sourceType)]),
    series = decodeStringValues(this[seriesFilterKey(sourceType)]),
)

private fun MutablePreferences.writeEncodedValues(
    key: Preferences.Key<String>,
    values: Iterable<String>,
) {
    val encoded = encodeValues(values)
    if (encoded == null) {
        remove(key)
    } else {
        this[key] = encoded
    }
}

private fun SharedPreferences.Editor.writeEncodedValues(
    key: String,
    values: Iterable<String>,
) {
    val encoded = encodeValues(values)
    if (encoded == null) {
        remove(key)
    } else {
        putString(key, encoded)
    }
}

private fun encodeValues(
    values: Iterable<String>,
): String? {
    val normalizedValues = values
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .map(::encodeValue)
    return normalizedValues.takeIf(List<String>::isNotEmpty)?.joinToString(VALUES_DELIMITER)
}

private fun decodeStringValues(
    value: String?,
): Set<String> = decodeValues(value)
    .map(::decodeValue)
    .filter(String::isNotBlank)
    .toSet()

private inline fun <reified T : Enum<T>> decodeEnumValues(
    value: String?,
): Set<T> = decodeValues(value)
    .mapNotNull { encodedValue ->
        decodeValue(encodedValue).toEnumOrNull<T>()
    }
    .toSet()

private fun decodeValues(
    value: String?,
): List<String> = value
    ?.split(VALUES_DELIMITER)
    ?.filter(String::isNotBlank)
    .orEmpty()

private fun encodeValue(
    value: String,
): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodeValue(
    value: String,
): String = runCatching {
    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}.getOrDefault("")

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? = runCatching {
    enumValueOf<T>(this)
}.getOrNull()

private fun sortModeKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(sortModeStorageKey(sourceType))

private fun formatFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(formatFilterStorageKey(sourceType))

private fun librariesFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(librariesFilterStorageKey(sourceType))

private fun fileLocationsFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(fileLocationsFilterStorageKey(sourceType))

private fun authorsFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(authorsFilterStorageKey(sourceType))

private fun seriesFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(seriesFilterStorageKey(sourceType))

private fun sortModeStorageKey(
    sourceType: SourceType,
): String = "library_sort_mode_${sourceType.keySuffix()}"

private fun formatFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_formats_${sourceType.keySuffix()}"

private fun librariesFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_libraries_${sourceType.keySuffix()}"

private fun fileLocationsFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_file_locations_${sourceType.keySuffix()}"

private fun authorsFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_authors_${sourceType.keySuffix()}"

private fun seriesFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_series_${sourceType.keySuffix()}"

private fun SourceType.keySuffix(): String = name.lowercase(Locale.ROOT)

private const val VALUES_DELIMITER = ","
