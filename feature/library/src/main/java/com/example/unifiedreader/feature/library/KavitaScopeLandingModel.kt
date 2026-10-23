package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.KavitaLibrarySection

/** "56 collections", "1 reading list", or "3 of 56 collections" while searching. */
fun kavitaScopeLandingCountLabel(
    section: KavitaLibrarySection,
    total: Int,
    shown: Int,
    searchActive: Boolean,
): String {
    val totalLabel = section.countLabel(total)
    return if (searchActive) "$shown of $totalLabel" else totalLabel
}

/** A tile's meta line: a collection holds series, a reading list holds items in order. */
fun kavitaScopeLandingItemMeta(section: KavitaLibrarySection, itemCount: Int): String = when (section) {
    KavitaLibrarySection.Collections -> "$itemCount series"
    else -> if (itemCount == 1) "1 item" else "$itemCount items"
}

/** Why the landing has nothing to show, which decides what its empty state says and offers. */
enum class KavitaScopeLandingEmptyReason {
    NoSearchMatches,
    NotConnected,
    Unreachable,
    NoneMade,
}

fun kavitaScopeLandingEmptyReason(
    searchActive: Boolean,
    notConnected: Boolean,
    unreachable: Boolean,
): KavitaScopeLandingEmptyReason = when {
    notConnected -> KavitaScopeLandingEmptyReason.NotConnected
    searchActive -> KavitaScopeLandingEmptyReason.NoSearchMatches
    unreachable -> KavitaScopeLandingEmptyReason.Unreachable
    else -> KavitaScopeLandingEmptyReason.NoneMade
}

/** The empty state's title and sentence. */
data class KavitaScopeLandingEmptyText(val title: String, val body: String)

fun kavitaScopeLandingEmptyText(
    section: KavitaLibrarySection,
    reason: KavitaScopeLandingEmptyReason,
): KavitaScopeLandingEmptyText {
    val plural = section.pluralNoun()
    return when (reason) {
        KavitaScopeLandingEmptyReason.NoSearchMatches -> KavitaScopeLandingEmptyText(
            title = "No $plural match this search",
            body = "Try fewer words, or a word from a description.",
        )
        KavitaScopeLandingEmptyReason.NotConnected -> KavitaScopeLandingEmptyText(
            title = "Kavita isn't connected",
            body = "Add your Kavita server, and its $plural show up here after the first sync.",
        )
        KavitaScopeLandingEmptyReason.Unreachable -> KavitaScopeLandingEmptyText(
            title = "Can't reach Kavita",
            body = "Check that the server is running and that this device can reach it, then try again.",
        )
        KavitaScopeLandingEmptyReason.NoneMade -> KavitaScopeLandingEmptyText(
            title = "No $plural yet",
            body = "${plural.replaceFirstChar(Char::uppercase)} you make in Kavita show up here after the next sync.",
        )
    }
}

private fun KavitaLibrarySection.pluralNoun(): String = when (this) {
    KavitaLibrarySection.Collections -> "collections"
    else -> "reading lists"
}

private fun KavitaLibrarySection.countLabel(count: Int): String = when (this) {
    KavitaLibrarySection.Collections -> if (count == 1) "1 collection" else "$count collections"
    else -> if (count == 1) "1 reading list" else "$count reading lists"
}
