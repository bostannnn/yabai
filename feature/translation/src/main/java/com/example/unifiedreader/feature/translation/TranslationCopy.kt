package com.example.unifiedreader.feature.translation

/** Every word translation shows, in one place. */
internal object TranslationCopy {
    const val Translate = "Translate"
    const val Translation = "Translation"
    const val From = "From"
    const val FromTheBook = "From · the book"
    const val To = "To"
    const val TheBooksLanguage = "The book's language"
    const val TranslateFrom = "Translate from"
    const val OpenOnSelect = "Open when I select text"
    const val OpenOnSelectSupporting = "Off: select text, then tap Translate"
    const val TranslateInto = "Translate into"
    const val Swap = "Swap languages"
    const val Close = "Close"
    const val FindLanguage = "Find a language"
    const val OnThisTablet = "On this tablet"
    const val DownloadHeading = "Download · about 30 MB each"
    const val Downloading = "Downloading"
    const val OnThisDevice = "Translated on this device"
    const val Copy = "Copy"
    const val CopyTranslation = "Copy translation"
    const val OpenFull = "Open full"
    const val OpenIn = "Open in…"
    const val Selected = "Selected"
    const val InThisSentence = "In this sentence"
    const val NotNow = "Not now"
    const val Download = "Download"
    const val TryAgain = "Try again"
    const val Hide = "Hide"
    const val KeepReading = "You can keep reading; the translation follows."
    const val PickTo = "Pick a language to translate into."
    const val CouldntTranslate = "Couldn't translate this"
    const val DownloadFailed = "The download didn't finish. Check the connection and try again."
    const val TranslateFailed = "Something went wrong on this tablet. Try again, or open it in another app."
    const val BuiltIn = "Built in"
    const val Remove = "Remove"
    const val AddLanguage = "Add a language"
    const val YourLanguageSupporting = "Your language, used unless you pick another"
    const val LanguagesGroup = "Languages"
    const val Copied = "Copied"

    // DeepL
    const val TranslateWith = "Translate with"
    const val DeepL = "DeepL"
    const val ThisTablet = "This tablet"
    const val AddDeepL = "Add DeepL…"
    const val DeepLSupporting = "Better wording. Sends the selected text and its sentence to DeepL"
    const val DeepLNoKeySupporting = "Better wording, online. Add your own key to use it"
    const val ThisTabletSupporting = "Private and offline. Used whenever DeepL can't help"
    const val ApiKey = "API key"
    const val Change = "Change"
    const val ThisMonth = "This month"
    const val DeepLDialogTitle = "Translate with DeepL"
    const val DeepLDialogBody = "DeepL words translations better than the tablet does, especially whole sentences. " +
        "It needs your own key: DeepL's free API plan allows 500,000 characters a month."
    const val DeepLKeyWhere = "deepl.com › Account › API Keys. Kept on this tablet only"
    const val DeepLPrivacy = "What you select, and the sentence around it, goes to DeepL. Nothing else from your books does."
    const val CheckAndSave = "Check and save"
    const val Cancel = "Cancel"
    const val RemoveKey = "Remove key"
    const val DeepLFailedTranslate = "DeepL couldn't translate this, and the languages aren't on this tablet yet. " +
        "Try again, or switch to This tablet below."

    fun plan(isFree: Boolean): String = if (isFree) "Free plan" else "Pro plan"

    fun usage(used: Long, limit: Long): String =
        "${"%,d".format(used)} of ${"%,d".format(limit)} characters"

    /** Under a translation the tablet made because DeepL couldn't. */
    fun fellBack(failure: DeepLFailure, from: String, to: String): String = when (failure) {
        DeepLFailure.Unreachable -> "DeepL couldn't be reached, so this tablet translated."
        DeepLFailure.LimitReached -> "DeepL's characters for this month are used up, so this tablet translated."
        DeepLFailure.KeyRefused -> "DeepL refused the key, so this tablet translated. Check it in Settings › Translation."
        DeepLFailure.LanguageMissing -> {
            val missing = listOf(from, to).first { !DeepLLanguages.supports(it) }
            "DeepL doesn't translate ${TranslationLanguages.displayName(missing)}, so this tablet did."
        }
        DeepLFailure.Other -> "DeepL couldn't translate this, so this tablet did."
    }

    /** Why a key wasn't saved, under the field. */
    fun keyProblem(failure: DeepLFailure): String = when (failure) {
        DeepLFailure.KeyRefused -> "DeepL didn't accept this key. Copy it again from your DeepL account."
        DeepLFailure.Unreachable -> "DeepL couldn't be reached. Check the connection and try again."
        DeepLFailure.LimitReached -> "This key's characters for the month are used up."
        DeepLFailure.LanguageMissing, DeepLFailure.Other -> "DeepL said no. Check the key and try again."
    }

    fun downloadTitle(names: List<String>): String = "Translate ${names.joinToString(" and ")} on this tablet?"

    fun downloadBody(count: Int): String =
        if (count == 1) {
            "A one-time download of about 30 MB. After that it works with no internet."
        } else {
            "A one-time download of about 30 MB each. After that it works with no internet."
        }

    fun gettingReady(names: List<String>): String = "Getting ${names.joinToString(" and ")} ready"

    fun downloadLanguage(name: String): String = "Download $name"

    /** The hub row's line: "Into Russian · 2 languages on this tablet". */
    fun hubSummary(state: TranslationState): String {
        val into = "Into ${TranslationLanguages.displayName(state.yourLanguage)}"
        if (!state.loaded) return into
        val count = state.downloaded.size
        return "$into · $count ${if (count == 1) "language" else "languages"} on this tablet"
    }
}

/** The Settings hub row's line: "Into Russian · 2 languages on this tablet". */
fun translationHubSummary(state: TranslationState): String = TranslationCopy.hubSummary(state)
