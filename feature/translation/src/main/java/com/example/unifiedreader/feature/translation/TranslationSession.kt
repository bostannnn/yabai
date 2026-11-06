package com.example.unifiedreader.feature.translation

import com.example.unifiedreader.core.model.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One selection's translation, from open to close: picks From and To, waits for a download
 * when a language isn't on the tablet, then translates the selection and, for a word, the
 * sentence it sits in. The reader keeps one per open book; [open] replaces what it showed.
 */
class TranslationSession(
    private val service: TranslationService,
    private val scope: CoroutineScope,
) {
    private val mutableCurrent = MutableStateFlow<TextSelectionTranslation?>(null)
    val current: StateFlow<TextSelectionTranslation?> = mutableCurrent.asStateFlow()
    private var pickedFrom: String? = null
    private var pickedTo: String? = null
    private var work: Job? = null

    fun open(selection: ReaderTextSelection) {
        service.start()
        pickedFrom = null
        pickedTo = null
        show(selection)
    }

    fun close() {
        work?.cancel()
        mutableCurrent.value = null
    }

    fun pickFrom(tag: String) {
        val now = mutableCurrent.value ?: return
        pickedFrom = tag
        if (pickedTo == tag) pickedTo = null
        show(now.selection)
    }

    /** Back to the book's own language for From. */
    fun resetFrom() {
        val now = mutableCurrent.value ?: return
        pickedFrom = null
        show(now.selection)
    }

    fun pickTo(tag: String) {
        val now = mutableCurrent.value ?: return
        pickedTo = tag
        service.rememberPick(now.from, tag)
        if (pickedFrom == tag || (pickedFrom == null && now.from == tag)) pickedFrom = now.to
        show(now.selection)
    }

    fun swap() {
        val now = mutableCurrent.value ?: return
        val to = now.to ?: return
        pickedFrom = to
        pickedTo = now.from
        show(now.selection)
    }

    /** Starts the downloads the card asked for; the translation follows when they land. */
    fun download() {
        val phase = mutableCurrent.value?.phase as? TranslationPhase.NeedsDownload ?: return
        phase.languages.forEach(service::download)
    }

    fun retry() {
        val now = mutableCurrent.value ?: return
        val failed = listOf(now.from, now.to).filterNotNull().filter { it in service.state.value.failed }
        failed.forEach(service::download)
        show(now.selection)
    }

    private fun show(selection: ReaderTextSelection) {
        work?.cancel()
        work = scope.launch {
            // The tablet's languages load once; until then the pair can't be judged.
            service.state.collect { state ->
                if (!state.loaded) return@collect
                val (from, to) = TranslationLanguages.resolvePair(
                    pickedFrom = pickedFrom,
                    bookLanguage = service.normalize(selection.bookLanguage),
                    preferences = state.preferences,
                    deviceLanguage = state.deviceLanguage,
                ).let { (from, to) -> from to (pickedTo?.takeIf { it != from } ?: to) }
                val waiting = nextPhase(from, to, state)
                val shown = mutableCurrent.value
                val alreadyDone = shown != null && shown.selection == selection && shown.from == from &&
                    shown.to == to && (shown.phase is TranslationPhase.Done || shown.phase is TranslationPhase.Translating)
                when {
                    waiting != null -> mutableCurrent.value = TextSelectionTranslation(selection, from, to, waiting)
                    !alreadyDone -> translate(selection, from, checkNotNull(to))
                }
            }
        }
    }

    private suspend fun translate(selection: ReaderTextSelection, from: String, to: String) {
        mutableCurrent.value = TextSelectionTranslation(selection, from, to, TranslationPhase.Translating)
        val phase = runSuspendCatching {
            coroutineScope {
                val sentence = selection.sentence?.takeIf { selection.isShort }
                val sentenceTranslation = sentence?.let { text -> async { service.translate(text, from, to) } }
                val translation = service.translate(selection.text.trim(), from, to)
                TranslationPhase.Done(translation = translation, sentence = sentenceTranslation?.await())
            }
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            TranslationPhase.Failed(TranslationFailure.Translate)
        }
        mutableCurrent.value = TextSelectionTranslation(selection, from, to, phase)
    }
}
