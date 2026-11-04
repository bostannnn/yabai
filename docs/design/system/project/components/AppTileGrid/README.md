# AppTileGrid

A few short choices with their counts, as tiles you tap: a library, a reading status, a mood, a length. Quieter than chips and easier to hit; the counts sit under the names, so the tiles line up.

- Kit: `AppTileGrid(tiles, onToggle)` with `AppTile(label, supporting, selected)`; `more` adds a last tile ("4 more") that opens the section's page.
- A tile: at least 56 tall, `radius-control` 12, `surface-container` (a step above the sheet's `surface`), the name in 14 Medium `on-surface`, the line under it 12 `on-surface-variant` in tabular figures. Selected: `primary-container` with a 1 `primary` edge, both lines `on-primary-container`.
- Columns: 2 for 2 or 4 tiles or when a name is longer than 11 characters (Medium-paced), 3 otherwise; `grid-gap` 8 both ways. More than six choices show five and a "N more" tile with a hairline edge and its name in `primary`.
- Filters: Library, Reading status, Mood and Length. Not for long lists of names; those are `AppCheckRow`s or chips.
