# AppCheckRow

One value of a list you can pick several of: a checkbox, the name, the count at the end. With `share`, a faint bar behind the row shows how big the value is next to the biggest in its list.

- Kit: `AppCheckRow(label, count, checked, onClick, share, indent)`.
- 44 tall (40 indented), the box 20 with 5 corners, `outline` edge; checked fills `primary` with an `on-primary` check. The name in 15 Regular, the count 13 `on-surface-variant` in tabular figures at the end.
- `share` (0 to 1): a `surface-container-low` bar from the box's start, `radius-inner` 8, 6 in from the row's top and bottom, as long as that share of the row less the count's room.
- `indent`: 26 in, for a city under its country.
- Filters: Language, Format, Location; Set in with its share bars, a picked country's cities indented under it.
