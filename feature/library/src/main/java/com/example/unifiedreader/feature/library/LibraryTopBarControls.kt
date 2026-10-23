package com.example.unifiedreader.feature.library

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppCircularProgress
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuDefaults
import com.example.unifiedreader.ui.components.AppMenuItem
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.appProgressRing
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kavita's "Refresh library" in the top bar's menu. */
data class LibraryRefreshAction(
    val enabled: Boolean,
    val refreshing: Boolean,
    val onRefresh: () -> Unit,
)

/**
 * The library top bar's "•••": Import a file and, on Kavita, Refresh library. With [showSelectionTip]
 * it also explains, under the items, that a long-press selects books. The button turns a ring
 * while the library refreshes.
 */
@Composable
fun LibraryTopBarMoreMenu(
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier,
    refresh: LibraryRefreshAction? = null,
    showSelectionTip: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val refreshing = refresh?.refreshing == true
    Box(modifier = modifier) {
        AppIconButton(
            onClick = { expanded = true },
            modifier = (if (refreshing) Modifier.appProgressRing(progress = null) else Modifier).semantics {
                contentDescription = if (refreshing) "More options, refreshing the library" else "More options"
            },
        ) {
            Icon(
                imageVector = AppIcons.MoreHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(LibraryTopBarMenuWidth),
        ) {
            AppMenuItem(
                text = { Text(text = "Import a file") },
                leadingIcon = { Icon(imageVector = AppIcons.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onImportFile()
                },
            )
            if (refresh != null) {
                AppMenuItem(
                    text = { Text(text = if (refreshing) "Refreshing library" else "Refresh library") },
                    leadingIcon = {
                        if (refreshing) {
                            AppCircularProgress(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = AppIcons.Refresh, contentDescription = null)
                        }
                    },
                    enabled = refresh.enabled && !refreshing,
                    onClick = {
                        expanded = false
                        refresh.onRefresh()
                    },
                )
            }
            if (showSelectionTip) {
                AppDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Long-press a cover to select books for download.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = AppMenuDefaults.horizontalPadding,
                        vertical = 8.dp,
                    ),
                )
            }
        }
    }
}

/**
 * The bar that replaces the top bar while books are selected (it sits on the selected
 * container): close, how many are picked, Select all, and Download N. Download is the selected
 * pair inverted, not `primary`: High Contrast's primary is as white as its selected container.
 */
@Composable
fun LibrarySelectionTopBar(
    selectedCount: Int,
    selectableCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = UnifiedReaderThemeTokens.colors.surfaceSelected
    val onSelected = UnifiedReaderThemeTokens.colors.onSurfaceSelected
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(imageVector = AppIcons.Close, contentDescription = "Cancel selection", tint = onSelected)
        }
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = onSelected,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AppTextButton(
            onClick = onSelectAll,
            enabled = selectedCount < selectableCount,
            colors = ButtonDefaults.textButtonColors(contentColor = onSelected),
        ) {
            Text(text = "Select all", fontWeight = FontWeight.SemiBold)
        }
        AppButton(
            onClick = onDownload,
            enabled = selectedCount > 0,
            colors = ButtonDefaults.buttonColors(containerColor = onSelected, contentColor = selected),
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
            modifier = Modifier.height(40.dp),
        ) {
            Icon(
                imageVector = AppIcons.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (selectedCount > 0) "Download $selectedCount" else "Download",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Whether the "•••" menu still shows the long-press tip: until the user has selected books once.
 * The stored answer loads off the main thread; until it has, the tip stays hidden.
 */
@Stable
class LibrarySelectionTip internal constructor(
    private val context: Context,
) {
    var shouldShow: Boolean by mutableStateOf(false)
        private set

    private var hasSelected: Boolean? = null

    internal suspend fun load() {
        val selected = withContext(Dispatchers.IO) { preferences().getBoolean(HasSelectedKey, false) }
        if (hasSelected == null) {
            hasSelected = selected
            shouldShow = !selected
        }
    }

    /** Books were selected: the tip has done its job. */
    fun markSelected() {
        if (hasSelected == true) return
        hasSelected = true
        shouldShow = false
        preferences().edit().putBoolean(HasSelectedKey, true).apply()
    }

    private fun preferences(): SharedPreferences =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    private companion object {
        const val PreferencesName = "library_tips"
        const val HasSelectedKey = "has_selected_books"
    }
}

@Composable
fun rememberLibrarySelectionTip(): LibrarySelectionTip {
    val context = LocalContext.current.applicationContext
    val tip = remember(context) { LibrarySelectionTip(context) }
    LaunchedEffect(tip) { tip.load() }
    return tip
}

private val LibraryTopBarMenuWidth = 260.dp
