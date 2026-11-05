# AppSearchField

The search field: 40 tall (56 in the library bar), `radius-control` 12, `surface-container-high`, a search icon, a placeholder that names what it searches, Clear once there is text, and an optional trailing slot (the connection state while a source is offline: `AppStatusPill` in the neutral tone; a tap retries).

- Kit: `AppSearchField(query, onQueryChange, placeholder, containerColor, trailingContent, onClick, onSearch)`.
- In the library bar it is centred on the bar, up to 640 (see AppTopBar). The placeholder says the place: "Search Kavita", "Search Neon Ronin", "Search shelves".
- **The library bar's field is a door, not a field**: `onClick` makes it a button that opens the search panel (`AppSearchPanel`), where you type. While a search is applied to All books (after Show all) it shows the words and Clear.
- `onSearch` is what the keyboard's search key does; without it the key only hides the keyboard.
- On a dialog, pass `AppSearchFieldDefaults.dialogContainerColor`.
