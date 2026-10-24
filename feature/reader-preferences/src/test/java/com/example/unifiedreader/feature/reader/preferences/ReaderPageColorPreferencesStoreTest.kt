package com.example.unifiedreader.feature.reader.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderPageColorPreferencesStoreTest {
    @Test
    fun `nothing is picked until a book picks a colour`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(context.cacheDir, "reader-page-color-default-test.preferences_pb").apply { delete() }
            },
        )
        val store = DataStoreReaderPageColorPreferencesStore(context = context, dataStore = dataStore)

        assertNull(store.pickFlow.first { pick -> pick.isLoaded }.name)

        store.setLastPicked("Sepia")

        assertEquals("Sepia", store.pickFlow.first { pick -> pick.name != null }.name)
    }

    @Test
    fun `the last pick is read back by a new store`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(context.cacheDir, "reader-page-color-persist-test.preferences_pb").apply { delete() }
            },
        )
        DataStoreReaderPageColorPreferencesStore(context = context, dataStore = dataStore).setLastPicked("Dusk")

        val reopened = DataStoreReaderPageColorPreferencesStore(context = context, dataStore = dataStore)

        assertEquals("Dusk", reopened.pickFlow.first { pick -> pick.name != null }.name)
    }
}
