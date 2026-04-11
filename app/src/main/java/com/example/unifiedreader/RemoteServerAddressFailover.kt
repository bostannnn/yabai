package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import java.io.IOException
import java.net.URI

internal data class RemoteServerAddressResult<T>(
    val value: T,
    val activeBaseUrl: String,
)

internal suspend fun <T> runWithServerAddressFailover(
    baseUrl: String,
    backupBaseUrl: String?,
    activeBaseUrl: String?,
    block: suspend (baseUrl: String) -> T,
): RemoteServerAddressResult<T> {
    val candidates = buildList {
        activeBaseUrl?.takeIf(String::isNotBlank)?.let(::add)
        add(baseUrl)
        backupBaseUrl?.takeIf(String::isNotBlank)?.let(::add)
    }.distinct()

    var lastFailure: Throwable? = null
    candidates.forEachIndexed { index, candidate ->
        try {
            return RemoteServerAddressResult(
                value = block(candidate),
                activeBaseUrl = candidate,
            )
        } catch (throwable: Throwable) {
            val canRetry = throwable.isRemoteServerAddressRetryableFailure() &&
                index < candidates.lastIndex
            if (!canRetry) {
                throw throwable
            }
            lastFailure = throwable
        }
    }
    throw lastFailure ?: error("Unable to reach any configured server address.")
}

internal fun Throwable.isRemoteServerAddressRetryableFailure(): Boolean = generateSequence(this) { error ->
    error.cause
}.any { error ->
    error is IOException ||
        error::class.simpleName in setOf(
            "ConnectTimeoutException",
            "HttpRequestTimeoutException",
            "SocketTimeoutException",
            "UnresolvedAddressException",
        )
}

internal suspend fun <Session : StoredSourceSession, Api, T> runWithStoredSessionAddressFailover(
    sessionStore: SourceSessionStore<Session>,
    session: Session,
    createApi: (String) -> Api,
    withActiveBaseUrl: (Session, String) -> Session,
    block: suspend (api: Api, session: Session) -> T,
): T {
    val result = runWithServerAddressFailover(
        baseUrl = session.baseUrl,
        backupBaseUrl = session.backupBaseUrl,
        activeBaseUrl = session.activeBaseUrl,
    ) { selectedBaseUrl ->
        val activeSession = withActiveBaseUrl(session, selectedBaseUrl)
        block(createApi(selectedBaseUrl), activeSession)
    }
    val currentSession = sessionStore.sessionFlow.value ?: session
    if (currentSession.activeBaseUrl != result.activeBaseUrl) {
        sessionStore.save(withActiveBaseUrl(currentSession, result.activeBaseUrl))
    }
    return result.value
}

internal fun rewriteRemoteAssetUrlForActiveServer(
    url: String?,
    session: StoredSourceSession?,
): String? {
    if (url.isNullOrBlank() || session == null) return url
    val activeBaseUrl = session.activeBaseUrl?.takeIf(String::isNotBlank) ?: return url
    return runCatching {
        val assetUri = URI(url)
        if (!assetUri.isAbsolute) return url
        val assetScheme = assetUri.scheme?.lowercase()
        if (assetScheme != "http" && assetScheme != "https") {
            return url
        }
        val allowedOrigins = listOfNotNull(session.baseUrl, session.backupBaseUrl)
            .mapNotNull(::originKeyOrNull)
            .toSet()
        val shouldRewrite = assetUri.originKeyOrNull() in allowedOrigins ||
            assetUri.path.isKnownServerAssetPath(session.sourceType)
        if (!shouldRewrite) {
            return url
        }
        val activeUri = URI(activeBaseUrl)
        val resolvedPath = resolveActiveAssetPath(
            assetUri = assetUri,
            activeUri = activeUri,
            configuredBaseUrls = listOfNotNull(session.baseUrl, session.backupBaseUrl),
        )
        URI(
            activeUri.scheme,
            assetUri.userInfo,
            activeUri.host,
            activeUri.port,
            resolvedPath,
            assetUri.query,
            assetUri.fragment,
        ).toString()
    }.getOrDefault(url)
}

private fun resolveActiveAssetPath(
    assetUri: URI,
    activeUri: URI,
    configuredBaseUrls: List<String>,
): String {
    val activeBasePath = normalizedBasePath(activeUri.path)
    val matchedConfiguredBasePath = configuredBaseUrls
        .mapNotNull(::parseUriOrNull)
        .firstOrNull { configuredUri ->
            configuredUri.originKeyOrNull() == assetUri.originKeyOrNull() &&
                assetUri.path.hasBasePathPrefix(normalizedBasePath(configuredUri.path))
        }
        ?.let { configuredUri -> normalizedBasePath(configuredUri.path) }

    val relativePath = matchedConfiguredBasePath
        ?.let(assetUri.path::removeBasePathPrefix)
        ?: assetUri.path

    return when {
        activeBasePath == "/" -> relativePath
        relativePath.hasBasePathPrefix(activeBasePath) -> relativePath
        else -> joinBasePath(activeBasePath, relativePath)
    }
}

private fun parseUriOrNull(
    url: String,
): URI? = runCatching {
    URI(url)
}.getOrNull()

private fun normalizedBasePath(
    path: String?,
): String = path
    ?.takeIf(String::isNotBlank)
    ?.trimEnd('/')
    ?.takeIf(String::isNotBlank)
    ?: "/"

private fun String.hasBasePathPrefix(
    basePath: String,
): Boolean {
    if (basePath == "/") return startsWith("/")
    return this == basePath || startsWith("$basePath/")
}

private fun String.removeBasePathPrefix(
    basePath: String,
): String {
    if (basePath == "/" || !hasBasePathPrefix(basePath)) return this
    val suffix = removePrefix(basePath)
    return if (suffix.startsWith("/")) suffix else "/$suffix"
}

private fun joinBasePath(
    basePath: String,
    path: String,
): String = when {
    basePath == "/" -> path
    path == "/" -> basePath
    else -> basePath + if (path.startsWith("/")) path else "/$path"
    }

private fun String.isKnownServerAssetPath(
    sourceType: SourceType,
): Boolean = when (sourceType) {
    SourceType.KAVITA ->
        contains("/api/Image/series-cover") || contains("/api/Image/chapter-cover")
    SourceType.STUMP ->
        matches(Regex(""".*/(media|series)/[^/]+/thumbnail$"""))
    SourceType.GRIMMORY ->
        matches(Regex(""".*/api/v1/media/book/[^/]+/thumbnail$"""))
    SourceType.LOCAL -> false
}

private fun originKeyOrNull(
    url: String,
): Triple<String, String, Int>? = runCatching {
    URI(url).originKeyOrNull()
}.getOrNull()

private fun URI.originKeyOrNull(): Triple<String, String, Int>? {
    val scheme = scheme?.lowercase() ?: return null
    val host = host?.lowercase() ?: return null
    val port = when {
        port != -1 -> port
        scheme == "https" -> 443
        scheme == "http" -> 80
        else -> -1
    }
    return Triple(scheme, host, port)
}
