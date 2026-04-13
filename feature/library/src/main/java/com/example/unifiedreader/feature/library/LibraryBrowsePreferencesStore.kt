package com.example.unifiedreader.feature.library

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn

val defaultLibrarySortMode: LibrarySortMode = LibrarySortMode.LAST_READ

data class LibraryBrowsePreferences(
    val filtersBySource: Map<SourceType, LibraryFilterState> = emptyMap(),
    val sortsBySource: Map<SourceType, LibrarySortMode> = emptyMap(),
    val filterPanelsBySource: Map<SourceType, List<LibraryFilterPanel>> = emptyMap(),
)

interface LibraryBrowsePreferencesStore {
    val preferencesFlow: StateFlow<LibraryBrowsePreferences>

    suspend fun setFilters(
        sourceType: SourceType,
        filters: LibraryFilterState,
    )

    suspend fun setSortMode(
        sourceType: SourceType,
        sortMode: LibrarySortMode,
    )

    suspend fun setFilterPanels(
        sourceType: SourceType,
        panels: List<LibraryFilterPanel>,
    )
}

class DataStoreLibraryBrowsePreferencesStore(
    private val dataStore: DataStore<Preferences>,
    private val startupPreferences: SharedPreferences,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioRetryDelayMillis: Long = LIBRARY_BROWSE_PREFERENCES_IO_RETRY_DELAY_MILLIS,
) : LibraryBrowsePreferencesStore {
    constructor(
        context: Context,
    ) : this(
        dataStore = context.applicationContext.libraryBrowsePreferencesDataStore,
        startupPreferences = context.applicationContext.libraryBrowseStartupPreferences,
    )

    private val startupSnapshot = loadStartupLibraryBrowsePreferences(startupPreferences)
    private val latestPreferences = AtomicReference(startupSnapshot)

    override val preferencesFlow: StateFlow<LibraryBrowsePreferences> = dataStore.data
        .map(Preferences::toLibraryBrowsePreferences)
        .retryWhen { error, _ ->
            if (error !is IOException) {
                throw error
            }
            emit(loadCurrentStartupPreferences())
            delay(ioRetryDelayMillis)
            true
        }
        .onEach { preferences ->
            latestPreferences.set(preferences)
            writeStartupPreferences(preferences)
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = startupSnapshot,
        )

    override suspend fun setFilters(
        sourceType: SourceType,
        filters: LibraryFilterState,
    ) {
        dataStore.edit { preferences ->
            preferences.writeFilterState(
                sourceType = sourceType,
                filters = filters,
            )
        }
        val updatedPreferences = latestPreferences.get().withFilters(sourceType, filters)
        latestPreferences.set(updatedPreferences)
        writeStartupPreferences(updatedPreferences)
    }

    override suspend fun setSortMode(
        sourceType: SourceType,
        sortMode: LibrarySortMode,
    ) {
        dataStore.edit { preferences ->
            preferences.writeSortMode(
                sourceType = sourceType,
                sortMode = sortMode,
            )
        }
        val updatedPreferences = latestPreferences.get().withSortMode(sourceType, sortMode)
        latestPreferences.set(updatedPreferences)
        writeStartupPreferences(updatedPreferences)
    }

    override suspend fun setFilterPanels(
        sourceType: SourceType,
        panels: List<LibraryFilterPanel>,
    ) {
        dataStore.edit { preferences ->
            preferences.writeFilterPanels(
                sourceType = sourceType,
                panels = panels,
            )
        }
        val updatedPreferences = latestPreferences.get().withFilterPanels(sourceType, panels)
        latestPreferences.set(updatedPreferences)
        writeStartupPreferences(updatedPreferences)
    }

    private fun writeStartupPreferences(
        preferences: LibraryBrowsePreferences,
    ) {
        writeStartupLibraryBrowsePreferences(
            startupPreferences = startupPreferences,
            preferences = preferences,
        )
    }

    private fun loadCurrentStartupPreferences(): LibraryBrowsePreferences = loadStartupLibraryBrowsePreferences(
        startupPreferences = startupPreferences,
    )
}

private val Context.libraryBrowsePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = LIBRARY_BROWSE_PREFERENCES_DATASTORE_NAME,
)

private val Context.libraryBrowseStartupPreferences: SharedPreferences
    get() = getSharedPreferences(LIBRARY_BROWSE_STARTUP_PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun LibraryBrowsePreferences.withFilters(
    sourceType: SourceType,
    filters: LibraryFilterState,
): LibraryBrowsePreferences = copy(
    filtersBySource = filtersBySource.updatedFilters(
        sourceType = sourceType,
        filters = filters,
    ),
)

private fun LibraryBrowsePreferences.withSortMode(
    sourceType: SourceType,
    sortMode: LibrarySortMode,
): LibraryBrowsePreferences = copy(
    sortsBySource = sortsBySource.updatedSortMode(
        sourceType = sourceType,
        sortMode = sortMode,
    ),
)

private fun LibraryBrowsePreferences.withFilterPanels(
    sourceType: SourceType,
    panels: List<LibraryFilterPanel>,
): LibraryBrowsePreferences = copy(
    filterPanelsBySource = filterPanelsBySource.updatedFilterPanels(
        sourceType = sourceType,
        panels = panels,
    ),
)

internal const val LIBRARY_BROWSE_PREFERENCES_DATASTORE_NAME = "unified_reader_library_browse_preferences"
internal const val LIBRARY_BROWSE_STARTUP_PREFERENCES_NAME = "unified_reader_library_browse_startup_preferences"
internal const val LIBRARY_BROWSE_PREFERENCES_IO_RETRY_DELAY_MILLIS = 1_000L
