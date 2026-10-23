package com.example.unifiedreader.feature.updates

import kotlinx.serialization.Serializable

/**
 * A dotted release version ("0.12.0", or a tag such as "v0.13.0"), compared part by part. A
 * missing part counts as 0, so "0.13" equals "0.13.0".
 */
@Serializable
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        /** Null when [raw] is not a dotted number; a "-beta" or "+build" suffix is ignored. */
        fun parse(raw: String?): AppVersion? {
            val core = raw?.trim()?.removePrefix("v")?.removePrefix("V")
                ?.substringBefore('-')?.substringBefore('+')
                ?.takeIf { it.isNotEmpty() }
                ?: return null
            val parts = core.split('.').map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
            return AppVersion(parts)
        }
    }
}

/** The newest published release of the app and the APK attached to it. */
@Serializable
data class AppRelease(
    val version: AppVersion,
    val tagName: String,
    /** The release notes' bullet lines, plain text, in order. */
    val notes: List<String>,
    /** The release page on GitHub. */
    val pageUrl: String,
    val apk: AppReleaseApk,
)

@Serializable
data class AppReleaseApk(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** Lowercase hex SHA-256 that GitHub publishes for the asset, when it does. */
    val sha256: String?,
)
