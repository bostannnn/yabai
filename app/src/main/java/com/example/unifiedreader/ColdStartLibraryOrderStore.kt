package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the most recently rendered, ordered library grid item ids for each source so a cold
 * start can restore the same view — in the same order — before the full catalog finishes hydrating,
 * avoiding a visible reshuffle. Only ids are stored; the items themselves are re-read from the
 * catalog by id, and the saved order is imposed via a cold-start [LibraryOrderedItemScope].
 */
interface ColdStartLibraryOrderStore {
    fun save(sourceType: SourceType, orderedItemIds: List<String>)

    fun restore(sourceType: SourceType): List<String>
}

@Singleton
class SharedPreferencesColdStartLibraryOrderStore @Inject constructor(
    @param:ApplicationContext context: Context,
) : ColdStartLibraryOrderStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        COLD_START_LIBRARY_ORDER_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun save(
        sourceType: SourceType,
        orderedItemIds: List<String>,
    ) {
        val capped = orderedItemIds.asSequence()
            .filter { id -> id.isNotBlank() && ID_SEPARATOR !in id }
            .distinct()
            .take(COLD_START_LIBRARY_ORDER_LIMIT)
            .toList()
        preferences.edit()
            .putString(sourceType.name, capped.joinToString(separator = ID_SEPARATOR.toString()))
            .apply()
    }

    override fun restore(
        sourceType: SourceType,
    ): List<String> {
        val raw = preferences.getString(sourceType.name, null) ?: return emptyList()
        return raw.split(ID_SEPARATOR).filter(String::isNotBlank)
    }
}

private const val COLD_START_LIBRARY_ORDER_PREFERENCES_NAME = "cold_start_library_order"
private const val ID_SEPARATOR = '\n'
internal const val COLD_START_LIBRARY_ORDER_LIMIT = 240
