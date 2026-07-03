package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsOverviewGrid(
    useSingleLaneCards: Boolean,
    header: @Composable () -> Unit,
    themeCard: @Composable () -> Unit,
    syncCard: @Composable () -> Unit,
    supplementarySection: SettingsSupplementarySection?,
    libraryFiltersCard: @Composable () -> Unit,
    kavitaCard: @Composable () -> Unit,
    grimmoryCard: @Composable () -> Unit,
    stumpCard: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(SettingsOverviewMinColumnWidthDp.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = SettingsMaxContentWidth)
                .padding(horizontal = SettingsScreenHorizontalPaddingDp.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalItemSpacing = 16.dp,
            horizontalArrangement = Arrangement.spacedBy(SettingsOverviewColumnSpacingDp.dp),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                header()
            }
            item {
                themeCard()
            }
            item {
                syncCard()
            }
            supplementarySection?.let { section ->
                item {
                    section.content()
                }
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                libraryFiltersCard()
            }
            item(
                span = if (useSingleLaneCards) {
                    StaggeredGridItemSpan.SingleLane
                } else {
                    StaggeredGridItemSpan.FullLine
                },
            ) {
                kavitaCard()
            }
            item(
                span = if (useSingleLaneCards) {
                    StaggeredGridItemSpan.SingleLane
                } else {
                    StaggeredGridItemSpan.FullLine
                },
            ) {
                grimmoryCard()
            }
            item(
                span = if (useSingleLaneCards) {
                    StaggeredGridItemSpan.SingleLane
                } else {
                    StaggeredGridItemSpan.FullLine
                },
            ) {
                stumpCard()
            }
        }
    }
}
