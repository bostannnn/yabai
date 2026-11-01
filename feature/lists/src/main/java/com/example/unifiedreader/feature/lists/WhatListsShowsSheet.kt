package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSheetDivider
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppSideSheet
import com.example.unifiedreader.ui.components.AppSwitch
import com.example.unifiedreader.ui.components.AppTextButton

/** How many hidden lists the sheet names before See all. */
private const val HiddenShownFirst = 5

/**
 * What Lists shows, from the bar's menu: per server, other people's lists, Kavita's collections
 * and empty lists, each a switch saying how many it covers; what finished lists do; then the lists
 * hidden one by one, each with Show. All of it stays on this device.
 */
@Composable
fun WhatListsShowsSheet(
    catalog: ListsCatalog,
    onShowingChange: (ListsShowing) -> Unit,
    onShowAgain: (UserList) -> Unit,
    onDismiss: () -> Unit,
) {
    val showing = catalog.showing
    var seeAllHidden by remember { mutableStateOf(false) }
    AppSideSheet(onDismissRequest = onDismiss) {
        AppPanelHeader(
            title = "What Lists shows",
            subtitle = "On this device only. Nothing changes on the servers.",
            onClose = { dismiss() },
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            catalog.servers.forEach { source ->
                val lists = catalog.listsOf(ListsFilter.Server(source))
                AppSheetLabel(text = source.displayName())
                val others = lists.count { it.server?.isOwned == false }
                if (others > 0) {
                    SwitchRow(
                        title = if (source == SourceType.KAVITA) "Lists other people share" else "Public shelves of other people",
                        supporting = if (source == SourceType.KAVITA) "$others promoted by someone else" else countLabel(others, "shelf", "shelves"),
                        checked = source !in showing.hideOthers,
                    ) { shown -> onShowingChange(showing.copy(hideOthers = showing.hideOthers.toggled(source, hidden = !shown))) }
                }
                val collections = lists.count { it.kind == UserListKind.ServerCollection }
                if (collections > 0) {
                    SwitchRow(
                        title = "Collections",
                        supporting = countLabel(collections, "group of series", "groups of series"),
                        checked = !showing.hideCollections,
                    ) { shown -> onShowingChange(showing.copy(hideCollections = !shown)) }
                }
                val empty = lists.count { it.entries.isEmpty() }
                if (empty > 0) {
                    SwitchRow(
                        title = if (source == SourceType.GRIMMORY) "Empty shelves" else "Empty lists",
                        supporting = if (empty == 1) "1 holds no books" else "$empty hold no books",
                        checked = source !in showing.hideEmpty,
                    ) { shown -> onShowingChange(showing.copy(hideEmpty = showing.hideEmpty.toggled(source, hidden = !shown))) }
                }
                AppSheetDivider()
            }
            AppSheetLabel(text = "Everywhere")
            AppSettingsRow(title = "Finished lists", supporting = "Every book read")
            AppSegmentedRow(modifier = Modifier.fillMaxWidth().padding(horizontal = AppLayout.inset)) {
                ListsFinished.entries.forEach { mode ->
                    AppSegment(
                        selected = showing.finished == mode,
                        onClick = { onShowingChange(showing.copy(finished = mode)) },
                        label = { Text(mode.label, maxLines = 1) },
                    )
                }
            }
            val hidden = catalog.listsOf(ListsFilter.All).filter { catalog.preference(it).hidden }
            if (hidden.isNotEmpty()) {
                AppSheetDivider()
                AppSheetLabel(text = "Hidden · ${hidden.size}")
                val named = if (seeAllHidden) hidden else hidden.take(HiddenShownFirst)
                named.forEach { list ->
                    val place = list.server?.sourceType?.displayName() ?: "This device"
                    AppSettingsRow(
                        title = list.name,
                        supporting = "$place · ${list.rowLine(catalog.progressByItemId).replaceFirstChar(Char::lowercaseChar)}",
                        showChevron = false,
                        trailing = {
                            AppTextButton(onClick = { onShowAgain(list) }) {
                                Text(text = "Show", fontWeight = FontWeight.SemiBold)
                            }
                        },
                    )
                }
                if (!seeAllHidden && hidden.size > HiddenShownFirst) {
                    AppTextButton(onClick = { seeAllHidden = true }, modifier = Modifier.padding(horizontal = AppLayout.headingGap)) {
                        Text(text = "See all ${hidden.size} hidden", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, supporting: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    AppSettingsRow(
        title = title,
        supporting = supporting,
        onClick = { onChange(!checked) },
        showChevron = false,
        trailing = { AppSwitch(checked = checked, onCheckedChange = null) },
    )
}

private fun countLabel(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"

private fun Set<SourceType>.toggled(source: SourceType, hidden: Boolean): Set<SourceType> = if (hidden) this + source else this - source
