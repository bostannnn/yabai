package com.example.unifiedreader.feature.translation

import android.content.Context
import com.example.unifiedreader.core.model.runSuspendCatching
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** What the settings page and the reader show about translation. */
data class TranslationState(
    /** False until the first look at what is on the tablet has finished. */
    val loaded: Boolean = false,
    val supported: Set<String> = emptySet(),
    val downloaded: Set<String> = emptySet(),
    val downloading: Set<String> = emptySet(),
    /** Languages whose last download failed, until they are tried again. */
    val failed: Set<String> = emptySet(),
    val preferences: TranslationPreferences = TranslationPreferences(),
    val deviceLanguage: String? = null,
) {
    /** "Translate into" as it applies now: your pick, else the device's language, else English. */
    val yourLanguage: String
        get() = preferences.targetLanguage ?: deviceLanguage ?: TranslationLanguages.BUILT_IN
}

/**
 * The app's one translation owner: the engine, the languages on the tablet and your language
 * choices. Construction only; nothing is read until [start], which the reader and the settings
 * page call when they first show translation.
 */
class TranslationService internal constructor(
    private val engine: TranslationEngine,
    private val preferencesStore: TranslationPreferencesStore,
    private val scope: CoroutineScope,
    private val deviceLocale: () -> Locale = { Locale.getDefault() },
) {
    private val mutableState = MutableStateFlow(TranslationState())
    val state: StateFlow<TranslationState> = mutableState.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        val supported = engine.supportedLanguages
        mutableState.update { state ->
            state.copy(
                supported = supported,
                deviceLanguage = TranslationLanguages.normalize(deviceLocale().toLanguageTag(), supported),
            )
        }
        scope.launch {
            preferencesStore.preferences.collect { preferences ->
                mutableState.update { state -> state.copy(preferences = preferences) }
            }
        }
        scope.launch { refreshDownloaded() }
    }

    /** The book's language as one the engine knows, or null. */
    fun normalize(tag: String?): String? = TranslationLanguages.normalize(tag, engine.supportedLanguages)

    fun download(tag: String) {
        if (tag in mutableState.value.downloading || tag in mutableState.value.downloaded) return
        mutableState.update { state -> state.copy(downloading = state.downloading + tag, failed = state.failed - tag) }
        scope.launch {
            val result = runSuspendCatching { engine.download(tag) }
            refreshDownloaded()
            mutableState.update { state ->
                state.copy(
                    downloading = state.downloading - tag,
                    failed = if (result.isFailure) state.failed + tag else state.failed,
                )
            }
        }
    }

    fun remove(tag: String) {
        if (tag == TranslationLanguages.BUILT_IN) return
        scope.launch {
            runSuspendCatching { engine.delete(tag) }
            refreshDownloaded()
        }
    }

    fun setTargetLanguage(tag: String) {
        scope.launch { preferencesStore.update { preferences -> preferences.copy(targetLanguage = tag) } }
    }

    /** Remembers the To you picked on a card, so the next translation uses it. */
    fun rememberPick(from: String, to: String) {
        val yours = mutableState.value.yourLanguage
        if (to == yours) return
        scope.launch {
            preferencesStore.update { preferences ->
                if (from == yours) preferences.copy(otherLanguage = to) else preferences.copy(targetLanguage = to)
            }
        }
    }

    suspend fun translate(text: String, from: String, to: String): String = engine.translate(text, from, to)

    private suspend fun refreshDownloaded() {
        val downloaded = runSuspendCatching { engine.downloadedLanguages() }.getOrNull() ?: return
        mutableState.update { state -> state.copy(loaded = true, downloaded = downloaded) }
    }

    companion object {
        /** [applicationScope] is the app's scope; state changes run on its main thread. */
        fun create(context: Context, applicationScope: CoroutineScope): TranslationService = TranslationService(
            engine = MlKitTranslationEngine(),
            preferencesStore = DataStoreTranslationPreferencesStore(context),
            scope = applicationScope + Dispatchers.Main.immediate,
        )
    }
}

/** The app's [TranslationService] for screens that show translation settings; null when there is none. */
val LocalTranslationService = androidx.compose.runtime.staticCompositionLocalOf<TranslationService?> { null }
