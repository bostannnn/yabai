package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.ui.components.R
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import kotlinx.coroutines.launch

/**
 * The app's modal bottom sheet: 28 dp top corners and the kit's own drag handle, on
 * [containerColor] (the theme's base surface when unspecified, or a cover's first tint). With
 * [skipPartiallyExpanded] a tall sheet opens all the way instead of stopping halfway.
 */
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color = Color.Unspecified,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppSheetLayout(
        onDismissRequest = onDismissRequest,
        skipPartiallyExpanded = skipPartiallyExpanded,
        containerColor = containerColor,
    ) { _ -> content() }
}

/** [AppSheet], with a `hide` for the content that slides the sheet away before dismissing it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSheetLayout(
    onDismissRequest: () -> Unit,
    skipPartiallyExpanded: Boolean = false,
    containerColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.(hide: () -> Unit) -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val scope = rememberCoroutineScope()
    val dismissLabel = stringResource(R.string.app_sheet_dismiss)
    val expandLabel = stringResource(R.string.app_sheet_expand)
    val collapseLabel = stringResource(R.string.app_sheet_collapse)
    val sheetShape = RoundedCornerShape(
        topStart = AppSheetDefaults.cornerRadius,
        topEnd = AppSheetDefaults.cornerRadius,
    )
    val hide: () -> Unit = {
        scope.launch {
            sheetState.hide()
            if (!sheetState.isVisible) onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor.takeOrElse { semanticColors.surfaceBase },
        shape = sheetShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // The sheet is its own window, so toasts raised from it show inside it.
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(BottomSheetDefaults.windowInsets),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics(mergeDescendants = true) {
                            dismiss(dismissLabel) {
                                hide()
                                true
                            }
                            when (sheetState.currentValue) {
                                SheetValue.PartiallyExpanded -> expand(expandLabel) {
                                    scope.launch { sheetState.expand() }
                                    true
                                }
                                SheetValue.Expanded -> {
                                    if (sheetState.hasPartiallyExpandedState) {
                                        collapse(collapseLabel) {
                                            scope.launch { sheetState.partialExpand() }
                                            true
                                        }
                                    }
                                }
                                SheetValue.Hidden -> Unit
                            }
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = AppSheetDefaults.handleVerticalPadding)
                            .size(width = AppSheetDefaults.handleWidth, height = AppSheetDefaults.handleHeight)
                            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(AppSheetDefaults.handleCornerRadius)),
                    )
                }
                content(hide)
            }
            AppToastHost(modifier = Modifier.matchParentSize())
        }
    }
}

object AppSheetDefaults {
    val cornerRadius: Dp = 28.dp
    val handleWidth: Dp = 36.dp
    val handleHeight: Dp = 4.dp
    val handleCornerRadius: Dp = 2.dp

    /** Space above and below the handle (Material's is 22 dp, which left a tall empty band). */
    val handleVerticalPadding: Dp = 16.dp
}
