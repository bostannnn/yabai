package com.example.unifiedreader.feature.library

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whether the filters are docked open beside the library on an Expanded window. The Filters chip
 * shows and hides them, and the app remembers which across launches. [openRequest] counts the times
 * something asked for a section, so a panel that is open already turns to it.
 */
@Stable
internal class LibraryFilterDockState(private val context: Context) {
    var open by mutableStateOf(false)
        private set
    var openRequest by mutableIntStateOf(0)
        private set
    private var changed = false

    suspend fun load() {
        val saved = withContext(Dispatchers.IO) { preferences().getBoolean(OpenKey, false) }
        if (!changed) open = saved
    }

    /** Shows the panel, turned to the section asked for (if any). */
    fun show() {
        openRequest++
        set(true)
    }

    fun hide() = set(false)

    private fun set(value: Boolean) {
        changed = true
        if (open == value) return
        open = value
        preferences().edit().putBoolean(OpenKey, value).apply()
    }

    private fun preferences() = context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private companion object {
        const val PreferencesName = "library_filters"
        const val OpenKey = "docked_open"
    }
}

@Composable
internal fun rememberLibraryFilterDockState(): LibraryFilterDockState {
    val context = LocalContext.current
    val state = remember { LibraryFilterDockState(context) }
    LaunchedEffect(state) { state.load() }
    return state
}
