package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppEmptyState
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppTonalButton

/** Why the grid is empty, which decides what the empty state says and offers. */
internal enum class LibraryEmptyReason {
    /** The search (and maybe filters) matched nothing. */
    NoSearchMatches,

    /** The filters matched nothing. */
    NoFilterMatches,

    /** A server source has nothing to show because it cannot be reached. */
    SourceUnreachable,

    /** A server source was never connected (no stored session), so it has nothing to sync. */
    SourceNotConnected,

    /** The source has no books at all. */
    Empty,
}

internal fun libraryEmptyReason(
    searchActive: Boolean,
    filtersActive: Boolean,
    sourceUnreachable: Boolean,
    sourceNotConnected: Boolean = false,
): LibraryEmptyReason = when {
    searchActive -> LibraryEmptyReason.NoSearchMatches
    filtersActive -> LibraryEmptyReason.NoFilterMatches
    sourceNotConnected -> LibraryEmptyReason.SourceNotConnected
    sourceUnreachable -> LibraryEmptyReason.SourceUnreachable
    else -> LibraryEmptyReason.Empty
}

/**
 * The empty grid: a drawing, a sentence, and the action that fixes it (clear the filters,
 * try the server again, import a file or connect a server). Actions without a handler are left out.
 */
@Composable
internal fun LibraryEmptyState(
    reason: LibraryEmptyReason,
    selectedSource: SourceType,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
    onImportFile: (() -> Unit)? = null,
    onConnectServer: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val sourceName = selectedSource.displayName()
    val isLocal = selectedSource == SourceType.LOCAL
    when (reason) {
        LibraryEmptyReason.NoSearchMatches -> AppEmptyState(
            title = "No books match this search",
            body = "Try fewer words, or search for an author or a series.",
            modifier = modifier,
        )
        LibraryEmptyReason.NoFilterMatches -> AppEmptyState(
            title = "No books match these filters",
            body = "Remove a filter, or clear them all to see the whole library.",
            modifier = modifier,
            actions = { LibraryEmptyAction("Clear filters", onClearFilters, filled = true) },
        )
        LibraryEmptyReason.SourceUnreachable -> AppEmptyState(
            title = "Can't reach $sourceName",
            body = "Check that the server is running and that this device can reach it, then try again.",
            modifier = modifier,
            actions = onRetry?.let { retry -> { LibraryEmptyAction("Try again", retry, filled = true) } },
        )
        LibraryEmptyReason.SourceNotConnected -> AppEmptyState(
            title = "$sourceName isn't connected",
            body = "Add your $sourceName server in Settings, and its books show up here after the first sync.",
            modifier = modifier,
            actions = onConnectServer?.let { connect -> { LibraryEmptyAction("Connect $sourceName", connect, filled = true) } },
        )
        LibraryEmptyReason.Empty -> AppEmptyState(
            title = "Nothing here yet",
            body = if (isLocal) {
                "Import an EPUB, PDF or comic from this device, or connect a Kavita, Stump or Grimmory server."
            } else {
                "Books added to $sourceName show up here after the next sync."
            },
            modifier = modifier,
            actions = if (isLocal && (onImportFile != null || onConnectServer != null)) {
                {
                    onImportFile?.let { LibraryEmptyAction("Import a file", it, filled = true, withAddIcon = true) }
                    onConnectServer?.let { LibraryEmptyAction("Connect a server", it, filled = onImportFile == null) }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun RowScope.LibraryEmptyAction(
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    withAddIcon: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (withAddIcon) {
            Icon(
                imageVector = AppIcons.Add,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
            )
        }
        Text(text = label)
    }
    if (filled) {
        AppButton(onClick = onClick, content = content)
    } else {
        AppTonalButton(onClick = onClick, content = content)
    }
}
