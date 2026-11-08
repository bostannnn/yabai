package com.example.unifiedreader.feature.translation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DeepL as the card meets it: picked, used without downloads, and the tablet when it fails. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeepLTranslationTest {
    private class TabletEngine(val onTablet: Set<String>) : TranslationEngine {
        override val supportedLanguages = setOf("en", "ru", "de", "he")
        val translated = mutableListOf<String>()
        override suspend fun downloadedLanguages(): Set<String> = onTablet
        override suspend fun download(tag: String) = Unit
        override suspend fun delete(tag: String) = Unit
        override suspend fun translate(text: String, from: String, to: String): String {
            translated += text
            return "tablet:$text"
        }
    }

    /** A saved key the keystore hasn't handed over yet: [key] has no value until [read]. */
    private class SlowKeyStore : DeepLKeyStore {
        override val key = MutableSharedFlow<String?>(replay = 1)
        fun read(saved: String?) {
            key.tryEmit(saved)
        }
        override suspend fun save(key: String) = Unit
        override suspend fun clear() = Unit
    }

    private class MemoryStore(initial: TranslationPreferences) : TranslationPreferencesStore {
        val flow = MutableStateFlow(initial)
        override val preferences = flow
        override suspend fun update(transform: (TranslationPreferences) -> TranslationPreferences) = flow.update(transform)
    }

    private class MemoryKeyStore(initial: String?) : DeepLKeyStore {
        override val key = MutableStateFlow(initial)
        override suspend fun save(key: String) {
            this.key.value = key
        }
        override suspend fun clear() {
            key.value = null
        }
    }

    /** DeepL answers with [status], or isn't there at all when [offline]. */
    private fun deepL(status: HttpStatusCode = HttpStatusCode.OK, offline: Boolean = false) = DeepLClient(
        HttpClient(
            MockEngine { request ->
                if (offline) throw IOException("offline")
                val body = if (request.url.encodedPath.endsWith("usage")) {
                    """{"character_count":10,"character_limit":500000}"""
                } else {
                    """{"translations":[{"text":"deepl:word"},{"text":"deepl:sentence"}]}"""
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
    )

    private class Setup(
        val service: TranslationService,
        val session: TranslationSession,
        val preferences: MemoryStore,
        val keys: MemoryKeyStore,
    )

    private fun TestScope.setUp(
        onTablet: Set<String> = setOf("en"),
        provider: TranslationProvider = TranslationProvider.DeepL,
        key: String? = "k:fx",
        client: DeepLClient = deepL(),
    ): Setup {
        val preferences = MemoryStore(TranslationPreferences(targetLanguage = "ru", provider = provider))
        val keys = MemoryKeyStore(key)
        val service = TranslationService(TabletEngine(onTablet), preferences, backgroundScope, client, keys) { Locale.ENGLISH }
        val session = TranslationSession(service, TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope)
        return Setup(service, session, preferences, keys)
    }

    /** DeepL answers on the mock engine's own thread: wait for the card to stop translating. */
    private suspend fun TranslationSession.settled(): TranslationPhase =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { current.first { it != null && it.phase !is TranslationPhase.Translating }!!.phase }
        }

    /** Waits for a finished card from [provider], skipping any earlier card still current. */
    private suspend fun TranslationSession.settledBy(provider: TranslationProvider): TranslationPhase.Done =
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                current.first { (it?.phase as? TranslationPhase.Done)?.by == provider }!!.phase as TranslationPhase.Done
            }
        }

    private val word = ReaderTextSelection(text = "reluctant", before = "He was ", after = " to leave.", bookLanguage = "en")

    @Test
    fun `with DeepL picked, a language not on the tablet needs no download`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp()

        setup.session.open(word)

        val done = setup.session.settled() as TranslationPhase.Done
        assertEquals("deepl:word", done.translation)
        assertEquals("deepl:sentence", done.sentence)
        assertEquals(TranslationProvider.DeepL, done.by)
        assertNull(done.fallback)
    }

    @Test
    fun `DeepL offline falls back to the tablet and says why`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp(onTablet = setOf("en", "ru"), client = deepL(offline = true))

        setup.session.open(word)

        val done = setup.session.settled() as TranslationPhase.Done
        assertEquals("tablet:reluctant", done.translation)
        assertEquals(TranslationProvider.Device, done.by)
        assertEquals(DeepLFailure.Unreachable, done.fallback)
    }

    @Test
    fun `DeepL failing with nothing on the tablet to fall back on fails the card`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp(client = deepL(status = HttpStatusCode(456, "Quota")))

        setup.session.open(word)

        assertEquals(TranslationPhase.Failed(TranslationFailure.DeepL), setup.session.settled())
    }

    @Test
    fun `a language DeepL lacks goes to the tablet, which asks for its download first`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp()

        setup.session.open(word.copy(bookLanguage = "he"))

        assertEquals(TranslationPhase.NeedsDownload(listOf("he", "ru")), setup.session.current.value?.phase)
    }

    @Test
    fun `switching to the tablet in the chip translates again there`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp(onTablet = setOf("en", "ru"))
        setup.session.open(word)
        setup.session.settled()

        setup.service.updatePreferences { it.copy(provider = TranslationProvider.Device) }
        // The save is queued behind this test's own step: let it run.
        yield()

        // The DeepL card stays current until the switch reaches the session, which can take more than one step on a
        // slow machine: wait for the card the tablet finished, not just any finished card.
        val done = setup.session.settledBy(TranslationProvider.Device)
        assertEquals("tablet:reluctant", done.translation)
    }

    @Test
    fun `DeepL picked without a key is the tablet`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp(key = null)
        setup.service.start()

        assertEquals(TranslationProvider.Device, setup.service.state.value.provider)
        assertFalse(setup.service.state.value.usesDeepL("en", "ru"))
    }

    @Test
    fun `a key that works is saved and picks DeepL`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp(provider = TranslationProvider.Device, key = null)
        setup.service.start()

        assertNull(setup.service.deepL!!.saveKey("  newkey:fx "))

        assertEquals("newkey:fx", setup.keys.key.value)
        assertEquals(TranslationProvider.DeepL, setup.preferences.flow.value.provider)
        val deepL = setup.service.deepL!!.state.value
        assertTrue(deepL.hasKey)
        assertTrue(deepL.isFree)
        assertEquals("••••wkey:fx", deepL.keyHint)
        assertEquals(DeepLUsage(10, 500_000), deepL.usage)
    }

    @Test
    fun `a refused key isn't saved`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp(provider = TranslationProvider.Device, key = null, client = deepL(status = HttpStatusCode.Forbidden))
        setup.service.start()

        assertEquals(DeepLFailure.KeyRefused, setup.service.deepL!!.saveKey("bad"))

        assertNull(setup.keys.key.value)
        assertEquals(TranslationProvider.Device, setup.preferences.flow.value.provider)
    }

    @Test
    fun `removing the key goes back to the tablet`() = runTest(UnconfinedTestDispatcher()) {
        val setup = setUp()
        setup.service.start()

        setup.service.deepL!!.removeKey()

        assertNull(setup.keys.key.value)
        assertFalse(setup.service.state.value.deepL.hasKey)
        assertEquals(TranslationProvider.Device, setup.preferences.flow.value.provider)
    }

    @Test
    fun `a card waits for the saved DeepL key instead of asking the tablet`() = runTest(UnconfinedTestDispatcher()) {
        val engine = TabletEngine(setOf("en", "ru"))
        val keys = SlowKeyStore()
        val preferences = MemoryStore(TranslationPreferences(targetLanguage = "ru", provider = TranslationProvider.DeepL))
        val service = TranslationService(engine, preferences, backgroundScope, deepL(), keys) { Locale.ENGLISH }
        val session = TranslationSession(service, TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope)

        session.open(word)
        // The tablet's languages are known, the key isn't yet: nothing is translated.
        assertTrue(service.state.value.loaded)
        assertNull(session.current.value)

        keys.read("k:fx")
        val done = session.settled() as TranslationPhase.Done
        assertEquals(TranslationProvider.DeepL, done.by)
        assertEquals("deepl:word", done.translation)
        assertTrue(engine.translated.isEmpty())
    }
}
