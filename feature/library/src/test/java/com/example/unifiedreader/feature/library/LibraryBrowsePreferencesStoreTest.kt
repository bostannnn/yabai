package com.example.unifiedreader.feature.library

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterPanel
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryPageRange
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.SourceType
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
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
    fun `persists per-source filters and sort modes`() = storeTest {
        val store = readyStore("library-browse-preferences")
        val filters = LibraryFilterState(
            formats = setOf(FormatType.EPUB),
            libraries = setOf("kavita:library:books"),
            fileLocations = setOf(LibraryFileLocation.LOCAL),
            pages = LibraryPageRange(min = 150, max = 300, includeUnknown = true),
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
    fun `default sort mode is last read and is not stored explicitly`() = storeTest {
        val store = readyStore("library-browse-default-sort")

        store.setSortMode(SourceType.LOCAL, LibrarySortMode.TITLE)
        // The flow starts without a LOCAL sort, so wait for TITLE first or the check below proves nothing.
        store.preferencesFlow.first { current -> current.sortsBySource[SourceType.LOCAL] == LibrarySortMode.TITLE }
        store.setSortMode(SourceType.LOCAL, defaultLibrarySortMode)

        val preferences = store.preferencesFlow.first { current ->
            SourceType.LOCAL !in current.sortsBySource
        }

        assertFalse(SourceType.LOCAL in preferences.sortsBySource)
        assertEquals(defaultLibrarySortMode, preferences.sortsBySource[SourceType.LOCAL] ?: defaultLibrarySortMode)
    }

    @Test
    fun `persists custom filter panel order per source`() = storeTest {
        val store = readyStore("library-browse-filter-panels")
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
    fun `normalizes persisted grid cell size`() = storeTest {
        val startupPreferences = testStartupPreferences()
        val store = readyStore("library-browse-grid-size", startupPreferences)

        store.setGridCellSizeDp(149)

        val preferences = store.preferencesFlow.first { current ->
            current.gridCellSizeDp == 140
        }

        assertEquals(140, preferences.gridCellSizeDp)
        assertEquals(140, loadStartupLibraryBrowsePreferences(startupPreferences).gridCellSizeDp)
    }

    @Test
    fun `default grid cell size is not stored explicitly`() = storeTest {
        val startupPreferences = testStartupPreferences()
        val store = readyStore("library-browse-default-grid-size", startupPreferences)

        // Wait for each value to come back through the store's flow: it mirrors every DataStore
        // emission into the startup preferences, so an unobserved 140 could land there after the
        // default was written. (The flow starts at the default, so waiting for it alone returns at once.)
        store.setGridCellSizeDp(140)
        store.preferencesFlow.first { current -> current.gridCellSizeDp == 140 }
        store.setGridCellSizeDp(defaultLibraryGridCellSizeDp)

        val preferences = store.preferencesFlow.first { current ->
            current.gridCellSizeDp == defaultLibraryGridCellSizeDp
        }

        assertEquals(defaultLibraryGridCellSizeDp, preferences.gridCellSizeDp)
        assertFalse(startupPreferences.contains("library_grid_cell_size_dp"))
    }

    @Test
    fun `persists fade read items preference`() = storeTest {
        val startupPreferences = testStartupPreferences()
        val store = readyStore("library-browse-fade-read", startupPreferences)

        store.setFadeReadItems(true)

        val preferences = store.preferencesFlow.first { current ->
            current.fadeReadItems
        }

        assertEquals(true, preferences.fadeReadItems)
        assertEquals(true, loadStartupLibraryBrowsePreferences(startupPreferences).fadeReadItems)
    }

    @Test
    fun `io fallback retries and resumes preferences observation`() = storeTest {
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
    fun `startup cache seeds initial preferences before datastore emits`() = storeTest {
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
                    pages = LibraryPageRange(max = 50),
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
        // A DataStore that has not emitted yet: the store's eager collector would otherwise replace
        // the seeded value with the empty file's defaults before the assertion on a slow machine.
        val store = DataStoreLibraryBrowsePreferencesStore(
            dataStore = SilentPreferencesDataStore(),
            startupPreferences = startupPreferences,
        )

        assertEquals(seededPreferences, store.preferencesFlow.value)
    }
}

private fun testStartupPreferences() = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences(
    "library-browse-test-${System.nanoTime()}",
    Context.MODE_PRIVATE,
)

/** runBlocking with a deadline: a value that never reaches the store's flow fails the test instead of stalling the build. */
private fun storeTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
    withTimeout(StoreTestTimeoutMillis) { block() }
}

private const val StoreTestTimeoutMillis = 30_000L

/**
 * A store over a fresh preferences file, returned once its collector has read the file. DataStore 1.1.1 can drop an
 * edit made while a collector's first read is still running (2 runs in 300 under load, and the test then waited
 * forever); in the app the store has long finished that read before anything is edited.
 */
private suspend fun readyStore(
    name: String,
    startupPreferences: SharedPreferences = testStartupPreferences(),
): DataStoreLibraryBrowsePreferencesStore {
    val dataStore = FirstReadSignallingDataStore(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File.createTempFile(name, ".preferences_pb") },
        ),
    )
    val store = DataStoreLibraryBrowsePreferencesStore(
        dataStore = dataStore,
        startupPreferences = startupPreferences,
    )
    dataStore.firstRead.await()
    return store
}

/** Passes everything to [delegate], and completes [firstRead] once its data has emitted. */
private class FirstReadSignallingDataStore(
    private val delegate: DataStore<Preferences>,
) : DataStore<Preferences> {
    val firstRead = CompletableDeferred<Unit>()

    override val data: Flow<Preferences> = delegate.data.onEach { firstRead.complete(Unit) }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = delegate.updateData(transform)
}

/** A DataStore whose data never emits, for what the store shows before it has read anything. */
private class SilentPreferencesDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { awaitCancellation() }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(emptyPreferences())
}

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
