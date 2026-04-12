package com.example.unifiedreader.core.model

fun SourceType.displayName(): String = when (this) {
    SourceType.KAVITA -> "Kavita"
    SourceType.STUMP -> "Stump"
    SourceType.GRIMMORY -> "Grimmory"
    SourceType.LOCAL -> "Local"
}
