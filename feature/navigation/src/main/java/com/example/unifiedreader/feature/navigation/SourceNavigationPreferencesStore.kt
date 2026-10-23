package com.example.unifiedreader.feature.navigation

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.unifiedreader.core.model.SourceType
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn

data class SourceNavigationPreferences(
    val sourceOrder: List<SourceType> = defaultSourceOrder(),
) {
    val startupSource: SourceType
        get() = sourceOrder.firstOrNull() ?: SourceType.KAVITA
}

interface SourceNavigationPreferencesStore {
    val preferencesFlow: StateFlow<SourceNavigationPreferences>

    suspend fun setSourceOrder(sourceOrder: List<SourceType>)

    /**
     * The servers that were connected when the app last knew, so the rail and the drawer list them
     * before the sessions load at launch. Null until first recorded.
     */
    fun lastConnectedSources(): Set<SourceType>? = null

    fun setLastConnectedSources(sources: Set<SourceType>) = Unit
}

class DataStoreSourceNavigationPreferencesStore(
    private val dataStore: DataStore<Preferences>,
    private val startupPreferences: SharedPreferences,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val ioRetryDelayMillis: Long = SOURCE_NAVIGATION_PREFERENCES_IO_RETRY_DELAY_MILLIS,
) : SourceNavigationPreferencesStore {
    constructor(
        context: Context,
    ) : this(
        dataStore = context.applicationContext.sourceNavigationPreferencesDataStore,
        startupPreferences = context.applicationContext.sourceNavigationStartupPreferences,
    )

    private val startupSnapshot = loadStartupSourceNavigationPreferences(startupPreferences)
    private val latestPreferences = AtomicReference(startupSnapshot)

    override val preferencesFlow: StateFlow<SourceNavigationPreferences> = dataStore.data
        .map(Preferences::toSourceNavigationPreferences)
        .retryWhen { error, _ ->
            if (error !is IOException) {
                throw error
            }
            emit(loadCurrentStartupPreferences())
            delay(ioRetryDelayMillis)
            true
        }
        .onEach { preferences ->
            latestPreferences.set(preferences)
            writeStartupPreferences(preferences)
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = startupSnapshot,
        )

    override suspend fun setSourceOrder(sourceOrder: List<SourceType>) {
        dataStore.edit { preferences ->
            preferences[Keys.SourceOrder] = serializeSourceOrder(sourceOrder)
        }
        val updatedPreferences = SourceNavigationPreferences(
            sourceOrder = normalizeSourceOrder(sourceOrder),
        )
        latestPreferences.set(updatedPreferences)
        writeStartupPreferences(updatedPreferences)
    }

    override fun lastConnectedSources(): Set<SourceType>? =
        startupPreferences.getString(KEY_CONNECTED_SOURCES, null)?.let(::parseSourceSet)

    override fun setLastConnectedSources(sources: Set<SourceType>) {
        startupPreferences.edit()
            .putString(KEY_CONNECTED_SOURCES, sources.sortedBy(SourceType::ordinal).joinToString(",") { it.name })
            .apply()
    }

    private fun writeStartupPreferences(
        preferences: SourceNavigationPreferences,
    ) {
        writeStartupSourceNavigationPreferences(
            startupPreferences = startupPreferences,
            preferences = preferences,
        )
    }

    private fun loadCurrentStartupPreferences(): SourceNavigationPreferences =
        loadStartupSourceNavigationPreferences(startupPreferences)
}

fun loadStartupSourceNavigationPreferences(
    context: Context,
): SourceNavigationPreferences = loadStartupSourceNavigationPreferences(
    startupPreferences = context.applicationContext.sourceNavigationStartupPreferences,
)

private val Context.sourceNavigationPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SOURCE_NAVIGATION_PREFERENCES_DATASTORE_NAME,
)

private val Context.sourceNavigationStartupPreferences: SharedPreferences
    get() = getSharedPreferences(SOURCE_NAVIGATION_STARTUP_PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun Preferences.toSourceNavigationPreferences(): SourceNavigationPreferences =
    SourceNavigationPreferences(
        sourceOrder = parseSourceOrder(this[Keys.SourceOrder]),
    )

internal fun loadStartupSourceNavigationPreferences(
    startupPreferences: SharedPreferences,
): SourceNavigationPreferences = SourceNavigationPreferences(
    sourceOrder = parseSourceOrder(
        startupPreferences.getString(KEY_SOURCE_ORDER, null),
    ),
)

internal fun writeStartupSourceNavigationPreferences(
    startupPreferences: SharedPreferences,
    preferences: SourceNavigationPreferences,
) {
    startupPreferences.edit()
        .putString(KEY_SOURCE_ORDER, serializeSourceOrder(preferences.sourceOrder))
        .commit()
}

internal fun normalizeSourceOrder(
    sourceOrder: Iterable<SourceType>,
): List<SourceType> {
    val normalized = linkedSetOf<SourceType>()
    sourceOrder.forEach(normalized::add)
    SourceType.entries.forEach(normalized::add)
    return normalized.toList()
}

private fun parseSourceOrder(
    storageValue: String?,
): List<SourceType> {
    if (storageValue.isNullOrBlank()) {
        return defaultSourceOrder()
    }
    return normalizeSourceOrder(
        sourceOrder = storageValue.split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull(::sourceTypeFromStorageValue),
    )
}

private fun serializeSourceOrder(
    sourceOrder: List<SourceType>,
): String = normalizeSourceOrder(sourceOrder).joinToString(",") { sourceType ->
    sourceType.name
}

private fun parseSourceSet(storageValue: String): Set<SourceType> =
    storageValue.split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNullTo(linkedSetOf(), ::sourceTypeFromStorageValue)

private fun sourceTypeFromStorageValue(
    storageValue: String,
): SourceType? = SourceType.entries.firstOrNull { sourceType ->
    sourceType.name == storageValue
}

private fun defaultSourceOrder(): List<SourceType> = SourceType.entries.toList()

private object Keys {
    val SourceOrder = stringPreferencesKey(KEY_SOURCE_ORDER)
}

internal const val SOURCE_NAVIGATION_PREFERENCES_DATASTORE_NAME = "unified_reader_source_navigation_preferences"
internal const val SOURCE_NAVIGATION_STARTUP_PREFERENCES_NAME =
    "unified_reader_source_navigation_startup_preferences"
internal const val SOURCE_NAVIGATION_PREFERENCES_IO_RETRY_DELAY_MILLIS = 1_000L
internal const val KEY_SOURCE_ORDER = "source_order"
internal const val KEY_CONNECTED_SOURCES = "connected_sources"

internal object SourceNavigationPreferencesStoreTestAccess {
    const val startupSharedPreferencesName: String = SOURCE_NAVIGATION_STARTUP_PREFERENCES_NAME
    const val sourceOrderKey: String = KEY_SOURCE_ORDER

    fun loadStartupPreferences(
        startupPreferences: SharedPreferences,
    ): SourceNavigationPreferences = loadStartupSourceNavigationPreferences(
        startupPreferences = startupPreferences,
    )
}
