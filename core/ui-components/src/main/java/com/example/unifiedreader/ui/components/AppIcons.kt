package com.example.unifiedreader.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.example.unifiedreader.core.ui.components.R

/**
 * The app's icon set: Material Symbols Rounded, bundled as vector drawables. Feature code uses
 * these instead of the Material Icons artifacts so every icon shares one weight and style.
 */
object AppIcons {
    val Add: ImageVector @Composable get() = icon(R.drawable.ic_add)
    val ArrowBack: ImageVector @Composable get() = icon(R.drawable.ic_arrow_back)
    val ArrowDownward: ImageVector @Composable get() = icon(R.drawable.ic_arrow_downward)
    val ArrowForward: ImageVector @Composable get() = icon(R.drawable.ic_arrow_forward)
    val ArrowUpward: ImageVector @Composable get() = icon(R.drawable.ic_arrow_upward)
    val Check: ImageVector @Composable get() = icon(R.drawable.ic_check)
    val Checklist: ImageVector @Composable get() = icon(R.drawable.ic_checklist)
    val Clear: ImageVector @Composable get() = icon(R.drawable.ic_close)
    val Close: ImageVector @Composable get() = icon(R.drawable.ic_close)
    val Cloud: ImageVector @Composable get() = icon(R.drawable.ic_cloud)
    val CollectionsBookmark: ImageVector @Composable get() = icon(R.drawable.ic_collections_bookmark)
    val Delete: ImageVector @Composable get() = icon(R.drawable.ic_delete)
    val DeleteSweep: ImageVector @Composable get() = icon(R.drawable.ic_delete_sweep)
    val Download: ImageVector @Composable get() = icon(R.drawable.ic_download)
    val DownloadDone: ImageVector @Composable get() = icon(R.drawable.ic_download_done)
    val ErrorOutline: ImageVector @Composable get() = icon(R.drawable.ic_error)
    val FilterList: ImageVector @Composable get() = icon(R.drawable.ic_filter_list)
    val Folder: ImageVector @Composable get() = icon(R.drawable.ic_folder)
    val FormatSize: ImageVector @Composable get() = icon(R.drawable.ic_format_size)
    val History: ImageVector @Composable get() = icon(R.drawable.ic_history)
    val Home: ImageVector @Composable get() = icon(R.drawable.ic_home)
    val HourglassTop: ImageVector @Composable get() = icon(R.drawable.ic_hourglass_top)
    val KeyboardArrowLeft: ImageVector @Composable get() = icon(R.drawable.ic_keyboard_arrow_left)
    val KeyboardArrowRight: ImageVector @Composable get() = icon(R.drawable.ic_keyboard_arrow_right)
    val Menu: ImageVector @Composable get() = icon(R.drawable.ic_menu)
    val MenuBook: ImageVector @Composable get() = icon(R.drawable.ic_menu_book)
    val MoreVert: ImageVector @Composable get() = icon(R.drawable.ic_more_vert)
    val NavigateBefore: ImageVector @Composable get() = icon(R.drawable.ic_navigate_before)
    val NavigateNext: ImageVector @Composable get() = icon(R.drawable.ic_navigate_next)
    val Refresh: ImageVector @Composable get() = icon(R.drawable.ic_refresh)
    val Search: ImageVector @Composable get() = icon(R.drawable.ic_search)
    val SelectAll: ImageVector @Composable get() = icon(R.drawable.ic_select_all)
    val Settings: ImageVector @Composable get() = icon(R.drawable.ic_settings)
    val Star: ImageVector @Composable get() = icon(R.drawable.ic_star_filled)
    val StarBorder: ImageVector @Composable get() = icon(R.drawable.ic_star)
    val StarHalf: ImageVector @Composable get() = icon(R.drawable.ic_star_half)
    val Stop: ImageVector @Composable get() = icon(R.drawable.ic_stop_filled)
    val Toc: ImageVector @Composable get() = icon(R.drawable.ic_toc)
    val Tune: ImageVector @Composable get() = icon(R.drawable.ic_tune)
    val VerticalAlignTop: ImageVector @Composable get() = icon(R.drawable.ic_vertical_align_top)
    val ViewCarousel: ImageVector @Composable get() = icon(R.drawable.ic_view_carousel)

    @Composable
    private fun icon(@DrawableRes id: Int): ImageVector = ImageVector.vectorResource(id)
}
