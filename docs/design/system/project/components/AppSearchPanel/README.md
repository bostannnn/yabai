# AppSearchPanel

Search in the middle of the screen, like Spotlight: the library bar's field grows into a panel over the dimmed library, and you type there.

- Kit: `AppSearchPanel(query, onQueryChange, placeholder, onDismissRequest, anchor, onSearch, content, footer)`. It is its own window, like a sheet.
- **Where**: its top and its centre are the bar field's (`anchor`), so it opens out of the field; 680 wide at most, `gutter` from the window's sides. It ends `gutter` above the keyboard or the navigation bar; with the keyboard up it holds what fits and scrolls the rest.
- **Shape**: `surface-container`, `radius-sheet` 28, the sheet shadow. The field row is 64 (the field without its own box or Clear; Close sits at its end), then an `AppSheetDivider`, the results, and an optional footer under another divider. While the keyboard is up the footer steps aside so the results get the room.
- **Leaving**: a tap outside, Back, or Close in the field row puts the search back as it was when the panel opened. Opening a result or Show all closes it.
- **The library's results** (`LibrarySearchPanel`):
  - **The tags a search names** first: "Filter by" in `on-surface-variant`, then an `AppChip` per tag, genre or mood the words name, its section quieter and its count after it ("Set in Japan 96"), whole names first, on one line that scrolls sideways. Picking one applies it to All books as a filter and closes the panel.
  - **Books** (`AppSheetLabel` with the count), then up to 50 `AppBookRow`s. A row says why it matched when it isn't the title or a name: the sentence of the description that holds the words, marked with `primary-container`, two lines at most, with "In the description"; or, for a tag or genre, one line named as Filters names it ("Set in **Japan**"). Read state at the row's end (Finished, 12%).
  - **The first row is highlighted** (`surface-selected`, `radius-control`): the keyboard's search key opens it.
  - **Footer**: "Show all 446 in All books" with a chevron, in `primary`: closes the panel and leaves the search on All books, as covers. The bar's field then shows the words; tapping it opens the panel again.
  - Nothing typed: one quiet line saying what search looks in. No match: "No books match “…”".
