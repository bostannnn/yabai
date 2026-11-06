package com.example.unifiedreader.feature.translation

import android.content.Context
import io.ktor.client.HttpClient
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val deepL: DeepLState = DeepLState(),
) {
    /** Translate with, as it applies: DeepL only with a key. */
    val provider: TranslationProvider
        get() = if (preferences.provider == TranslationProvider.DeepL && deepL.hasKey) TranslationProvider.DeepL else TranslationProvider.Device

    /** Whether DeepL translates this pair now: picked, a key saved, both languages known to it. */
    fun usesDeepL(from: String, to: String): Boolean =
        provider == TranslationProvider.DeepL && DeepLLanguages.supports(from) && DeepLLanguages.supports(to)

    /** "Translate into" as it applies now: your pick, else the device's language, else English. */
    val yourLanguage: String
        get() = preferences.targetLanguage ?: deviceLanguage ?: TranslationLanguages.BUILT_IN
}

/** Your DeepL key as Settings shows it: never the key itself. */
data class DeepLState(
    val hasKey: Boolean = false,
    /** The key's last characters: "••••3f2a:fx". */
    val keyHint: String? = null,
    val isFree: Boolean = false,
    /** This month's characters; null until DeepL has been asked. */
    val usage: DeepLUsage? = null,
)

/** The selection and its sentence, translated, and who did it. */
data class SelectionTranslationResult(
    val translation: String,
    val sentence: String?,
    val by: TranslationProvider,
    val fallback: DeepLFailure? = null,
)

/**
 * The app's one translation owner: the engine, the languages on the tablet and your language
 * choices. Construction only; nothing is read until [start], which the reader and the settings
 * page call when they first show translation.
 */
class TranslationService internal constructor(
    private val engine: TranslationEngine,
    private val preferencesStore: TranslationPreferencesStore,
    private val scope: CoroutineScope,
    private val deepLClient: DeepLClient? = null,
    private val deepLKeyStore: DeepLKeyStore? = null,
    private val deviceLocale: () -> Locale = { Locale.getDefault() },
) {
    private var deepLKey: String? = null
    private val mutableState = MutableStateFlow(TranslationState())
    val state: StateFlow<TranslationState> = mutableState.asStateFlow()
    private var started = false
    private var readingPreferences = false

    /**
     * Reads only your choices, not the engine: an open book needs [TranslationPreferences.openOnSelect]
     * before the first selection, and ML Kit stays untouched until you translate.
     */
    fun startPreferences() {
        if (readingPreferences) return
        readingPreferences = true
        scope.launch {
            preferencesStore.preferences.collect { preferences ->
                mutableState.update { state -> state.copy(preferences = preferences) }
            }
        }
        deepLKeyStore?.let { store ->
            scope.launch {
                store.key.collect { key ->
                    deepLKey = key
                    mutableState.update { state ->
                        state.copy(
                            deepL = if (key == null) {
                                DeepLState()
                            } else {
                                state.deepL.copy(hasKey = true, keyHint = keyHint(key), isFree = DeepLClient.isFreeKey(key))
                            },
                        )
                    }
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        startPreferences()
        val supported = engine.supportedLanguages
        mutableState.update { state ->
            state.copy(
                supported = supported,
                deviceLanguage = TranslationLanguages.normalize(deviceLocale().toLanguageTag(), supported),
            )
        }
        scope.launch { refreshDownloaded() }
    }

    /** The book's language as one the engine knows, or null. */
    fun normalize(tag: String?): String? = TranslationLanguages.normalize(tag, engine.supportedLanguages)

    fun download(tag: String) {
        if (tag in mutableState.value.downloading || tag in mutableState.value.downloaded) return
        mutableState.update { state -> state.copy(downloading = state.downloading + tag, failed = state.failed - tag) }
        scope.launch {
            val result = runCatching { engine.download(tag) }
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
            runCatching { engine.delete(tag) }
            refreshDownloaded()
        }
    }

    fun setOpenOnSelect(open: Boolean) {
        scope.launch { preferencesStore.update { preferences -> preferences.copy(openOnSelect = open) } }
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

    fun setProvider(provider: TranslationProvider) {
        scope.launch { preferencesStore.update { preferences -> preferences.copy(provider = provider) } }
    }

    /**
     * Asks DeepL whether [key] works (its usage), and saves it and picks DeepL when it does.
     * Returns why not otherwise.
     */
    suspend fun saveDeepLKey(key: String): DeepLFailure? {
        val client = deepLClient ?: return DeepLFailure.Other
        val store = deepLKeyStore ?: return DeepLFailure.Other
        val trimmed = key.trim()
        val usage = try {
            client.usage(trimmed)
        } catch (error: DeepLException) {
            return error.failure
        }
        store.save(trimmed)
        deepLKey = trimmed
        mutableState.update { state ->
            state.copy(deepL = DeepLState(hasKey = true, keyHint = keyHint(trimmed), isFree = DeepLClient.isFreeKey(trimmed), usage = usage))
        }
        preferencesStore.update { preferences -> preferences.copy(provider = TranslationProvider.DeepL) }
        return null
    }

    fun removeDeepLKey() {
        scope.launch {
            deepLKeyStore?.clear()
            deepLKey = null
            mutableState.update { state -> state.copy(deepL = DeepLState()) }
            preferencesStore.update { preferences -> preferences.copy(provider = TranslationProvider.Device) }
        }
    }

    /** This month's characters, for Settings. */
    fun refreshDeepLUsage() {
        val key = deepLKey ?: return
        val client = deepLClient ?: return
        scope.launch {
            val usage = runCatching { client.usage(key) }.getOrNull() ?: return@launch
            mutableState.update { state -> state.copy(deepL = state.deepL.copy(usage = usage)) }
        }
    }

    /**
     * Translates [text] and, when given, the [sentence] it sits in. DeepL when it is picked and
     * knows both languages (the sentence also tells it which meaning the word has); the tablet
     * otherwise, and whenever DeepL fails and the tablet has both languages.
     */
    suspend fun translateSelection(text: String, sentence: String?, from: String, to: String): SelectionTranslationResult {
        val state = mutableState.value
        val key = deepLKey
        val client = deepLClient
        var fallback: DeepLFailure? = null
        if (state.preferences.provider == TranslationProvider.DeepL && key != null && client != null) {
            try {
                val translated = client.translate(key, listOfNotNull(text, sentence), from, to, context = sentence)
                return SelectionTranslationResult(translated[0], translated.getOrNull(1), TranslationProvider.DeepL)
            } catch (error: DeepLException) {
                fallback = error.failure
                val onTablet = mutableState.value.downloaded
                if (from !in onTablet || to !in onTablet) throw error
            }
        }
        val translation = engine.translate(text, from, to)
        val sentenceTranslation = sentence?.let { engine.translate(it, from, to) }
        return SelectionTranslationResult(translation, sentenceTranslation, TranslationProvider.Device, fallback)
    }

    private suspend fun refreshDownloaded() {
        val downloaded = runCatching { engine.downloadedLanguages() }.getOrNull() ?: return
        mutableState.update { state -> state.copy(loaded = true, downloaded = downloaded) }
    }

    companion object {
        /** [httpClient] reaches DeepL, on the internet: give it internet timeouts. */
        fun create(context: Context, httpClient: HttpClient): TranslationService = TranslationService(
            engine = MlKitTranslationEngine(),
            preferencesStore = DataStoreTranslationPreferencesStore(context),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            deepLClient = DeepLClient(httpClient),
            deepLKeyStore = KeystoreDeepLKeyStore(context),
        )

        internal fun keyHint(key: String): String = "••••" + key.takeLast(if (DeepLClient.isFreeKey(key)) 7 else 4)
    }
}

/** The app's [TranslationService] for screens that show translation settings; null when there is none. */
val LocalTranslationService = androidx.compose.runtime.staticCompositionLocalOf<TranslationService?> { null }
