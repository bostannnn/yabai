package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import com.example.unifiedreader.core.ui.components.R
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment
import kotlinx.coroutines.launch

/** The app's modal bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val sheetRecipe = UnifiedReaderThemeTokens.components.sheet
    val sheetContentRecipe = sheetRecipe.copy(
        borderAlpha = 0f,
        frameAlpha = 0f,
    )
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val dismissLabel = stringResource(R.string.app_sheet_dismiss)
    val expandLabel = stringResource(R.string.app_sheet_expand)
    val collapseLabel = stringResource(R.string.app_sheet_collapse)
    val sheetShape = RoundedCornerShape(
        topStart = presentation.cardCornerRadius,
        topEnd = presentation.cardCornerRadius,
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = semanticColors.surfaceBase,
        shape = sheetShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .unifiedReaderComponentTreatment(
                    recipe = sheetContentRecipe,
                    accentColor = semanticColors.readerAccent,
                    secondaryColor = semanticColors.syncAccent,
                    overlayColor = MaterialTheme.colorScheme.onSurface,
                    shape = sheetShape,
                )
                .windowInsetsPadding(BottomSheetDefaults.windowInsets),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .semantics(mergeDescendants = true) {
                        dismiss(dismissLabel) {
                            scope.launch {
                                sheetState.hide()
                                if (!sheetState.isVisible) onDismissRequest()
                            }
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
                BottomSheetDefaults.DragHandle()
            }
            content()
        }
    }
}
