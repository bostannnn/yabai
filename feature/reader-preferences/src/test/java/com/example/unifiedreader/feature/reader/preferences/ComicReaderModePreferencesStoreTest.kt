package com.example.unifiedreader.feature.reader.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ComicReaderModePreferencesStoreTest {
    @Test
    fun `store reports hydrated after first DataStore value`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val data = MutableSharedFlow<Preferences>(replay = 1)
        val store = DataStoreComicReaderModePreferencesStore(
            context = context,
            dataStore = FlowBackedReaderModePreferencesDataStore(data),
        )

        assertFalse(store.isHydratedFlow.value)

        data.emit(emptyPreferences())

        assertTrue(store.isHydratedFlow.first { it })
    }

    @Test
    fun `store persists library and series modes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(context.cacheDir, "comic-reader-mode-test.preferences_pb")
            },
        )
        val store = DataStoreComicReaderModePreferencesStore(
            context = context,
            dataStore = dataStore,
        )

        store.setLibraryMode(
            libraryId = "kavita:library:webtoons",
            mode = ComicReaderMode.VerticalScroll,
        )
        store.setSeriesModeOverride(
            seriesKey = "kavita:library:webtoons::Tower of God",
            mode = ComicReaderMode.Paged,
        )

        val preferences = store.preferencesFlow.first { current ->
            current.libraryModesById.isNotEmpty() && current.seriesModesByKey.isNotEmpty()
        }

        assertEquals(
            ComicReaderMode.VerticalScroll,
            preferences.libraryModesById["kavita:library:webtoons"],
        )
        assertEquals(
            ComicReaderMode.Paged,
            preferences.seriesModesByKey["kavita:library:webtoons::Tower of God"],
        )
    }

    @Test
    fun `store removes series override when null is saved`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(context.cacheDir, "comic-reader-mode-remove-test.preferences_pb")
            },
        )
        val store = DataStoreComicReaderModePreferencesStore(
            context = context,
            dataStore = dataStore,
        )

        store.setSeriesModeOverride(
            seriesKey = "stump:library:webtoons::Solo Leveling",
            mode = ComicReaderMode.VerticalScroll,
        )
        store.setSeriesModeOverride(
            seriesKey = "stump:library:webtoons::Solo Leveling",
            mode = null,
        )

        val preferences = store.preferencesFlow.first()

        assertTrue(preferences.seriesModesByKey.isEmpty())
    }
}

private class FlowBackedReaderModePreferencesDataStore(
    override val data: Flow<Preferences>,
) : DataStore<Preferences> {
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = error("Not supported in this test.")
}
