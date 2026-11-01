package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot

/**
 * The books whose place stays on this device for now: open in a reader ([openItemIds]) with the
 * place at the very end. Reaching the end and turning back is not finishing a book, and a server
 * told it was finished keeps a trace of it (Grimmory keeps the finish date when the place goes
 * back), so the place is uploaded once the reader leaves the book.
 */
internal fun Map<String, ReaderProgressSnapshot>.heldAtBookEnd(
    openItemIds: Set<String>,
): Set<String> = openItemIds.filterTo(mutableSetOf()) { itemId -> this[itemId]?.isAtBookEnd() == true }

internal fun List<ReadableItem>.withoutHeld(
    heldItemIds: Set<String>,
): List<ReadableItem> = if (heldItemIds.isEmpty()) this else filterNot { item -> item.id in heldItemIds }

/** Finished, or far enough that Grimmory counts the book read (99.5%). */
private fun ReaderProgressSnapshot.isAtBookEnd(): Boolean =
    isFinished || (percent ?: 0.0) >= BookEndPercent

private const val BookEndPercent = 0.995
