# AppSideSheet

The sheet for anything opened over a screen: the book page, filters, downloads, a journal day. 400 wide, 12 from the window, `radius-sheet` 28, on `surface`, shadow 16. Under 600 it becomes `AppSheet` (bottom sheet) with the same header and body.

- Kit: `AppSideSheet(onDismissRequest)`; **one width**, `sheet-width` 400, for every sheet (it takes no width). Corners 28 like dialogs and bottom sheets.
- Header: `AppPanelHeader`. Body inset 20. Section labels inside: `AppSheetLabel` (`sheet-label`, 13 SemiBold, `on-surface-variant`), 14 above, 8 to their content. A sheet with two levels (downloads: a section over its days) puts `AppGroupLabel` above `AppSheetLabel`.
- Hairlines between a sheet's sections: `AppSheetDivider`, the sheet's width less the inset on both sides, `outline-variant` at `AppDividerDefaults.alpha` (60%).
- A section's body (chips, value rows, a slider) ends with `AppSheetSectionEnd`, `grid-gap` 16, before the next divider, the same under every kind of section; nothing sits on a divider. A section that ends in a row (`AppSettingsRow`, a value row that opens a page) leaves it out: the row carries its own padding.
- A footer action is a full-width filled button with 20 around it.
