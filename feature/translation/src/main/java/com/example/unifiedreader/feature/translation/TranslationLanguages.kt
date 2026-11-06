package com.example.unifiedreader.feature.translation

import java.util.Locale

/** A language the engine can translate, by its ML Kit tag ("en", "ru", "zh"). */
data class TranslationLanguage(
    val tag: String,
    val name: String,
)

/** Tags, names and the rules for picking From and To. Pure, so it is tested without the engine. */
object TranslationLanguages {
    /** English ships with the engine: it is always on the tablet and can't be removed. */
    const val BUILT_IN = "en"

    /** The language's name in the app's language: "Russian", "German". */
    fun displayName(tag: String, inLocale: Locale = Locale.getDefault()): String {
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(inLocale).ifBlank { tag }
        return name.replaceFirstChar { first -> if (first.isLowerCase()) first.titlecase(inLocale) else first.toString() }
    }

    fun languages(tags: Collection<String>, inLocale: Locale = Locale.getDefault()): List<TranslationLanguage> =
        tags.map { tag -> TranslationLanguage(tag, displayName(tag, inLocale)) }
            .sortedBy { language -> language.name.lowercase(inLocale) }

    /**
     * A book's or a device's language as one the engine knows: "en-GB" and "eng" become "en",
     * "zh-Hant" becomes "zh". Null when the engine can't translate it.
     */
    fun normalize(tag: String?, supported: Set<String>): String? {
        val raw = tag?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val language = Locale.forLanguageTag(raw.replace('_', '-')).language.takeIf { it.isNotEmpty() }
            ?: raw.substringBefore('-').lowercase(Locale.ROOT)
        val twoLetter = if (language.length == 3) {
            BibliographicCodes[language] ?: iso3ToTwoLetter(language) ?: language
        } else {
            language
        }
        // Java still knows Hebrew, Indonesian and Yiddish by their old codes.
        val modern = when (twoLetter) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> twoLetter
        }
        return modern.takeIf { it in supported } ?: twoLetter.takeIf { it in supported }
    }

    /**
     * From and To for a selection. From is the reader's own pick, else the book's language, else
     * English. To is your language unless it is the book's own, then the other language you used
     * last, then English; null when nothing is left, and the card asks you to pick one.
     */
    fun resolvePair(
        pickedFrom: String?,
        bookLanguage: String?,
        preferences: TranslationPreferences,
        deviceLanguage: String?,
    ): Pair<String, String?> {
        val from = pickedFrom ?: bookLanguage ?: BUILT_IN
        val yours = preferences.targetLanguage ?: deviceLanguage ?: BUILT_IN
        val to = listOfNotNull(yours, preferences.otherLanguage, BUILT_IN).firstOrNull { it != from }
        return from to to
    }

    /** ISO 639-2's library codes, which some books carry and Java doesn't know ("ger", not "deu"). */
    private val BibliographicCodes = mapOf(
        "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs",
        "dut" to "nl", "fre" to "fr", "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is",
        "mac" to "mk", "mao" to "mi", "may" to "ms", "per" to "fa", "rum" to "ro", "slo" to "sk",
        "tib" to "bo", "wel" to "cy",
    )

    private fun iso3ToTwoLetter(code: String): String? =
        Locale.getISOLanguages().firstOrNull { twoLetter ->
            runCatching { Locale.forLanguageTag(twoLetter).isO3Language }.getOrNull() == code
        }
}
