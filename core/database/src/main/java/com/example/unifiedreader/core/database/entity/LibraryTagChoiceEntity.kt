package com.example.unifiedreader.core.database.entity

import androidx.room.Entity

/**
 * Where you put one of a source's tags (or one of Kavita's Locations) in Browse, by its lower-case
 * name, so every book with that tag follows. [field], [kind] and [region] hold enum names from
 * core:model (`LibraryTagField`, `LibraryTagKind`, `LibraryPlaceRegion`). Kept on this device only.
 */
@Entity(
    tableName = "library_tag_choices",
    primaryKeys = ["sourceType", "field", "tagKey"],
)
data class LibraryTagChoiceEntity(
    val sourceType: String,
    val field: String,
    val tagKey: String,
    val kind: String,
    val region: String?,
    val chosenAtEpochMillis: Long,
)
