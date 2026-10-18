package com.example.unifiedreader.core.database.dao

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.unifiedreader.core.model.LibraryFacetDimension
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode

/**
 * Builds the paging query for a content-filtered library view: grid roots of [sourceType] that match
 * every active content dimension of [filters] (each persisted in `library_root_facets`), ordered by
 * [sortMode] — the same ordering the unfiltered paging sources use.
 *
 * Only content dimensions are handled (format, library, author, series, publisher, genre, tag,
 * language). Reading-status and file-location filters are runtime state (not in the tables), and
 * search is separate, so the caller must ensure those aren't active and keep the in-memory path when
 * they are. Filter values are passed as bound arguments; only the constant dimension names are
 * interpolated.
 */
fun buildFilteredGridRootsQuery(
    sourceType: String,
    sortMode: LibrarySortMode,
    filters: LibraryFilterState,
): SupportSQLiteQuery {
    val args = mutableListOf<Any>()
    val sql = StringBuilder("SELECT r.* FROM library_grid_roots r")
    if (sortMode == LibrarySortMode.LAST_READ) {
        sql.append(
            " LEFT JOIN (SELECT m.rootId AS rootId, MAX(p.updatedAtEpochMillis) AS lastReadAt " +
                "FROM library_root_members m INNER JOIN reading_progress p ON p.itemId = m.itemId " +
                "WHERE m.sourceType = ? AND p.isExplicitUnreadReset = 0 GROUP BY m.rootId) lr ON lr.rootId = r.id",
        )
        args.add(sourceType)
    }
    sql.append(" WHERE r.sourceType = ?")
    args.add(sourceType)

    sql.appendDimension(args, LibraryFacetDimension.FORMAT, filters.formats.map { format -> format.name })
    sql.appendDimension(args, LibraryFacetDimension.LIBRARY, filters.libraries.toList())
    sql.appendDimension(args, LibraryFacetDimension.AUTHOR, filters.authors.toList())
    sql.appendDimension(args, LibraryFacetDimension.SERIES, filters.series.toList())
    sql.appendDimension(args, LibraryFacetDimension.PUBLISHER, filters.publishers.toList())
    sql.appendDimension(args, LibraryFacetDimension.GENRE, filters.genres.toList())
    sql.appendDimension(args, LibraryFacetDimension.TAG, filters.tags.toList())
    sql.appendDimension(args, LibraryFacetDimension.LANGUAGE, filters.languages.toList())

    sql.append(
        when (sortMode) {
            LibrarySortMode.DATE_ADDED -> " ORDER BY r.addedAtEpochMillis DESC, r.title COLLATE NOCASE ASC"
            LibrarySortMode.LAST_READ -> " ORDER BY lr.lastReadAt IS NULL, lr.lastReadAt DESC, r.title COLLATE NOCASE ASC"
            else -> " ORDER BY r.title COLLATE NOCASE ASC"
        },
    )
    return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
}

private fun StringBuilder.appendDimension(
    args: MutableList<Any>,
    dimension: LibraryFacetDimension,
    values: List<String>,
) {
    if (values.isEmpty()) return
    val placeholders = values.joinToString(separator = ",") { "?" }
    append(
        " AND EXISTS (SELECT 1 FROM library_root_facets f WHERE f.rootId = r.id " +
            "AND f.dimension = '${dimension.name}' AND f.value IN ($placeholders))",
    )
    args.addAll(values)
}
