# AppListCover

A list's cover: its first four books' covers in a 2×2 mosaic, at a cover's 2:3 and `radius-cover`, 2 apart. A list reads apart from a book (one cover) and a series (one cover with a stack behind it).

- Kit: `AppListCover(count, cell = { index, modifier -> ... })`; the caller draws each cover (the library's cover composable) filling the modifier it gets. Fewer than four books leave the other cells in `surface-container-high`; an empty list is all four.
- Used for list tiles on the Lists screen (the grid's cover width) and the list page's header (132).
- A list that updates itself (Stalled) adds a 32 round badge with the `tune` icon at its top end, on `surface-container-high`.
