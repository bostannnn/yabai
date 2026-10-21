package com.example.unifiedreader.feature.reader.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether the readers show the page number while their menu is hidden. One value for every
 * format, set from any reader's settings; on until the reader turns it off.
 */
interface ReaderPageNumberPreferencesStore {
    val isVisibleFlow: StateFlow<Boolean>

    /** Saves the choice in the background; [isVisibleFlow] follows once it is written. */
    fun setVisible(visible: Boolean)
}

class DataStoreReaderPageNumberPreferencesStore(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.readerPageNumberPreferencesDataStore,
    private val ioRetryDelayMillis: Long = DATASTORE_IO_RETRY_DELAY_MILLIS,
) : ReaderPageNumberPreferencesStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val isVisibleFlow: StateFlow<Boolean> = dataStore.data
        .retryWhen { error, _ ->
            if (error is IOException) {
                emit(emptyPreferences())
                delay(ioRetryDelayMillis)
            }
            error is IOException
        }
        .map { preferences -> preferences[PageNumberVisibleKey] ?: true }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    override fun setVisible(visible: Boolean) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[PageNumberVisibleKey] = visible
            }
        }
    }
}

/** For previews and tests that do not persist the choice. */
class InMemoryReaderPageNumberPreferencesStore(
    visible: Boolean = true,
) : ReaderPageNumberPreferencesStore {
    private val visibleState = MutableStateFlow(visible)
    override val isVisibleFlow: StateFlow<Boolean> = visibleState.asStateFlow()

    override fun setVisible(visible: Boolean) {
        visibleState.value = visible
    }
}

private val Context.readerPageNumberPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = READER_PAGE_NUMBER_DATASTORE_NAME,
)

private val PageNumberVisibleKey = booleanPreferencesKey("page_number_visible")

internal const val READER_PAGE_NUMBER_DATASTORE_NAME = "reader_page_number_preferences"
