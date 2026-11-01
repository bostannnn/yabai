package com.example.unifiedreader.feature.library.browse

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.unifiedreader.core.model.DefaultLibraryBrowseRows
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.hasStarted
import com.example.unifiedreader.feature.library.isComplete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Browse is open on a source, with [picked] chips; none picked is its landing page. */
@Immutable
data class LibraryBrowseRequest(
    val picked: List<LibraryBrowseChip> = emptyList(),
) {
    fun adding(chip: LibraryBrowseChip): LibraryBrowseRequest =
        if (picked.any { it.sameAs(chip) }) this else copy(picked = picked + chip)

    fun removing(chip: LibraryBrowseChip): LibraryBrowseRequest = copy(picked = picked.filterNot { it.sameAs(chip) })

    fun toggling(chip: LibraryBrowseChip): LibraryBrowseRequest =
        if (picked.any { it.sameAs(chip) }) removing(chip) else adding(chip)
}

internal fun LibraryBrowseChip.sameAs(other: LibraryBrowseChip): Boolean =
    row == other.row && value.equals(other.value, ignoreCase = true)

/** Keeps an open Browse (and its chips) across configuration changes; null when it is closed. */
val LibraryBrowseRequestSaver: Saver<LibraryBrowseRequest?, String> = Saver(
    save = { request ->
        request?.picked?.joinToString(separator = ChipSeparator) { chip -> "${chip.row.name}$ValueSeparator${chip.value}" }
            ?.let { chips -> "$OpenMarker$chips" }
    },
    restore = { saved ->
        if (!saved.startsWith(OpenMarker)) return@Saver null
        LibraryBrowseRequest(
            picked = saved.removePrefix(OpenMarker).split(ChipSeparator).mapNotNull { encoded ->
                val row = encoded.substringBefore(ValueSeparator, missingDelimiterValue = "")
                val value = encoded.substringAfter(ValueSeparator, missingDelimiterValue = "")
                LibraryBrowseRow.entries.firstOrNull { it.name == row }
                    ?.takeIf { value.isNotBlank() }
                    ?.let { LibraryBrowseChip(it, value) }
            },
        )
    },
)

private const val OpenMarker = "open:"
private const val ChipSeparator = "\u001E"
private const val ValueSeparator = "\u001F"

/** Your reading's status of an item, from the progress the library shows under its cover. */
internal fun libraryBrowseReadingStatus(progress: ItemProgressSummary?): LibraryReadingStatus = when {
    progress == null -> LibraryReadingStatus.NOT_STARTED
    progress.isComplete -> LibraryReadingStatus.FINISHED
    progress.hasStarted -> LibraryReadingStatus.IN_PROGRESS
    else -> LibraryReadingStatus.NOT_STARTED
}

/**
 * Which rows a source's Browse shows, and in what order. The rows Browse starts with stay on until
 * you hide them, and the others stay off until you show them, so a row is remembered by what you
 * changed: a field that turns up later (moods, say) appears by itself. [order] is every row as you
 * last arranged them, empty until you move one.
 */
@Immutable
data class LibraryBrowseRowSettings(
    val hidden: Set<LibraryBrowseRow> = emptySet(),
    val added: Set<LibraryBrowseRow> = emptySet(),
    val order: List<LibraryBrowseRow> = emptyList(),
) {
    /** Every row in your order; a row you never placed keeps its spot after the row it follows by default. */
    val ordered: List<LibraryBrowseRow>
        get() = libraryBrowseRowOrder(order)

    val shown: List<LibraryBrowseRow>
        get() = ordered.filter { row -> row in added || (row in DefaultLibraryBrowseRows && row !in hidden) }

    fun with(row: LibraryBrowseRow, show: Boolean): LibraryBrowseRowSettings = if (row in DefaultLibraryBrowseRows) {
        copy(hidden = if (show) hidden - row else hidden + row)
    } else {
        copy(added = if (show) added + row else added - row)
    }

    /**
     * [arranged] in that order, in the places those rows held among all of them: the rows it leaves
     * out (off, or waiting for metadata) stay where they were.
     */
    fun arranging(arranged: List<LibraryBrowseRow>): LibraryBrowseRowSettings {
        val rows = ordered.toMutableList()
        val places = rows.indices.filter { index -> rows[index] in arranged }
        places.zip(arranged).forEach { (index, row) -> rows[index] = row }
        return copy(order = rows)
    }
}

/** [saved] with every row it lacks put back after the row it follows in Browse's own order. */
internal fun libraryBrowseRowOrder(saved: List<LibraryBrowseRow>): List<LibraryBrowseRow> {
    val rows = saved.distinct().toMutableList()
    LibraryBrowseRow.entries.forEachIndexed { index, row ->
        if (row in rows) return@forEachIndexed
        val before = LibraryBrowseRow.entries.subList(0, index).lastOrNull { it in rows }
        rows.add(before?.let { rows.indexOf(it) + 1 } ?: 0, row)
    }
    return rows
}

/** A source's row settings, read off the main thread and written back when they change. */
internal class LibraryBrowseRowStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    suspend fun read(source: SourceType): LibraryBrowseRowSettings = withContext(Dispatchers.IO) {
        LibraryBrowseRowSettings(
            hidden = decode(preferences.getString(hiddenKey(source), null)),
            added = decode(preferences.getString(addedKey(source), null)),
            order = decodeOrder(preferences.getString(orderKey(source), null)),
        )
    }

    fun write(source: SourceType, settings: LibraryBrowseRowSettings) {
        preferences.edit()
            .putString(hiddenKey(source), settings.hidden.joinToString(",") { it.name })
            .putString(addedKey(source), settings.added.joinToString(",") { it.name })
            .putString(orderKey(source), settings.order.joinToString(",") { it.name })
            .apply()
    }

    /** Whether Not now hid the Places and times panel on [source]'s Browse. */
    suspend fun readPlacesPromptDismissed(source: SourceType): Boolean = withContext(Dispatchers.IO) {
        preferences.getBoolean(placesPromptKey(source), false)
    }

    fun writePlacesPromptDismissed(source: SourceType) {
        preferences.edit().putBoolean(placesPromptKey(source), true).apply()
    }

    private fun decode(value: String?): Set<LibraryBrowseRow> = decodeOrder(value).toSet()

    private fun decodeOrder(value: String?): List<LibraryBrowseRow> =
        value.orEmpty().split(',').mapNotNull { name -> LibraryBrowseRow.entries.firstOrNull { it.name == name } }.distinct()

    private fun hiddenKey(source: SourceType) = "library_browse_hidden_rows_${source.name.lowercase()}"

    private fun addedKey(source: SourceType) = "library_browse_added_rows_${source.name.lowercase()}"

    private fun orderKey(source: SourceType) = "library_browse_row_order_${source.name.lowercase()}"

    private fun placesPromptKey(source: SourceType) = "library_browse_places_prompt_dismissed_${source.name.lowercase()}"

    private companion object {
        const val PreferencesName = "library_browse"
    }
}

/** The rows [source]'s Browse shows, and a way to change them; the defaults until the saved ones load. */
@Composable
internal fun rememberLibraryBrowseRows(source: SourceType): LibraryBrowseRowsHolder {
    val context = LocalContext.current
    val holder = remember(source) { LibraryBrowseRowsHolder(source, LibraryBrowseRowStore(context)) }
    LaunchedEffect(holder) { holder.load() }
    return holder
}

internal class LibraryBrowseRowsHolder(
    private val source: SourceType,
    private val store: LibraryBrowseRowStore,
) {
    var settings by mutableStateOf(LibraryBrowseRowSettings())
        private set
    private var changedBeforeLoad = false

    /** Whether Not now hid the Places and times panel; read with the rows, so the panel never flashes. */
    var placesPromptDismissed by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    suspend fun load() {
        val saved = store.read(source)
        val dismissed = store.readPlacesPromptDismissed(source)
        if (!changedBeforeLoad) settings = saved
        placesPromptDismissed = placesPromptDismissed || dismissed
        loaded = true
    }

    fun dismissPlacesPrompt() {
        placesPromptDismissed = true
        store.writePlacesPromptDismissed(source)
    }

    fun set(row: LibraryBrowseRow, show: Boolean) {
        changedBeforeLoad = true
        settings = settings.with(row, show)
        store.write(source, settings)
    }

    /** Puts the shown rows in [arranged]'s order (a drag in Rows). */
    fun arrange(arranged: List<LibraryBrowseRow>) {
        changedBeforeLoad = true
        settings = settings.arranging(arranged)
        store.write(source, settings)
    }
}
