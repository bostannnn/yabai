# AppSettingsGroup

A labelled group of rows on a panel: the settings hub and every settings page, the source page's groups, About.

- Kit: `AppSettingsGroup(label)` holding `AppSettingsRow`s separated by `AppSettingsRowDivider()`.
- The label is `AppGroupLabel` (`group-label`: 15 SemiBold, `on-surface`, a character theme's `label-accent`), **flush with the panel's edge**, `heading-gap` 8 above the panel. A count may follow it, `heading-gap` apart, in Medium `on-surface-variant` ("Rules 1,284 match"). The theme page's Device / Light / Dark use the same label over their tiles.
- Rows: `AppSettingsRow(title, icon, supporting, selected, onClick, trailing, leading)`. Inset 20, min height 64, icon 24 then 16 to the text. One trailing thing: a chevron (`navigate_next`), a switch, a value, a button or a drag handle.
- **A row says its value.** The supporting line is the current state ("Synced 09:12", "8 in Kavita, 6 in Grimmory", "Kavita, Grimmory, Local"), not a description of what the page does.
- **A state gets the status dot.** `supportingTone = AppStatusTone.Good / Neutral / Problem` puts `AppStatusPill`'s 8 dot 8 before the line: Good in `primary` (`on-surface-selected` on a selected row), Neutral the `outline` ring, Problem the dot and the line in `error`. The hub's server rows use it.
- **One button per row.** A row that can do more puts one button (filled for the thing it is for, tonal otherwise) and a ••• `AppIconButton` whose `AppMenu` holds the rest. Something that deletes (Remove cached covers) never sits on the row; it lives in the •••.
- Dividers start under the text (60) and use `outline-variant` at 60%.
- The selected row (two panes) fills with `primary-container`; its supporting text and chevron drop to 72%.
- Use it instead of hand-made rows: the sync queue row, the reading-defaults row, the filter-chips row and first launch's choices are rows of this family.
- Drag handles lead the row (the filter chips page): `leading = { AppReorderHandleIcon(state, index, label) }`, the 24 arrow on the inset line with its grab area reaching 8 past it on each side. A row with a drag handle has no chevron; its trailing switch stays.
