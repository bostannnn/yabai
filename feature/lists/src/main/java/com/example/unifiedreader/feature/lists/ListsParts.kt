package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.library.libraryBookTitle
import com.example.unifiedreader.ui.components.AppBookRow
import com.example.unifiedreader.ui.components.AppBookRowDefaults
import com.example.unifiedreader.ui.components.AppBookRowLine
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppChip
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppListCover
import com.example.unifiedreader.ui.components.AppListRow
import com.example.unifiedreader.ui.components.AppListRowDefaults
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppMenuItem
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppProgressBar
import com.example.unifiedreader.ui.components.AppRoundCheck
import com.example.unifiedreader.ui.components.AppSectionHeading
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTabPill
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/** A list's cover: its first four books in its order, or the copy of each kept for a book that has left the library. */
@Composable
internal fun ListCoverOf(
    list: UserList,
    itemsById: Map<String, ReadableItem>,
    cover: ListCover,
    modifier: Modifier = Modifier,
) {
    val items = remember(list, itemsById) {
        list.sortedEntries().take(4).map { entry -> itemsById[entry.itemId] ?: entry.asReadableItem() }
    }
    AppListCover(count = items.size, modifier = modifier) { index, cellModifier -> cover(items[index], cellModifier) }
}

/**
 * A list as a cover tile: its [AppListCover] (with the `tune` badge when it updates itself, a
 * round check while selecting), its name and [caption]. A long press opens its menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ListTile(
    list: UserList,
    caption: String,
    itemsById: Map<String, ReadableItem>,
    cover: ListCover,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.coverCornerRadius))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "More for this list"),
        verticalArrangement = Arrangement.spacedBy(ListTileGap),
    ) {
        Box {
            ListCoverOf(list, itemsById, cover)
            if (list.isBuiltFromRules) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -ListBadgeInset, y = ListBadgeInset)
                        .size(ListBadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = AppIcons.Tune, contentDescription = "Updates itself", modifier = Modifier.size(AppListRowDefaults.markSize))
                }
            }
            if (selected != null) {
                AppRoundCheck(
                    checked = selected,
                    ringColor = MaterialTheme.colorScheme.onSurface,
                    ringBackdrop = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.TopStart).offset(x = ListBadgeInset, y = ListBadgeInset),
                )
            }
        }
        Column {
            Text(
                text = list.name,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A list as an [AppListRow], its cover drawn from the library's covers. */
@Composable
internal fun ListRow(
    list: UserList,
    line: String,
    itemsById: Map<String, ReadableItem>,
    cover: ListCover,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    pinned: Boolean = false,
    selected: Boolean? = null,
    pressed: Boolean = false,
    highlight: String? = null,
) {
    AppListRow(
        name = list.name,
        line = line,
        cover = { coverModifier -> ListCoverOf(list, itemsById, cover, coverModifier) },
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "More for this list",
        progress = progress,
        pinned = pinned,
        updatesItself = list.isBuiltFromRules,
        selected = selected,
        pressed = pressed,
        highlight = highlight,
        modifier = modifier,
    )
}

/**
 * A list's menu, from a long press on its tile or row: Pin to the top (or Unpin), Hide from
 * Lists (or Show again), Select several, and Edit, greyed with its reason for a list you can't change.
 */
@Composable
internal fun ListMenu(
    list: UserList,
    preference: ListPreference,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPin: (Boolean) -> Unit,
    onHide: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    val place = list.server?.sourceType?.displayName()
    AppMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (preference.isPinned) {
            AppMenuItem(
                text = { Text("Unpin") },
                leadingIcon = { Icon(imageVector = AppIcons.KeepOff, contentDescription = null) },
                onClick = { onDismiss(); onPin(false) },
            )
        } else {
            AppMenuItem(
                text = { Text("Pin to the top") },
                supporting = if (place != null) "First on All and on $place" else "First on All and on Your lists",
                leadingIcon = { Icon(imageVector = AppIcons.Keep, contentDescription = null) },
                onClick = { onDismiss(); onPin(true) },
            )
        }
        if (preference.hidden) {
            AppMenuItem(
                text = { Text("Show again") },
                leadingIcon = { Icon(imageVector = AppIcons.VisibilityOff, contentDescription = null) },
                onClick = { onDismiss(); onHide(false) },
            )
        } else {
            AppMenuItem(
                text = { Text("Hide from Lists") },
                supporting = "Stays on ${place ?: "this device"}. Hidden brings it back.",
                leadingIcon = { Icon(imageVector = AppIcons.VisibilityOff, contentDescription = null) },
                onClick = { onDismiss(); onHide(true) },
            )
        }
        AppMenuItem(
            text = { Text("Select several") },
            leadingIcon = { Icon(imageVector = AppIcons.SelectAll, contentDescription = null) },
            onClick = { onDismiss(); onSelect() },
        )
        val readOnly = when {
            list.kind == UserListKind.Stalled -> "Works itself out from your reading"
            !list.isEditable -> "Someone else's: read-only"
            else -> null
        }
        AppMenuItem(
            text = { Text("Edit") },
            supporting = readOnly,
            enabled = readOnly == null,
            leadingIcon = { Icon(imageVector = AppIcons.Edit, contentDescription = null) },
            onClick = { onDismiss(); onEdit() },
        )
    }
}

/**
 * A place's tool row: its kinds as tab pills with counts (when it holds more than one), then Sort.
 * On Compact, Sort is an icon button and the kinds scroll.
 */
@Composable
internal fun ListsToolRow(
    page: ListsPlacePage,
    onChoiceChange: (ListsPlaceChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    val choice = page.choice
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.headingGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ListTabGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            page.kinds.forEach { (kind, count) ->
                AppTabPill(
                    label = kind.label,
                    selected = kind == choice.kind,
                    onClick = { onChoiceChange(choice.copy(kind = kind)) },
                    count = count.takeIf { kind != ListsKind.All || !compact },
                )
            }
        }
        Box {
            if (compact) {
                AppIconButton(onClick = { sortOpen = true }) {
                    Icon(imageVector = AppIcons.SwapVert, contentDescription = "Sort: ${choice.sort.label}")
                }
            } else {
                AppChip(
                    selected = false,
                    onClick = { sortOpen = true },
                    label = { Text(choice.sort.label) },
                    leadingIcon = { Icon(imageVector = AppIcons.SwapVert, contentDescription = null, modifier = Modifier.size(AppListRowDefaults.markSize)) },
                )
            }
            AppMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                page.sorts.forEach { sort ->
                    AppMenuChoiceItem(
                        text = sort.label,
                        selected = sort == choice.sort,
                        onClick = {
                            sortOpen = false
                            onChoiceChange(choice.copy(sort = sort))
                        },
                    )
                }
            }
        }
    }
}

/** A section's heading, with See all N at its end when it opens a place. */
@Composable
internal fun ListsHeading(
    title: String,
    count: Int?,
    modifier: Modifier = Modifier,
    seeAll: (() -> Unit)? = null,
    seeAllCount: Int = 0,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppSectionHeading(text = title, count = count?.toString(), modifier = Modifier.weight(1f))
        if (seeAll != null) {
            AppTextButton(onClick = seeAll) {
                Text(text = "See all $seeAllCount", fontWeight = FontWeight.SemiBold)
                Icon(imageVector = AppIcons.NavigateNext, contentDescription = null, modifier = Modifier.size(AppListRowDefaults.markSize))
            }
        }
    }
}

/**
 * A place's end: Finished and Hidden, each a row that opens its lists in place, saying the first
 * few names. Shows only the ones that hold something.
 */
@Composable
internal fun ListsFolds(
    finished: List<UserList>,
    hidden: List<UserList>,
    openFold: ListsFold?,
    onToggle: (ListsFold) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppPanel(modifier = modifier.fillMaxWidth()) {
        if (finished.isNotEmpty()) {
            AppSettingsRow(
                title = "Finished",
                supporting = "Every book read: " + namesLine(finished),
                icon = AppIcons.Check,
                value = finished.size.toString(),
                selected = openFold == ListsFold.Finished,
                onClick = { onToggle(ListsFold.Finished) },
            )
        }
        if (finished.isNotEmpty() && hidden.isNotEmpty()) AppSettingsRowDivider()
        if (hidden.isNotEmpty()) {
            AppSettingsRow(
                title = "Hidden",
                supporting = namesLine(hidden),
                icon = AppIcons.VisibilityOff,
                value = hidden.size.toString(),
                selected = openFold == ListsFold.Hidden,
                onClick = { onToggle(ListsFold.Hidden) },
            )
        }
    }
}

/** Which of a place's folds is open. */
enum class ListsFold { Finished, Hidden }

private fun namesLine(lists: List<UserList>): String {
    val names = lists.take(3).joinToString(", ") { it.name }
    return if (lists.size > 3) "$names and ${lists.size - 3} more" else names
}

/** A book a search found in your lists: its cover, title, author or series, and the lists holding it. */
@Composable
internal fun FoundBookRow(
    book: ListsFoundBook,
    itemsById: Map<String, ReadableItem>,
    cover: ListCover,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = itemsById[book.entry.itemId] ?: book.entry.asReadableItem()
    AppBookRow(
        title = itemsById[book.entry.itemId]?.libraryBookTitle() ?: book.entry.title,
        cover = { cover(item, Modifier.width(AppBookRowDefaults.coverWidth).aspectRatio(2f / 3f)) },
        onClick = onClick,
        contentPadding = AppListRowDefaults.contentPadding,
        modifier = modifier,
        supporting = {
            book.entry.primaryText?.let { AppBookRowLine(text = it) }
            AppBookRowLine(text = book.whereLine(), color = MaterialTheme.colorScheme.onSurface)
        },
        trailing = {
            Icon(imageVector = AppIcons.NavigateNext, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

/** The reading order under way: what is next, how far you are, and Continue. A tap elsewhere opens the list. */
@Composable
internal fun ListUnderWayCard(
    underWay: UserListUnderWay,
    cover: ListCover,
    onContinue: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val next = underWay.next
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    AppPanel(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AppLayout.inset),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(UnderWayCoverWidth)) {
                cover(next.item ?: next.entry.asReadableItem(), Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UnderWayLineGap)) {
                Text(
                    text = "Reading order · book ${underWay.position} of ${underWay.count}",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = underWay.list.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Next: ${next.item?.libraryBookTitle() ?: next.entry.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(UnderWayLineGap))
                AppProgressBar(
                    fraction = underWay.finishedCount.toFloat() / underWay.count.coerceAtLeast(1),
                    modifier = Modifier.fillMaxWidth(),
                )
                // On a phone Continue goes under the text, which needs the whole width.
                if (compact) {
                    Spacer(modifier = Modifier.height(AppLayout.headingGap))
                    AppButton(onClick = onContinue) {
                        Text(text = "Continue", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (!compact) {
                AppButton(onClick = onContinue) {
                    Text(text = "Continue", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private val ListTileGap: Dp = 6.dp
private val ListBadgeInset: Dp = 8.dp
private val ListBadgeSize: Dp = 32.dp
private val ListTabGap: Dp = 4.dp
private val UnderWayCoverWidth: Dp = 88.dp
private val UnderWayLineGap: Dp = 4.dp
