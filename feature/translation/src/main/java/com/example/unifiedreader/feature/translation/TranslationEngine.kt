package com.example.unifiedreader.feature.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** What translates text, and keeps a language's files on the tablet. ML Kit in the app; a fake in tests. */
interface TranslationEngine {
    /** Every language the engine knows, by tag. */
    val supportedLanguages: Set<String>

    /** The languages whose files are on the tablet, English included. */
    suspend fun downloadedLanguages(): Set<String>

    /** Fetches a language's files (about 30 MB); returns once they are on the tablet. */
    suspend fun download(tag: String)

    suspend fun delete(tag: String)

    /** Both languages must be downloaded first. */
    suspend fun translate(text: String, from: String, to: String): String
}

/**
 * Google's ML Kit, on the device. One translator is kept open for the last pair used, so a
 * page of look-ups doesn't reload the models each time; another pair closes it.
 */
internal class MlKitTranslationEngine : TranslationEngine {
    private val models by lazy { RemoteModelManager.getInstance() }
    private val translatorLock = Mutex()
    private var openPair: Pair<String, String>? = null
    private var openTranslator: Translator? = null

    override val supportedLanguages: Set<String> by lazy { TranslateLanguage.getAllLanguages().toSet() }

    override suspend fun downloadedLanguages(): Set<String> =
        models.getDownloadedModels(TranslateRemoteModel::class.java).await()
            .mapTo(mutableSetOf()) { model -> model.language } + TranslationLanguages.BUILT_IN

    override suspend fun download(tag: String) {
        // Wi-Fi or mobile data both fine: it is the reader asking, and the card says the size.
        models.download(model(tag), DownloadConditions.Builder().build()).await()
    }

    override suspend fun delete(tag: String) {
        if (tag == TranslationLanguages.BUILT_IN) return
        translatorLock.withLock {
            if (openPair?.first == tag || openPair?.second == tag) closeTranslator()
        }
        models.deleteDownloadedModel(model(tag)).await()
    }

    override suspend fun translate(text: String, from: String, to: String): String {
        val translator = translatorLock.withLock {
            if (openPair != (from to to)) {
                closeTranslator()
                openTranslator = Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(from)
                        .setTargetLanguage(to)
                        .build(),
                )
                openPair = from to to
            }
            checkNotNull(openTranslator)
        }
        return translator.translate(text).await()
    }

    private fun closeTranslator() {
        openTranslator?.close()
        openTranslator = null
        openPair = null
    }

    private fun model(tag: String) = TranslateRemoteModel.Builder(tag).build()
}
