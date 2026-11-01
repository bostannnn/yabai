# AppSettingsGroup

A labelled group of rows on a panel: the settings hub and every settings page, the source page's groups, About.

- Kit: `AppSettingsGroup(label)` holding `AppSettingsRow`s separated by `AppSettingsRowDivider()`.
- The label is `AppGroupLabel` (`group-label`: 15 SemiBold, `on-surface`), **flush with the panel's edge**, `heading-gap` 8 above the panel. The theme page's Device / Light / Dark use the same label over their tiles.
- Rows: `AppSettingsRow(title, icon, supporting, selected, onClick, trailing, leading)`. Inset 20, min height 64, icon 24 then 16 to the text. One trailing thing: a chevron (`navigate_next`), a switch, a value, a button or a drag handle.
- Dividers start under the text (60) and use `outline-variant` at 60%.
- The selected row (two panes) fills with `primary-container`; its supporting text and chevron drop to 72%.
- Use it instead of hand-made rows: the sync queue row, the reading-defaults row, the filter-chips row and first launch's choices are rows of this family.
- Drag handles lead the row (the filter chips page): `leading = { AppReorderHandleIcon(state, index, label) }`, the 24 arrow on the inset line with its grab area reaching 8 past it on each side. A row with a drag handle has no chevron; its trailing switch stays.
