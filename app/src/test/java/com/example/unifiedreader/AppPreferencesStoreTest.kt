package com.example.unifiedreader

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.feature.settings.ThemeMode
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
class AppPreferencesStoreTest {
    @Test
    fun `data store persists theme mode for startup cache reads`() = runBlocking {
        val fixture = createTestStoreFixture()

        fixture.store.setThemeMode(ThemeMode.Dark)

        assertEquals(
            ThemeMode.Dark,
            fixture.store.preferencesFlow.first { it.themeMode == ThemeMode.Dark }.themeMode,
        )
        assertEquals(
            ThemeMode.Dark,
            AppPreferencesStoreTestAccess.loadStartupPreferences(
                startupPreferences = fixture.startupPreferences,
                legacyPreferences = fixture.legacyPreferences,
            ).themeMode,
        )
    }

    @Test
    fun `legacy shared preferences migrate into data store`() = runBlocking {
        val fixture = createTestStoreFixture(legacyThemeMode = ThemeMode.Dark)

        val migratedThemeMode = fixture.store.preferencesFlow
            .first { it.themeMode == ThemeMode.Dark }
            .themeMode

        assertEquals(ThemeMode.Dark, migratedThemeMode)
        assertEquals(
            ThemeMode.Dark,
            AppPreferencesStoreTestAccess.loadStartupPreferences(
                startupPreferences = fixture.startupPreferences,
                legacyPreferences = fixture.legacyPreferences,
            ).themeMode,
        )
    }

    @Test
    fun `io fallback retries and resumes preferences observation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val suffix = System.nanoTime().toString()
        val startupPreferences = context.getSharedPreferences(
            "${AppPreferencesStoreTestAccess.startupSharedPreferencesName}_test_$suffix",
            Context.MODE_PRIVATE,
        )
        val legacyPreferences = context.getSharedPreferences(
            "${AppPreferencesStoreTestAccess.legacySharedPreferencesName}_test_$suffix",
            Context.MODE_PRIVATE,
        )
        startupPreferences.edit()
            .putString(AppPreferencesStoreTestAccess.themeModeKey, ThemeMode.Dark.storageValue)
            .commit()
        val failingDataStore = RecoveringPreferencesDataStore(initialThemeMode = ThemeMode.Light)
        val store = DataStoreAppPreferencesStore(
            appContext = context,
            dataStore = failingDataStore,
            startupPreferences = startupPreferences,
            ioRetryDelayMillis = 0L,
        )

        assertEquals(ThemeMode.Dark, store.preferencesFlow.value.themeMode)
        failingDataStore.awaitCollection()
        startupPreferences.edit()
            .putString(AppPreferencesStoreTestAccess.themeModeKey, ThemeMode.Light.storageValue)
            .commit()
        failingDataStore.fail()
        assertEquals(
            ThemeMode.Light,
            store.preferencesFlow.first { it.themeMode == ThemeMode.Light }.themeMode,
        )
        failingDataStore.setThemeMode(ThemeMode.Dark)
        assertEquals(
            ThemeMode.Dark,
            store.preferencesFlow.first { it.themeMode == ThemeMode.Dark }.themeMode,
        )
    }
}

private data class TestStoreFixture(
    val store: DataStoreAppPreferencesStore,
    val startupPreferences: SharedPreferences,
    val legacyPreferences: SharedPreferences,
)

private fun createTestStoreFixture(
    legacyThemeMode: ThemeMode? = null,
): TestStoreFixture {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val suffix = System.nanoTime().toString()
    val legacyPreferencesName = "${AppPreferencesStoreTestAccess.legacySharedPreferencesName}_test_$suffix"
    val startupPreferencesName = "${AppPreferencesStoreTestAccess.startupSharedPreferencesName}_test_$suffix"
    val startupPreferences = context.getSharedPreferences(startupPreferencesName, Context.MODE_PRIVATE)
    val legacyPreferences = context.getSharedPreferences(legacyPreferencesName, Context.MODE_PRIVATE)
    if (legacyThemeMode != null) {
        legacyPreferences.edit()
            .putString(AppPreferencesStoreTestAccess.themeModeKey, legacyThemeMode.storageValue)
            .commit()
    }
    val dataStore = PreferenceDataStoreFactory.create(
        migrations = listOf(
            SharedPreferencesMigration(context, legacyPreferencesName),
        ),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = {
            File(context.filesDir, "datastore/app-preferences-test-$suffix.preferences_pb").apply {
                parentFile?.mkdirs()
            }
        },
    )

    return TestStoreFixture(
        store = DataStoreAppPreferencesStore(
            appContext = context,
            dataStore = dataStore,
            startupPreferences = startupPreferences,
        ),
        startupPreferences = startupPreferences,
        legacyPreferences = legacyPreferences,
    )
}

private class RecoveringPreferencesDataStore(
    initialThemeMode: ThemeMode? = null,
) : DataStore<Preferences> {
    private val collectionStarted = CompletableDeferred<Unit>()
    private val failRead = CompletableDeferred<Unit>()
    private val shouldFail = AtomicBoolean(true)
    private val preferencesState = MutableStateFlow<Preferences>(
        initialThemeMode?.let { themeMode ->
            mutablePreferencesOf(
                stringPreferencesKey(AppPreferencesStoreTestAccess.themeModeKey) to themeMode.storageValue,
            )
        } ?: emptyPreferences()
    )

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

    fun setThemeMode(themeMode: ThemeMode) {
        preferencesState.value = mutablePreferencesOf(
            stringPreferencesKey(AppPreferencesStoreTestAccess.themeModeKey) to themeMode.storageValue,
        )
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = transform(preferencesState.value).also { updatedPreferences ->
        preferencesState.value = updatedPreferences
    }
}
