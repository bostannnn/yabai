# AppMenu

A dropdown anchored to its button: the sort menu, •••, a tile's actions. `surface-container-high`, 44 tall items.

- Kit: `AppMenu`, `AppMenuItem`, `AppMenuHeading`, `AppMenuChoiceItem`, `AppMenuSwitchItem`.
- An item that needs saying what it does (Lists' New: List, Smart list, Import a reading order) takes a `supporting` line under its label and grows to 56; keep that to menus whose choices make something.
- **One inset, `inset-menu` 16** (`AppMenuDefaults.horizontalPadding`), for items, headings and controls: the cover-size slider lines up with the labels.
- Corners `radius-control` 12, as the 2026-10-22 decision says for menus.
- A menu with a single item (••• on a Local library) is fine; do not pad it with disabled entries.
