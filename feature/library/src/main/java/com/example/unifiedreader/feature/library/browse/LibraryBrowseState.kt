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
 * Which rows a source's Browse shows. The rows Browse starts with stay on until you hide them, and
 * the others stay off until you show them, so a row is remembered by what you changed: a field
 * that turns up later (moods, say) appears by itself.
 */
@Immutable
data class LibraryBrowseRowSettings(
    val hidden: Set<LibraryBrowseRow> = emptySet(),
    val added: Set<LibraryBrowseRow> = emptySet(),
) {
    val shown: List<LibraryBrowseRow>
        get() = LibraryBrowseRow.entries.filter { row -> row in added || (row in DefaultLibraryBrowseRows && row !in hidden) }

    fun with(row: LibraryBrowseRow, show: Boolean): LibraryBrowseRowSettings = if (row in DefaultLibraryBrowseRows) {
        copy(hidden = if (show) hidden - row else hidden + row)
    } else {
        copy(added = if (show) added + row else added - row)
    }
}

/** A source's row settings, read off the main thread and written back when they change. */
internal class LibraryBrowseRowStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    suspend fun read(source: SourceType): LibraryBrowseRowSettings = withContext(Dispatchers.IO) {
        LibraryBrowseRowSettings(
            hidden = decode(preferences.getString(hiddenKey(source), null)),
            added = decode(preferences.getString(addedKey(source), null)),
        )
    }

    fun write(source: SourceType, settings: LibraryBrowseRowSettings) {
        preferences.edit()
            .putString(hiddenKey(source), settings.hidden.joinToString(",") { it.name })
            .putString(addedKey(source), settings.added.joinToString(",") { it.name })
            .apply()
    }

    private fun decode(value: String?): Set<LibraryBrowseRow> =
        value.orEmpty().split(',').mapNotNullTo(mutableSetOf()) { name -> LibraryBrowseRow.entries.firstOrNull { it.name == name } }

    private fun hiddenKey(source: SourceType) = "library_browse_hidden_rows_${source.name.lowercase()}"

    private fun addedKey(source: SourceType) = "library_browse_added_rows_${source.name.lowercase()}"

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

    suspend fun load() {
        val saved = store.read(source)
        if (!changedBeforeLoad) settings = saved
    }

    fun set(row: LibraryBrowseRow, show: Boolean) {
        changedBeforeLoad = true
        settings = settings.with(row, show)
        store.write(source, settings)
    }
}
