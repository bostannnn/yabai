package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryPageRange

// A source's Pages filter in one preference value: "min:max:unknown", an empty bound for none
// ("150:300:0", ":50:1"). No value means no filter.

internal fun encodeLibraryPageRange(range: LibraryPageRange?): String? = range
    ?.takeIf { it.min != null || it.max != null }
    ?.let { "${it.min ?: ""}:${it.max ?: ""}:${if (it.includeUnknown) 1 else 0}" }

internal fun decodeLibraryPageRange(value: String?): LibraryPageRange? {
    val parts = value?.split(':')?.takeIf { it.size == 3 } ?: return null
    val min = parts[0].toIntOrNull()?.takeIf { it >= 0 }
    val max = parts[1].toIntOrNull()?.takeIf { it >= 0 }
    if (min == null && max == null) return null
    return LibraryPageRange(min = min, max = max, includeUnknown = parts[2] == "1")
}
