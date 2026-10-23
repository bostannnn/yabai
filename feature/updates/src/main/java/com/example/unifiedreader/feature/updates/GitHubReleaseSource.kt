package com.example.unifiedreader.feature.updates

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the app learns about its newest release. */
fun interface AppReleaseSource {
    /** The latest published release with an APK, or null when there is none. Throws on failure. */
    suspend fun latestRelease(): AppRelease?
}

/**
 * The latest release of [repository] from the GitHub REST API: one unauthenticated request
 * (GitHub allows 60 an hour per address). Drafts and pre-releases never come back from
 * `releases/latest`.
 */
class GitHubReleaseSource(
    private val httpClient: HttpClient,
    private val repository: String = DEFAULT_REPOSITORY,
    private val apiBaseUrl: String = "https://api.github.com",
) : AppReleaseSource {
    override suspend fun latestRelease(): AppRelease? {
        val response = httpClient.get("$apiBaseUrl/repos/$repository/releases/latest") {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.UserAgent, "yabai-android")
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw IOException("GitHub answered ${response.status.value} for the latest release")
        }
        return parseGitHubRelease(response.bodyAsText())
    }

    companion object {
        const val DEFAULT_REPOSITORY = "bostannnn/yabai"
    }
}

private val gitHubJson = Json { ignoreUnknownKeys = true }

/** Null when the release has no usable version or no APK attached. */
internal fun parseGitHubRelease(body: String): AppRelease? {
    val release = gitHubJson.decodeFromString<GitHubReleaseDto>(body)
    if (release.draft || release.prerelease) return null
    val version = AppVersion.parse(release.tagName) ?: return null
    val apks = release.assets.filter { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
    val apk = apks.firstOrNull { asset -> asset.name.startsWith("yabai-") } ?: apks.firstOrNull() ?: return null
    return AppRelease(
        version = version,
        tagName = release.tagName,
        notes = parseReleaseNotes(release.body),
        pageUrl = release.htmlUrl,
        apk = AppReleaseApk(
            name = apk.name,
            downloadUrl = apk.browserDownloadUrl,
            sizeBytes = apk.size,
            sha256 = apk.digest
                ?.takeIf { it.startsWith("sha256:") }
                ?.removePrefix("sha256:")
                ?.lowercase(),
        ),
    )
}

/**
 * The bullet lines of a release body as plain text. The "Full Changelog" link and other
 * paragraphs are left out, and Markdown emphasis, code and links are reduced to their text.
 */
internal fun parseReleaseNotes(body: String?): List<String> =
    body.orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter { line -> line.startsWith("- ") || line.startsWith("* ") }
        .map { line -> line.drop(2).toPlainText() }
        .filter { line -> line.isNotEmpty() && !line.startsWith("Full Changelog", ignoreCase = true) }
        .toList()

private val markdownLink = Regex("""\[([^\]]*)]\([^)]*\)""")

private fun String.toPlainText(): String =
    replace(markdownLink, "$1")
        .replace("**", "")
        .replace("`", "")
        .trim()

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
private data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
    val digest: String? = null,
)
