package com.example.unifiedreader.feature.translation

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLanguagesTest {
    private val supported = setOf("en", "ru", "de", "he", "zh", "uk")

    @Test
    fun `book languages become engine tags`() {
        assertEquals("en", TranslationLanguages.normalize("en-GB", supported))
        assertEquals("ru", TranslationLanguages.normalize("ru_RU", supported))
        assertEquals("de", TranslationLanguages.normalize("ger", supported))
        assertEquals("zh", TranslationLanguages.normalize("zh-Hant", supported))
        assertEquals("he", TranslationLanguages.normalize("iw", supported))
        assertNull(TranslationLanguages.normalize("tlh", supported))
        assertNull(TranslationLanguages.normalize(" ", supported))
    }

    @Test
    fun `an English book translates into your language`() {
        val pair = TranslationLanguages.resolvePair(null, "en", TranslationPreferences(targetLanguage = "ru"), "en")
        assertEquals("en" to "ru", pair)
    }

    @Test
    fun `a book in your language translates into the other one you used`() {
        val preferences = TranslationPreferences(targetLanguage = "ru", otherLanguage = "de")
        assertEquals("ru" to "de", TranslationLanguages.resolvePair(null, "ru", preferences, "en"))
        assertEquals("ru" to "en", TranslationLanguages.resolvePair(null, "ru", TranslationPreferences("ru"), "en"))
    }

    @Test
    fun `an English book with English as your language asks for a language`() {
        assertEquals("en" to null, TranslationLanguages.resolvePair(null, "en", TranslationPreferences(), "en"))
    }

    @Test
    fun `names are in the app's language`() {
        assertEquals("Russian", TranslationLanguages.displayName("ru", Locale.ENGLISH))
        assertEquals("Ukrainian", TranslationLanguages.displayName("uk", Locale.ENGLISH))
    }

    @Test
    fun `a word opens the card and finds its sentence`() {
        val selection = ReaderTextSelection(
            text = "reluctant",
            before = "The tram stopped. He was ",
            after = " to leave the warm kitchen. Outside it was dark.",
        )
        assertTrue(selection.isShort)
        assertEquals("He was reluctant to leave the warm kitchen.", selection.sentence)
    }

    @Test
    fun `a passage opens the sheet`() {
        assertFalse(ReaderTextSelection(text = "one two three four").isShort)
        assertTrue(ReaderTextSelection(text = "one two three").isShort)
    }

    @Test
    fun `phases follow what is on the tablet`() {
        val state = TranslationState(loaded = true, downloaded = setOf("en"))
        assertEquals(TranslationPhase.NeedsDownload(listOf("ru")), nextPhase("en", "ru", state))
        assertEquals(TranslationPhase.Downloading(listOf("ru")), nextPhase("en", "ru", state.copy(downloading = setOf("ru"))))
        assertEquals(TranslationPhase.Failed(TranslationFailure.Download), nextPhase("en", "ru", state.copy(failed = setOf("ru"))))
        assertNull(nextPhase("en", "ru", state.copy(downloaded = setOf("en", "ru"))))
        assertEquals(TranslationPhase.PickLanguage, nextPhase("en", null, state))
    }
}
