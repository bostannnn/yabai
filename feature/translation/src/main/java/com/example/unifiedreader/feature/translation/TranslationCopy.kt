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
    const val FromSupporting = "From the book's details; English when they don't say"
    const val LanguagesGroup = "Languages"
    const val Copied = "Copied"

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
