package com.example.unifiedreader.feature.library

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryBrowsePreferencesStoreTest {
    @Test
    fun `persists per-source filters and sort modes`() = runBlocking {
        val file = File.createTempFile("library-browse-preferences", ".preferences_pb")
        val startupPreferences = testStartupPreferences()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file },
        )
        val store = DataStoreLibraryBrowsePreferencesStore(
            dataStore = dataStore,
            startupPreferences = startupPreferences,
        )
        val filters = LibraryFilterState(
            formats = setOf(FormatType.EPUB),
            libraries = setOf("kavita:library:books"),
            fileLocations = setOf(LibraryFileLocation.LOCAL),
            authors = setOf("Ursula K. Le Guin"),
            series = setOf("Earthsea"),
            publishers = setOf("Parnassus"),
            genres = setOf("Fantasy"),
            tags = setOf("Wizard"),
            languages = setOf("English"),
        )

        store.setFilters(SourceType.KAVITA, filters)
        store.setSortMode(SourceType.KAVITA, LibrarySortMode.TITLE)
        store.setGridCellSizeDp(140)

        val preferences = store.preferencesFlow.first { current ->
            current.filtersBySource[SourceType.KAVITA] == filters &&
                current.sortsBySource[SourceType.KAVITA] == LibrarySortMode.TITLE &&
                current.gridCellSizeDp == 140
        }

        assertEquals(filters, preferences.filtersBySource[SourceType.KAVITA])
        assertEquals(LibrarySortMode.TITLE, preferences.sortsBySource[SourceType.KAVITA])
        assertEquals(140, preferences.gridCellSizeDp)
    }

    @Test
    fun `default sort mode is last read and is not stored explicitly`() = runBlocking {
        val file = File.createTempFile("library-browse-default-sort", ".preferences_pb")
        val startupPreferences = testStartupPreferences()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file },
        )
        val store = DataStoreLibraryBrowsePreferencesStore(
            dataStore = dataStore,
            startupPreferences = startupPreferences,
        )

        store.setSortMode(SourceType.LOCAL, LibrarySortMode.TITLE)
        store.setSortMode(SourceType.LOCAL, defaultLibrarySortMode)

        val preferences = store.preferencesFlow.first { current ->
            SourceType.LOCAL !in current.sortsBySource
        }

        assertFalse(SourceType.LOCAL in preferences.sortsBySource)
        assertEquals(defaultLibrarySortMode, preferences.sortsBySource[SourceType.LOCAL] ?: defaultLibrarySortMode)
    }

    @Test
    fun `persists custom filter panel order per source`() = runBlocking {
        val file = File.createTempFile("library-browse-filter-panels", ".preferences_pb")
        val startupPreferences = testStartupPreferences()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file },
        )
        val store = DataStoreLibraryBrowsePreferencesStore(
            dataStore = dataStore,
            startupPreferences = startupPreferences,
        )
        val panels = listOf(
            LibraryFilterPanel.Format,
            LibraryFilterPanel.Tag,
            LibraryFilterPanel.Language,
        )

        store.setFilterPanels(
            sourceType = SourceType.KAVITA,
            panels = panels,
        )

        val preferences = store.preferencesFlow.first { current ->
            current.filterPanelsBySource[SourceType.KAVITA] == panels
        }

        assertEquals(panels, preferences.filterPanelsBySource[SourceType.KAVITA])
    }

    @Test
    fun `normalizes persisted grid cell size`() = runBlocking {
        val file = File.createTempFile("library-browse-grid-size", ".preferences_pb")
        val startupPreferences = testStartupPreferences()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file },
        )
        val store = DataStoreLibraryBrowsePreferencesStore(
            dataStore = dataStore,
            startupPreferences = startupPreferences,
        )

        store.setGridCellSizeDp(149)

        val preferences = store.preferencesFlow.first { current ->
            current.gridCellSizeDp == 140
        }

        assertEquals(140, preferences.gridCellSizeDp)
        assertEquals(140, loadStartupLibraryBrowsePreferences(startupPreferences).gridCellSizeDp)
    }

    @Test
    fun `default grid cell size is not stored explicitly`() = runBlocking {
        val file = File.createTempFile("library-browse-default-grid-size", ".preferences_pb")
        val startupPreferences = testStartupPreferences()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file },
        )
        val store = DataStoreLibraryBrowsePreferencesStore(
            dataStore = dataStore,
            startupPreferences = startupPreferences,
        )

        store.setGridCellSizeDp(140)
        store.setGridCellSizeDp(defaultLibraryGridCellSizeDp)

        val preferences = store.preferencesFlow.first { current ->
            current.gridCellSizeDp == defaultLibraryGridCellSizeDp
        }

        assertEquals(defaultLibraryGridCellSizeDp, preferences.gridCellSizeDp)
        assertFalse(startupPreferences.contains("library_grid_cell_size_dp"))
    }

    @Test
    fun `io fallback retries and resumes preferences observation`() = runBlocking {
        val dataStore = RecoveringPreferencesDataStore()
        val store = DataStoreLibraryBrowsePreferencesStore(
            dataStore = dataStore,
            startupPreferences = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences(
                "library-browse-recovering-test-${System.nanoTime()}",
                Context.MODE_PRIVATE,
            ),
            ioRetryDelayMillis = 0L,
        )

        dataStore.awaitCollection()
        dataStore.fail()
        store.setSortMode(SourceType.LOCAL, LibrarySortMode.TITLE)

        val preferences = store.preferencesFlow.first { current ->
            current.sortsBySource[SourceType.LOCAL] == LibrarySortMode.TITLE
        }

        assertEquals(LibrarySortMode.TITLE, preferences.sortsBySource[SourceType.LOCAL])
    }

    @Test
    fun `startup cache seeds initial preferences before datastore emits`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val suffix = System.nanoTime().toString()
        val startupPreferences = context.getSharedPreferences(
            "${LIBRARY_BROWSE_STARTUP_PREFERENCES_NAME}_test_$suffix",
            Context.MODE_PRIVATE,
        )
        val seededPreferences = LibraryBrowsePreferences(
            filtersBySource = mapOf(
                SourceType.LOCAL to LibraryFilterState(
                    formats = setOf(FormatType.EPUB),
                    authors = setOf("Ursula K. Le Guin"),
                ),
            ),
            sortsBySource = mapOf(
                SourceType.LOCAL to LibrarySortMode.TITLE,
            ),
            filterPanelsBySource = mapOf(
                SourceType.GRIMMORY to listOf(
                    LibraryFilterPanel.Format,
                    LibraryFilterPanel.Tag,
                ),
            ),
            gridCellSizeDp = 140,
        )
        writeStartupLibraryBrowsePreferences(
            startupPreferences = startupPreferences,
            preferences = seededPreferences,
        )
        val file = File.createTempFile("library-browse-startup", ".preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file },
        )

        val store = DataStoreLibraryBrowsePreferencesStore(
            dataStore = dataStore,
            startupPreferences = startupPreferences,
        )

        assertEquals(seededPreferences, store.preferencesFlow.value)
    }
}

private fun testStartupPreferences() = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences(
    "library-browse-test-${System.nanoTime()}",
    Context.MODE_PRIVATE,
)

private class RecoveringPreferencesDataStore : DataStore<Preferences> {
    private val collectionStarted = CompletableDeferred<Unit>()
    private val failRead = CompletableDeferred<Unit>()
    private val shouldFail = AtomicBoolean(true)
    private val preferencesState = MutableStateFlow<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = flow {
        collectionStarted.complete(Unit)
        if (shouldFail.compareAndSet(true, false)) {
            failRead.await()
            throw IOException("Simulated read failure")
        }
        emitAll(preferencesState)
    }

    suspend fun awaitCollection() {
        withTimeout(1_000) {
            collectionStarted.await()
        }
    }

    fun fail() {
        failRead.complete(Unit)
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(preferencesState.value).also { updatedPreferences ->
        preferencesState.value = updatedPreferences
    }
}
