# AppListRow

A list as a row, for a page that holds many lists: a place on Lists (Kavita, Grimmory), Lists' search, and a list's place in What Lists shows. Names come first in a row; covers of story-arc lists and genre shelves look alike.

- Kit: `AppListRow(name, line, cover, progress, pinned, updatesItself, selection, onClick, onLongClick)`; `cover` draws the list's `AppListCover` 40 wide.
- `AppBookRow`'s shape: `inset` 20 at the start, 12 at the end, 76 tall at least, the cover 40 wide and 14 from the text; the name in 15 SemiBold and one line under it in 13 `on-surface-variant`, each on one line with an ellipsis.
- The line says what the list is, how big, how far you are and, for a Kavita reading list, its years: "Reading list · 9 of 22 read · 2019–2020", "Collection · 9 series", "Magic shelf · 44 books", "Shelf · 55 books · 21 read". Under a kind's tab the kind is left out.
- A reading order with something read and something left adds an `AppProgressBar` under the line, at most 220 wide.
- At the end, `on-surface-variant`: `keep` (18) when pinned, `tune` (18) when it updates itself, then the `navigate_next` chevron.
- Long-press opens the list's menu (Pin, Hide, Select several, Edit); the row takes `surface-container-highest` while the menu is open.
- Selecting several: an `AppRoundCheck` before the cover and no chevron; a picked row takes `primary-container`.
- Rows sit in an `AppPanel` with 4 above and below, no dividers; two columns of rows on Expanded, one below it.
