package com.example.unifiedreader.feature.updates

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppReleaseParsingTest {

    @Test
    fun `versions compare part by part and ignore a v prefix and suffixes`() {
        assertEquals(AppVersion(listOf(0, 13, 0)), AppVersion.parse("v0.13.0"))
        assertEquals(AppVersion(listOf(1, 2, 3)), AppVersion.parse("1.2.3-beta+7"))
        assertEquals(0, AppVersion.parse("0.13")!!.compareTo(AppVersion.parse("0.13.0")!!))
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.1")!!)
        assertTrue(AppVersion.parse("0.12.1")!! > AppVersion.parse("0.12.0")!!)
        assertNull(AppVersion.parse("nightly"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse(null))
        assertEquals("0.13.0", AppVersion.parse("v0.13.0").toString())
    }

    @Test
    fun `a GitHub release becomes the release with its apk, checksum and bullet notes`() {
        val release = parseGitHubRelease(ReleaseJson)!!

        assertEquals(AppVersion(listOf(0, 13, 0)), release.version)
        assertEquals("0.13.0", release.tagName)
        assertEquals("https://github.com/bostannnn/yabai/releases/tag/0.13.0", release.pageUrl)
        assertEquals("yabai-0.13.0.apk", release.apk.name)
        assertEquals("https://github.com/bostannnn/yabai/releases/download/0.13.0/yabai-0.13.0.apk", release.apk.downloadUrl)
        assertEquals(61_179_881L, release.apk.sizeBytes)
        assertEquals("3b99929c592ae71a668bc76a845eecdd8c844024136d69047771f9fa4036b941", release.apk.sha256)
        assertEquals(
            listOf(
                "Add motion: screen transitions, predictive back, press feedback (step 8.1)",
                "Check the server's progress before pushing a queued save",
                "Kavita: see the readme",
            ),
            release.notes,
        )
    }

    @Test
    fun `releases without an apk, pre-releases and odd tags are not offered`() {
        assertNull(parseGitHubRelease(ReleaseJson.replace("yabai-0.13.0.apk\"", "yabai-0.13.0.zip\"")))
        assertNull(parseGitHubRelease(ReleaseJson.replace("\"prerelease\": false", "\"prerelease\": true")))
        assertNull(parseGitHubRelease(ReleaseJson.replace("\"tag_name\": \"0.13.0\"", "\"tag_name\": \"nightly\"")))
    }

    @Test
    fun `an asset without a digest has no checksum`() {
        val release = parseGitHubRelease(ReleaseJson.replace(Regex("\"digest\": \"[^\"]*\","), ""))!!

        assertNull(release.apk.sha256)
    }

    @Test
    fun `GitHub's generated notes keep only their bullet lines as plain text`() {
        val notes = parseReleaseNotes(
            """
            ## What's Changed
            * Fix the **reader** in `EpubReaderEntry` by @someone in [#12](https://github.com/x/y/pull/12)

            **Full Changelog**: https://github.com/x/y/compare/1...2
            - Full Changelog: nothing
            """.trimIndent(),
        )

        assertEquals(listOf("Fix the reader in EpubReaderEntry by @someone in #12"), notes)
        assertEquals(emptyList<String>(), parseReleaseNotes(null))
    }

    @Test
    fun `the source asks GitHub's latest-release endpoint and reads the answer`() = runTest {
        var requestedUrl = ""
        var acceptHeader: String? = null
        val client = HttpClient(
            MockEngine { request ->
                requestedUrl = request.url.toString()
                acceptHeader = request.headers[HttpHeaders.Accept]
                respond(ReleaseJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )

        val release = GitHubReleaseSource(client).latestRelease()

        assertEquals("https://api.github.com/repos/bostannnn/yabai/releases/latest", requestedUrl)
        assertEquals("application/vnd.github+json", acceptHeader)
        assertEquals("0.13.0", release?.version.toString())
    }

    @Test
    fun `no releases at all is no update, and a server error is a failure`() = runTest {
        val missing = HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) })
        assertNull(GitHubReleaseSource(missing).latestRelease())

        val broken = HttpClient(MockEngine { respond("rate limited", HttpStatusCode.Forbidden) })
        try {
            GitHubReleaseSource(broken).latestRelease()
            fail("expected a failure")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("403"))
        }
    }

    private companion object {
        val ReleaseJson = """
            {
              "tag_name": "0.13.0",
              "name": "yabai 0.13.0",
              "html_url": "https://github.com/bostannnn/yabai/releases/tag/0.13.0",
              "draft": false,
              "prerelease": false,
              "body": "## What's new\n\n- Add motion: screen transitions, predictive back, press feedback (step 8.1)\n- Check the server's progress before pushing a queued save\n- Kavita: see the [readme](https://example.com)\n\n**Full Changelog**: https://github.com/bostannnn/yabai/compare/0.12.0...0.13.0",
              "assets": [
                {
                  "name": "yabai-0.13.0.apk",
                  "digest": "sha256:3B99929C592AE71A668BC76A845EECDD8C844024136D69047771F9FA4036B941",
                  "size": 61179881,
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "https://github.com/bostannnn/yabai/releases/download/0.13.0/yabai-0.13.0.apk"
                }
              ]
            }
        """.trimIndent()
    }
}
