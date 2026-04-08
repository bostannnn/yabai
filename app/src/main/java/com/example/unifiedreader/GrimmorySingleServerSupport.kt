package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem

// The app supports one active Grimmory server at a time. Grimmory items from any
// previous Grimmory connection are stale until the library is synced again.
internal fun List<ReadableItem>.forActiveGrimmoryServer(
    session: GrimmoryStoredSession,
): List<ReadableItem> = forSource(session.sourceType)
