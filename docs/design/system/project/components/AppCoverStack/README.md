# AppCoverStack

A series as a thing you can see: its first three covers fanned, the name under them and how far you are ("41 · 12 read"). A row of them scrolls sideways.

- Kit: `AppCoverStack(name, line, selected, onClick, cover = { index, modifier -> ... })`; the caller draws the covers (the library's cover composable) in the modifiers it gets.
- 96 wide; the covers 64 wide, the back two turned 6° and 2° and set 20 and 10 to the end, the front one with the small cover shadow. Name 13 Medium, line 12 `on-surface-variant`. Selected: the front cover gets a 2 `primary` ring.
- Filters › Series: the series with most books, then "All 86" at the label's end opens the page.
