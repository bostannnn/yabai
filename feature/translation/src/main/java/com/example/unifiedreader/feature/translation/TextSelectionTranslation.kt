package com.example.unifiedreader.feature.translation

import androidx.compose.ui.geometry.Rect

/**
 * Text you selected in a book: the selection, the text around it (for the sentence), the
 * book's language and where the selection is on screen (for the card), in the overlay's pixels.
 */
data class ReaderTextSelection(
    val text: String,
    val before: String = "",
    val after: String = "",
    val bookLanguage: String? = null,
    val bounds: Rect? = null,
) {
    val words: Int get() = text.trim().split(WhitespaceRegex).count { it.isNotEmpty() }

    /** One to three words open the card; anything longer opens the sheet. */
    val isShort: Boolean get() = words in 1..ShortSelectionWords

    /** The sentence the selection sits in, when it is more than the selection itself. */
    val sentence: String?
        get() {
            val start = before.lastIndexOfAny(SentenceEnds).let { index -> before.substring(index + 1) }
            val endIndex = after.indexOfAny(SentenceEnds)
            val end = if (endIndex >= 0) after.substring(0, endIndex + 1) else after
            val sentence = (start + text + end).replace(WhitespaceRegex, " ").trim()
            return sentence.takeIf { it.length > text.trim().length && it.length <= MaxSentenceLength }
        }

    companion object {
        const val ShortSelectionWords = 3
        private const val MaxSentenceLength = 400
        private val SentenceEnds = charArrayOf('.', '!', '?', '…', '\n')
        private val WhitespaceRegex = Regex("\\s+")
    }
}

/** Where a translation stands. */
sealed interface TranslationPhase {
    /** No To yet (the book is in your language and you have no other): pick one. */
    data object PickLanguage : TranslationPhase

    /** These languages aren't on the tablet yet. */
    data class NeedsDownload(val languages: List<String>) : TranslationPhase

    data class Downloading(val languages: List<String>) : TranslationPhase

    data object Translating : TranslationPhase

    data class Done(val translation: String, val sentence: String?) : TranslationPhase

    data class Failed(val reason: TranslationFailure) : TranslationPhase
}

enum class TranslationFailure {
    /** A download didn't finish (offline, usually). */
    Download,

    /** The engine couldn't translate. */
    Translate,
}

/** What the card or sheet needs from the moment: the selection, the pair and the phase. */
data class TextSelectionTranslation(
    val selection: ReaderTextSelection,
    val from: String,
    val to: String?,
    val phase: TranslationPhase,
)

/** The pure part of the flow: what to show for a selection, given what is on the tablet. */
internal fun nextPhase(
    from: String,
    to: String?,
    state: TranslationState,
): TranslationPhase? {
    if (to == null) return TranslationPhase.PickLanguage
    val missing = listOf(from, to).distinct().filterNot { it in state.downloaded }
    return when {
        missing.isEmpty() -> null
        missing.any { it in state.downloading } -> TranslationPhase.Downloading(missing)
        missing.any { it in state.failed } -> TranslationPhase.Failed(TranslationFailure.Download)
        else -> TranslationPhase.NeedsDownload(missing)
    }
}
