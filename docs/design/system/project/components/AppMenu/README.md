# AppMenu

A dropdown anchored to its button: the sort menu, •••, a tile's actions. `surface-container-high`, 44 tall items.

- Kit: `AppMenu`, `AppMenuHeading`, `AppMenuChoiceItem`, `AppMenuSwitchItem`.
- **One inset, `inset-menu` 16** (`AppMenuDefaults.horizontalPadding`), for items, headings and controls: the cover-size segmented row lines up with the labels.
- Corners `radius-control` 12, as the 2026-10-22 decision says for menus.
- A menu with a single item (••• on a Local library) is fine; do not pad it with disabled entries.
