package com.example.unifiedreader.feature.translation

import com.example.unifiedreader.core.model.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Your DeepL key as Settings shows it: never the key itself. */
data class DeepLState(
    val hasKey: Boolean = false,
    /** The key's last characters: "••••3f2a:fx". */
    val keyHint: String? = null,
    val isFree: Boolean = false,
    /** This month's characters; null until DeepL has been asked. */
    val usage: DeepLUsage? = null,
    /** False until the saved key, or that there is none, has been read. */
    val loaded: Boolean = false,
)

/**
 * Your DeepL key and this month's use, and translating with them. Saving a key picks DeepL in
 * Translate with ([onPicked]); removing it goes back to the tablet.
 */
class DeepLAccount internal constructor(
    private val client: DeepLClient,
    private val keyStore: DeepLKeyStore,
    private val scope: CoroutineScope,
    private val onPicked: suspend (TranslationProvider) -> Unit,
) {
    private var key: String? = null
    private val mutableState = MutableStateFlow(DeepLState())
    val state: StateFlow<DeepLState> = mutableState.asStateFlow()

    internal fun start() {
        scope.launch {
            keyStore.key.collect { saved ->
                key = saved
                mutableState.update { state -> if (saved == null) DeepLState(loaded = true) else state.withKey(saved) }
            }
        }
    }

    /** Asks DeepL whether [key] works (its usage); saves it and picks DeepL when it does, else says why not. */
    suspend fun saveKey(key: String): DeepLFailure? {
        val trimmed = key.trim()
        val usage = try {
            client.usage(trimmed)
        } catch (error: DeepLException) {
            return error.failure
        }
        keyStore.save(trimmed)
        this.key = trimmed
        mutableState.value = DeepLState().withKey(trimmed).copy(usage = usage)
        onPicked(TranslationProvider.DeepL)
        return null
    }

    fun removeKey() {
        scope.launch {
            keyStore.clear()
            key = null
            mutableState.value = DeepLState(loaded = true)
            onPicked(TranslationProvider.Device)
        }
    }

    /** This month's characters, for Settings. */
    fun refreshUsage() {
        val saved = key ?: return
        scope.launch {
            val usage = runSuspendCatching { client.usage(saved) }.getOrNull() ?: return@launch
            mutableState.update { state -> state.copy(usage = usage) }
        }
    }

    /** Translates [texts] in one request; throws [DeepLException], also when there is no key. */
    internal suspend fun translate(texts: List<String>, from: String, to: String, context: String?): List<String> {
        val saved = key ?: throw DeepLException(DeepLFailure.KeyRefused, "No DeepL key")
        return client.translate(saved, texts, from, to, context)
    }

    private fun DeepLState.withKey(key: String) =
        copy(hasKey = true, keyHint = keyHint(key), isFree = DeepLClient.isFreeKey(key), loaded = true)

    internal companion object {
        /** A free key's hint keeps its ":fx" ending, so it reads as one. */
        private const val FreeKeyHintLength = 7
        private const val KeyHintLength = 4

        fun keyHint(key: String): String =
            "••••" + key.takeLast(if (DeepLClient.isFreeKey(key)) FreeKeyHintLength else KeyHintLength)
    }
}
