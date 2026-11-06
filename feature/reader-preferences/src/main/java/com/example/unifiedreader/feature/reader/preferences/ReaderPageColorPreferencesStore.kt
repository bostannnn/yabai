package com.example.unifiedreader.feature.reader.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The page colour last picked in any book ([name], null before the first pick), once [isLoaded]. */
data class ReaderPageColorPick(
    val name: String?,
    val isLoaded: Boolean,
)

/**
 * The page colour a book you haven't set opens on: the last one picked in any book. A book you
 * set keeps its own; that is the reader's session, not this.
 */
interface ReaderPageColorPreferencesStore {
    val pickFlow: StateFlow<ReaderPageColorPick>

    /** Saves the pick in the background; [pickFlow] follows once it is written. */
    fun setLastPicked(name: String)
}

class DataStoreReaderPageColorPreferencesStore(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.readerPageColorPreferencesDataStore,
    /** The app scope; saves run on it so they outlive the reader. */
    private val scope: CoroutineScope,
    private val ioRetryDelayMillis: Long = DATASTORE_IO_RETRY_DELAY_MILLIS,
) : ReaderPageColorPreferencesStore {

    override val pickFlow: StateFlow<ReaderPageColorPick> = dataStore.data
        .retryWhen { error, _ ->
            if (error is IOException) {
                emit(emptyPreferences())
                delay(ioRetryDelayMillis)
            }
            error is IOException
        }
        .map { preferences -> ReaderPageColorPick(name = preferences[LastPickedKey], isLoaded = true) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ReaderPageColorPick(name = null, isLoaded = false),
        )

    override fun setLastPicked(name: String) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[LastPickedKey] = name
            }
        }
    }
}

/** For previews and tests that do not persist the pick. */
class InMemoryReaderPageColorPreferencesStore(
    name: String? = null,
) : ReaderPageColorPreferencesStore {
    private val pickState = MutableStateFlow(ReaderPageColorPick(name = name, isLoaded = true))
    override val pickFlow: StateFlow<ReaderPageColorPick> = pickState.asStateFlow()

    override fun setLastPicked(name: String) {
        pickState.value = ReaderPageColorPick(name = name, isLoaded = true)
    }
}

/** What the readers' settings change about the reading page beyond one book: the page number, and the page colour new books open on. */
class ReaderDisplayPreferences(
    val pageNumbers: ReaderPageNumberPreferencesStore = InMemoryReaderPageNumberPreferencesStore(),
    val pageColor: ReaderPageColorPreferencesStore = InMemoryReaderPageColorPreferencesStore(),
)

private val Context.readerPageColorPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = READER_PAGE_COLOR_DATASTORE_NAME,
)

private val LastPickedKey = stringPreferencesKey("last_picked")

internal const val READER_PAGE_COLOR_DATASTORE_NAME = "reader_page_color_preferences"
