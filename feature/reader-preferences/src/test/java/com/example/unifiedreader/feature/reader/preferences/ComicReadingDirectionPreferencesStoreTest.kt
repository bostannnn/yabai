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
class ComicReadingDirectionPreferencesStoreTest {
    @Test
    fun `store reports hydrated after first DataStore value`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val data = MutableSharedFlow<Preferences>(replay = 1)
        val store = DataStoreComicReadingDirectionPreferencesStore(
            context = context,
            dataStore = FlowBackedDirectionPreferencesDataStore(data),
        )

        assertFalse(store.isHydratedFlow.value)

        data.emit(emptyPreferences())

        assertTrue(store.isHydratedFlow.first { it })
    }

    @Test
    fun `store persists library and series directions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(context.cacheDir, "comic-direction-test.preferences_pb")
            },
        )
        val store = DataStoreComicReadingDirectionPreferencesStore(
            context = context,
            dataStore = dataStore,
        )

        store.setLibraryDirection(
            libraryId = "kavita:library:comics",
            direction = ComicReadingDirection.RTL,
        )
        store.setSeriesDirectionOverride(
            seriesKey = "kavita:library:comics::Akira",
            direction = ComicReadingDirection.LTR,
        )

        val preferences = store.preferencesFlow.first { current ->
            current.libraryDirectionsById.isNotEmpty() && current.seriesDirectionsByKey.isNotEmpty()
        }

        assertEquals(
            ComicReadingDirection.RTL,
            preferences.libraryDirectionsById["kavita:library:comics"],
        )
        assertEquals(
            ComicReadingDirection.LTR,
            preferences.seriesDirectionsByKey["kavita:library:comics::Akira"],
        )
    }

    @Test
    fun `store removes series override when null is saved`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(context.cacheDir, "comic-direction-remove-test.preferences_pb")
            },
        )
        val store = DataStoreComicReadingDirectionPreferencesStore(
            context = context,
            dataStore = dataStore,
        )

        store.setSeriesDirectionOverride(
            seriesKey = "stump:library:manga::Berserk",
            direction = ComicReadingDirection.RTL,
        )
        store.setSeriesDirectionOverride(
            seriesKey = "stump:library:manga::Berserk",
            direction = null,
        )

        val preferences = store.preferencesFlow.first()

        assertTrue(preferences.seriesDirectionsByKey.isEmpty())
    }
}

private class FlowBackedDirectionPreferencesDataStore(
    override val data: Flow<Preferences>,
) : DataStore<Preferences> {
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = error("Not supported in this test.")
}
