package com.example.unifiedreader.feature.translation

import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationSessionTest {
    private class FakeEngine : TranslationEngine {
        override val supportedLanguages = setOf("en", "ru", "de")
        val onTablet = mutableSetOf("en")
        val downloads = mutableMapOf<String, CompletableDeferred<Unit>>()
        val translated = mutableListOf<String>()

        override suspend fun downloadedLanguages(): Set<String> = onTablet.toSet()
        override suspend fun download(tag: String) {
            downloads.getOrPut(tag) { CompletableDeferred() }.await()
            onTablet += tag
        }
        override suspend fun delete(tag: String) {
            onTablet -= tag
        }
        override suspend fun translate(text: String, from: String, to: String): String {
            translated += text
            return "[$to] $text"
        }
    }

    private class FakeStore(initial: TranslationPreferences) : TranslationPreferencesStore {
        val flow = MutableStateFlow(initial)
        override val preferences = flow
        override suspend fun update(transform: (TranslationPreferences) -> TranslationPreferences) {
            flow.update(transform)
        }
    }

    private fun TestScope.setUp(engine: FakeEngine, preferences: TranslationPreferences): Pair<TranslationService, TranslationSession> {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope
        val service = TranslationService(engine, FakeStore(preferences), backgroundScope) { Locale.ENGLISH }
        return service to TranslationSession(service, scope)
    }

    private val word = ReaderTextSelection(
        text = "reluctant",
        before = "He was ",
        after = " to leave.",
        bookLanguage = "en-US",
    )

    @Test
    fun `a word waits for its language, then translates with its sentence`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine()
        val (service, session) = setUp(engine, TranslationPreferences(targetLanguage = "ru"))

        session.open(word)
        assertEquals(TranslationPhase.NeedsDownload(listOf("ru")), session.current.value?.phase)

        session.download()
        assertEquals(TranslationPhase.Downloading(listOf("ru")), session.current.value?.phase)

        engine.downloads.getValue("ru").complete(Unit)
        val done = session.current.value?.phase as TranslationPhase.Done
        assertEquals("[ru] reluctant", done.translation)
        assertEquals("[ru] He was reluctant to leave.", done.sentence)
        assertTrue("ru" in service.state.value.downloaded)
    }

    @Test
    fun `swap turns the pair round`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine().apply { onTablet += "ru" }
        val (_, session) = setUp(engine, TranslationPreferences(targetLanguage = "ru"))

        session.open(word)
        session.swap()

        val now = session.current.value!!
        assertEquals("ru" to "en", now.from to now.to)
    }

    @Test
    fun `picking another To translates again and is remembered`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine().apply { onTablet += setOf("ru", "de") }
        val (service, session) = setUp(engine, TranslationPreferences(targetLanguage = "ru"))

        session.open(word)
        session.pickTo("de")

        assertEquals("[de] reluctant", (session.current.value?.phase as TranslationPhase.Done).translation)
        assertEquals("de", service.state.value.preferences.targetLanguage)
    }

    @Test
    fun `a failed download offers to try again`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine()
        val (_, session) = setUp(engine, TranslationPreferences(targetLanguage = "ru"))

        session.open(word)
        session.download()
        engine.downloads.getValue("ru").completeExceptionally(IllegalStateException("offline"))

        assertEquals(TranslationPhase.Failed(TranslationFailure.Download), session.current.value?.phase)
    }

    @Test
    fun `a card that follows the selection keeps your languages as the selection grows`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine().apply { onTablet += setOf("ru", "de") }
        val (_, session) = setUp(engine, TranslationPreferences(targetLanguage = "ru"))
        val followed = word.copy(followsSelection = true)

        session.follow(followed)
        session.pickTo("de")
        val longer = followed.copy(text = "reluctant to leave the house at night")
        session.follow(longer)

        val now = session.current.value!!
        assertEquals(longer, now.selection)
        assertEquals("de", now.to)
        assertEquals("[de] reluctant to leave the house at night", (now.phase as TranslationPhase.Done).translation)
    }

    @Test
    fun `following after Translate opened the card starts afresh`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine().apply { onTablet += setOf("ru", "de") }
        val (_, session) = setUp(engine, TranslationPreferences(targetLanguage = "ru"))

        session.open(word)
        session.pickFrom("de")
        session.follow(word.copy(followsSelection = true))

        assertEquals("en", session.current.value?.from)
        assertTrue(session.current.value!!.selection.followsSelection)
    }

    @Test
    fun `the same selection again translates once`() = runTest(UnconfinedTestDispatcher()) {
        val engine = FakeEngine().apply { onTablet += "ru" }
        val (_, session) = setUp(engine, TranslationPreferences(targetLanguage = "ru"))
        val followed = word.copy(followsSelection = true)

        session.follow(followed)
        session.follow(followed)

        assertEquals(listOf("reluctant"), engine.translated.filter { it == "reluctant" })
    }
}
