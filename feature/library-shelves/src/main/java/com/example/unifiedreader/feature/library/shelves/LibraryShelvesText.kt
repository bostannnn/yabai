package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.librarySecondaryText

internal fun continueReadingSupportingText(
    item: ReadableItem,
): String = item.librarySecondaryText() ?: item.title
