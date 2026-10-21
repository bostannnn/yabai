package com.example.unifiedreader.feature.reader.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderPageNumberPreferencesStoreTest {
    @Test
    fun `page number is visible until turned off`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(context.cacheDir, "reader-page-number-default-test.preferences_pb").apply { delete() }
            },
        )
        val store = DataStoreReaderPageNumberPreferencesStore(
            context = context,
            dataStore = dataStore,
        )

        assertTrue(store.isVisibleFlow.value)

        store.setVisible(false)

        assertFalse(store.isVisibleFlow.first { visible -> !visible })
    }

    @Test
    fun `saved choice is read back by a new store`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(context.cacheDir, "reader-page-number-persist-test.preferences_pb").apply { delete() }
            },
        )
        DataStoreReaderPageNumberPreferencesStore(context = context, dataStore = dataStore)
            .setVisible(false)

        val reopened = DataStoreReaderPageNumberPreferencesStore(context = context, dataStore = dataStore)

        assertFalse(reopened.isVisibleFlow.first { visible -> !visible })
    }
}
