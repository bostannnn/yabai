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
    val Description: ImageVector @Composable get() = icon(R.drawable.ic_description)
    val Delete: ImageVector @Composable get() = icon(R.drawable.ic_delete)
    val DeleteSweep: ImageVector @Composable get() = icon(R.drawable.ic_delete_sweep)
    val DragHandle: ImageVector @Composable get() = icon(R.drawable.ic_drag_handle)
    val Download: ImageVector @Composable get() = icon(R.drawable.ic_download)
    val DownloadDone: ImageVector @Composable get() = icon(R.drawable.ic_download_done)
    val ErrorOutline: ImageVector @Composable get() = icon(R.drawable.ic_error)
    val FilterList: ImageVector @Composable get() = icon(R.drawable.ic_filter_list)
    val Folder: ImageVector @Composable get() = icon(R.drawable.ic_folder)
    val FormatSize: ImageVector @Composable get() = icon(R.drawable.ic_format_size)
    val GridView: ImageVector @Composable get() = icon(R.drawable.ic_grid_view)
    val History: ImageVector @Composable get() = icon(R.drawable.ic_history)
    val Home: ImageVector @Composable get() = icon(R.drawable.ic_home)
    val Info: ImageVector @Composable get() = icon(R.drawable.ic_info)
    val HourglassTop: ImageVector @Composable get() = icon(R.drawable.ic_hourglass_top)
    val KeyboardArrowLeft: ImageVector @Composable get() = icon(R.drawable.ic_keyboard_arrow_left)
    val KeyboardArrowRight: ImageVector @Composable get() = icon(R.drawable.ic_keyboard_arrow_right)
    val Menu: ImageVector @Composable get() = icon(R.drawable.ic_menu)
    val MenuBook: ImageVector @Composable get() = icon(R.drawable.ic_menu_book)
    val MoreHoriz: ImageVector @Composable get() = icon(R.drawable.ic_more_horiz)
    val MoreVert: ImageVector @Composable get() = icon(R.drawable.ic_more_vert)
    val NavigateBefore: ImageVector @Composable get() = icon(R.drawable.ic_navigate_before)
    val NavigateNext: ImageVector @Composable get() = icon(R.drawable.ic_navigate_next)
    val OpenInNew: ImageVector @Composable get() = icon(R.drawable.ic_open_in_new)
    val Palette: ImageVector @Composable get() = icon(R.drawable.ic_palette)
    val Refresh: ImageVector @Composable get() = icon(R.drawable.ic_refresh)
    val Remove: ImageVector @Composable get() = icon(R.drawable.ic_remove)
    val Search: ImageVector @Composable get() = icon(R.drawable.ic_search)
    val SelectAll: ImageVector @Composable get() = icon(R.drawable.ic_select_all)
    val Settings: ImageVector @Composable get() = icon(R.drawable.ic_settings)
    val Star: ImageVector @Composable get() = icon(R.drawable.ic_star_filled)
    val StarBorder: ImageVector @Composable get() = icon(R.drawable.ic_star)
    val StarHalf: ImageVector @Composable get() = icon(R.drawable.ic_star_half)
    val SwapVert: ImageVector @Composable get() = icon(R.drawable.ic_swap_vert)
    val Stop: ImageVector @Composable get() = icon(R.drawable.ic_stop_filled)
    val Toc: ImageVector @Composable get() = icon(R.drawable.ic_toc)
    val Tune: ImageVector @Composable get() = icon(R.drawable.ic_tune)
    val VerticalAlignTop: ImageVector @Composable get() = icon(R.drawable.ic_vertical_align_top)

    @Composable
    private fun icon(@DrawableRes id: Int): ImageVector = ImageVector.vectorResource(id)
}
