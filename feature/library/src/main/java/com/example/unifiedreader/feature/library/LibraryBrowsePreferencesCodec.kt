package com.example.unifiedreader.feature.library

import android.content.SharedPreferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.defaultVisibleLibraryFilterPanels
import com.example.unifiedreader.core.model.resolveLibraryFilterPanels
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
    filterPanelsBySource = buildMap {
        SourceType.entries.forEach { sourceType ->
            val panels = decodePanels(
                sourceType = sourceType,
                value = this@toLibraryBrowsePreferences[filterPanelsKey(sourceType)],
            )
            if (panels != null) {
                put(sourceType, panels)
            }
        }
    },
    gridCellSizeDp = sanitizeLibraryGridCellSizeDp(
        this[gridCellSizeKey()] ?: defaultLibraryGridCellSizeDp,
    ),
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
    writeEncodedValues(publishersFilterKey(sourceType), filters.publishers)
    writeEncodedValues(genresFilterKey(sourceType), filters.genres)
    writeEncodedValues(tagsFilterKey(sourceType), filters.tags)
    writeEncodedValues(languagesFilterKey(sourceType), filters.languages)
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

internal fun MutablePreferences.writeFilterPanels(
    sourceType: SourceType,
    panels: List<LibraryFilterPanel>,
) {
    val key = filterPanelsKey(sourceType)
    val encoded = encodeOrderedValues(
        values = panels.map(LibraryFilterPanel::name),
        defaultValues = defaultVisibleLibraryFilterPanels(sourceType).map(LibraryFilterPanel::name),
    )
    if (encoded == null) {
        remove(key)
    } else {
        this[key] = encoded
    }
}

internal fun MutablePreferences.writeGridCellSizeDp(
    gridCellSizeDp: Int,
) {
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
    if (sanitizedGridCellSizeDp == defaultLibraryGridCellSizeDp) {
        remove(gridCellSizeKey())
    } else {
        this[gridCellSizeKey()] = sanitizedGridCellSizeDp
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
                publishers = decodeStringValues(startupPreferences.getString(publishersFilterStorageKey(sourceType), null)),
                genres = decodeStringValues(startupPreferences.getString(genresFilterStorageKey(sourceType), null)),
                tags = decodeStringValues(startupPreferences.getString(tagsFilterStorageKey(sourceType), null)),
                languages = decodeStringValues(startupPreferences.getString(languagesFilterStorageKey(sourceType), null)),
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
    filterPanelsBySource = buildMap {
        SourceType.entries.forEach { sourceType ->
            val panels = decodePanels(
                sourceType = sourceType,
                value = startupPreferences.getString(filterPanelsStorageKey(sourceType), null),
            )
            if (panels != null) {
                put(sourceType, panels)
            }
        }
    },
    gridCellSizeDp = sanitizeLibraryGridCellSizeDp(
        startupPreferences.getInt(
            gridCellSizeStorageKey(),
            defaultLibraryGridCellSizeDp,
        ),
    ),
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
            writeEncodedValues(publishersFilterStorageKey(sourceType), filters.publishers)
            writeEncodedValues(genresFilterStorageKey(sourceType), filters.genres)
            writeEncodedValues(tagsFilterStorageKey(sourceType), filters.tags)
            writeEncodedValues(languagesFilterStorageKey(sourceType), filters.languages)

            val sortMode = preferences.sortsBySource[sourceType]
            if (sortMode == null || sortMode == defaultLibrarySortMode) {
                remove(sortModeStorageKey(sourceType))
            } else {
                putString(sortModeStorageKey(sourceType), sortMode.name)
            }

            writeOrderedValues(
                key = filterPanelsStorageKey(sourceType),
                values = preferences.filterPanelsBySource[sourceType]?.map(LibraryFilterPanel::name).orEmpty(),
                defaultValues = defaultVisibleLibraryFilterPanels(sourceType).map(LibraryFilterPanel::name),
            )
        }
        val gridCellSizeDp = sanitizeLibraryGridCellSizeDp(preferences.gridCellSizeDp)
        if (gridCellSizeDp == defaultLibraryGridCellSizeDp) {
            remove(gridCellSizeStorageKey())
        } else {
            putInt(gridCellSizeStorageKey(), gridCellSizeDp)
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
    publishers = decodeStringValues(this[publishersFilterKey(sourceType)]),
    genres = decodeStringValues(this[genresFilterKey(sourceType)]),
    tags = decodeStringValues(this[tagsFilterKey(sourceType)]),
    languages = decodeStringValues(this[languagesFilterKey(sourceType)]),
)

private fun decodePanels(
    sourceType: SourceType,
    value: String?,
): List<LibraryFilterPanel>? {
    val decodedNames = decodeOrderedValues(value)
    if (decodedNames.isEmpty()) return null
    val panels = resolveLibraryFilterPanels(
        sourceType = sourceType,
        preferredPanels = decodedNames.mapNotNull(String::toLibraryFilterPanelOrNull),
    )
    return panels.takeIf { it != defaultVisibleLibraryFilterPanels(sourceType) }
}

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

private fun SharedPreferences.Editor.writeOrderedValues(
    key: String,
    values: Iterable<String>,
    defaultValues: List<String>,
) {
    val encoded = encodeOrderedValues(values, defaultValues)
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

private fun encodeOrderedValues(
    values: Iterable<String>,
    defaultValues: List<String>,
): String? {
    val normalizedValues = values
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    if (normalizedValues.isEmpty() || normalizedValues == defaultValues) {
        return null
    }
    return normalizedValues.joinToString(VALUES_DELIMITER, transform = ::encodeValue)
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

private fun decodeOrderedValues(
    value: String?,
): List<String> = decodeValues(value)
    .map(::decodeValue)
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

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

private fun publishersFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(publishersFilterStorageKey(sourceType))

private fun genresFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(genresFilterStorageKey(sourceType))

private fun tagsFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(tagsFilterStorageKey(sourceType))

private fun languagesFilterKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(languagesFilterStorageKey(sourceType))

private fun filterPanelsKey(
    sourceType: SourceType,
): Preferences.Key<String> = stringPreferencesKey(filterPanelsStorageKey(sourceType))

private fun gridCellSizeKey(): Preferences.Key<Int> = intPreferencesKey(gridCellSizeStorageKey())

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

private fun publishersFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_publishers_${sourceType.keySuffix()}"

private fun genresFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_genres_${sourceType.keySuffix()}"

private fun tagsFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_tags_${sourceType.keySuffix()}"

private fun languagesFilterStorageKey(
    sourceType: SourceType,
): String = "library_filter_languages_${sourceType.keySuffix()}"

private fun filterPanelsStorageKey(
    sourceType: SourceType,
): String = "library_filter_panels_${sourceType.keySuffix()}"

private fun gridCellSizeStorageKey(): String = "library_grid_cell_size_dp"

private fun SourceType.keySuffix(): String = name.lowercase(Locale.ROOT)

private fun String.toLibraryFilterPanelOrNull(): LibraryFilterPanel? = runCatching {
    enumValueOf<LibraryFilterPanel>(this)
}.getOrNull()

private const val VALUES_DELIMITER = ","
