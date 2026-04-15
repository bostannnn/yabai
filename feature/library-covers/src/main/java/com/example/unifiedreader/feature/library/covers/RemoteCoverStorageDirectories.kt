package com.example.unifiedreader.feature.library.covers

data class RemoteCoverStorageDirectory(
    val storageDirectoryName: String,
    val legacyDirectoryName: String? = null,
)

const val KAVITA_COVER_DIRECTORY_NAME = "kavita-covers"
const val KAVITA_LEGACY_COVER_DIRECTORY_NAME = "kavita-covers"

const val STUMP_COVER_DIRECTORY_NAME = "stump-covers"
const val STUMP_LEGACY_COVER_DIRECTORY_NAME = "stump-covers"

const val GRIMMORY_COVER_DIRECTORY_NAME = "grimmory-covers"
const val GRIMMORY_LEGACY_COVER_DIRECTORY_NAME = "grimmory-covers"
