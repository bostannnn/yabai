package com.example.unifiedreader.feature.navigation

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceNavigationPreferencesStoreTest {
    @Test
    fun `data store persists source order for startup cache reads`() = runBlocking {
        val fixture = createTestStoreFixture()

        fixture.store.setSourceOrder(
            listOf(
                SourceType.LOCAL,
                SourceType.KAVITA,
                SourceType.STUMP,
                SourceType.GRIMMORY,
            ),
        )

        assertEquals(
            listOf(
                SourceType.LOCAL,
                SourceType.KAVITA,
                SourceType.STUMP,
                SourceType.GRIMMORY,
            ),
            fixture.store.preferencesFlow.first {
                it.sourceOrder.first() == SourceType.LOCAL
            }.sourceOrder,
        )
        assertEquals(
            listOf(
                SourceType.LOCAL,
                SourceType.KAVITA,
                SourceType.STUMP,
                SourceType.GRIMMORY,
            ),
            SourceNavigationPreferencesStoreTestAccess.loadStartupPreferences(
                startupPreferences = fixture.startupPreferences,
            ).sourceOrder,
        )
    }

    @Test
    fun `startup cache normalizes missing sources`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val suffix = System.nanoTime().toString()
        val startupPreferences = context.getSharedPreferences(
            "${SourceNavigationPreferencesStoreTestAccess.startupSharedPreferencesName}_test_$suffix",
            Context.MODE_PRIVATE,
        )
        startupPreferences.edit()
            .putString(SourceNavigationPreferencesStoreTestAccess.sourceOrderKey, "LOCAL,KAVITA")
            .commit()

        assertEquals(
            listOf(
                SourceType.LOCAL,
                SourceType.KAVITA,
                SourceType.STUMP,
                SourceType.GRIMMORY,
            ),
            SourceNavigationPreferencesStoreTestAccess.loadStartupPreferences(
                startupPreferences = startupPreferences,
            ).sourceOrder,
        )
    }

    @Test
    fun `io fallback retries and resumes preferences observation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val suffix = System.nanoTime().toString()
        val startupPreferences = context.getSharedPreferences(
            "${SourceNavigationPreferencesStoreTestAccess.startupSharedPreferencesName}_test_$suffix",
            Context.MODE_PRIVATE,
        )
        startupPreferences.edit()
            .putString(SourceNavigationPreferencesStoreTestAccess.sourceOrderKey, "LOCAL,KAVITA,STUMP,GRIMMORY")
            .commit()
        val failingDataStore = RecoveringPreferencesDataStore(initialSourceOrder = "KAVITA,STUMP,GRIMMORY,LOCAL")
        val store = DataStoreSourceNavigationPreferencesStore(
            dataStore = failingDataStore,
            startupPreferences = startupPreferences,
            ioRetryDelayMillis = 0L,
        )

        assertEquals(SourceType.LOCAL, store.preferencesFlow.value.startupSource)
        failingDataStore.awaitCollection()
        startupPreferences.edit()
            .putString(SourceNavigationPreferencesStoreTestAccess.sourceOrderKey, "GRIMMORY,KAVITA,STUMP,LOCAL")
            .commit()
        failingDataStore.fail()
        assertEquals(
            SourceType.GRIMMORY,
            store.preferencesFlow.first { it.startupSource == SourceType.GRIMMORY }.startupSource,
        )
        failingDataStore.setSourceOrder("LOCAL,KAVITA,STUMP,GRIMMORY")
        assertEquals(
            SourceType.LOCAL,
            store.preferencesFlow.first { it.startupSource == SourceType.LOCAL }.startupSource,
        )
    }
}

private data class TestStoreFixture(
    val store: DataStoreSourceNavigationPreferencesStore,
    val startupPreferences: SharedPreferences,
)

private fun createTestStoreFixture(): TestStoreFixture {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val suffix = System.nanoTime().toString()
    val startupPreferencesName = "${SourceNavigationPreferencesStoreTestAccess.startupSharedPreferencesName}_test_$suffix"
    val startupPreferences = context.getSharedPreferences(startupPreferencesName, Context.MODE_PRIVATE)
    val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = {
            File(context.filesDir, "datastore/source-navigation-preferences-test-$suffix.preferences_pb").apply {
                parentFile?.mkdirs()
            }
        },
    )

    return TestStoreFixture(
        store = DataStoreSourceNavigationPreferencesStore(
            dataStore = dataStore,
            startupPreferences = startupPreferences,
        ),
        startupPreferences = startupPreferences,
    )
}

private class RecoveringPreferencesDataStore(
    initialSourceOrder: String? = null,
) : DataStore<Preferences> {
    private val collectionStarted = CompletableDeferred<Unit>()
    private val failRead = CompletableDeferred<Unit>()
    // Holds the retried read until the test sets the next value, so the startup fallback the
    // store emits on failure isn't replaced before the test observes it.
    private val resumeRead = CompletableDeferred<Unit>()
    private val shouldFail = AtomicBoolean(true)
    private val preferencesState = MutableStateFlow<Preferences>(
        initialSourceOrder?.let { sourceOrder ->
            mutablePreferencesOf(
                stringPreferencesKey(SourceNavigationPreferencesStoreTestAccess.sourceOrderKey) to sourceOrder,
            )
        } ?: emptyPreferences(),
    )

    override val data: Flow<Preferences> = flow {
        collectionStarted.complete(Unit)
        if (shouldFail.compareAndSet(true, false)) {
            failRead.await()
            throw IOException("Simulated read failure")
        }
        resumeRead.await()
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

    fun setSourceOrder(sourceOrder: String) {
        preferencesState.value = mutablePreferencesOf(
            stringPreferencesKey(SourceNavigationPreferencesStoreTestAccess.sourceOrderKey) to sourceOrder,
        )
        resumeRead.complete(Unit)
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(preferencesState.value).also { updatedPreferences ->
        preferencesState.value = updatedPreferences
    }
}
