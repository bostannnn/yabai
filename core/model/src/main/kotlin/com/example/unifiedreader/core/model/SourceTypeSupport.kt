package com.example.unifiedreader.core.model

fun SourceType.displayName(): String = when (this) {
    SourceType.KAVITA -> "Kavita"
    SourceType.STUMP -> "Stump"
    SourceType.GRIMMORY -> "Grimmory"
    SourceType.LOCAL -> "Local"
}

enum class KavitaLibrarySection {
    Home,
    Collections,
    ReadingLists,
}

fun KavitaLibrarySection.displayName(): String = when (this) {
    KavitaLibrarySection.Home -> "Home"
    KavitaLibrarySection.Collections -> "Collections"
    KavitaLibrarySection.ReadingLists -> "Reading lists"
}
