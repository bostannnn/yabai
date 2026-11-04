# AppTopBar

The floating top bar every screen shares: 56 tall, 12 below the status bar, its glass edge on the gutter line.

- Kit: `AppTopBar(surfaceVisible, onDrawerClick, ...)` in `AppTopBar.kt`; `AppTopBarDefaults.contentInset` (76) is the inset content starts under, plus `content-top` 8.
- **At rest it has no glass.** `surfaceVisible` turns true only once content has scrolled under the bar, on every screen: settings and journal report it with `onScrolledChanged`, the shelf browser through `LibraryShelfBrowserViewState.scrolled`.
- **Its sides follow the gutter** (`appGutter()`): 16 on Compact, 24 from Medium.
- Contents by screen: a source's library has the menu, the search field ("Search Kavita"), Downloads and •••. A top-level screen without search (journal, settings hub) has only the menu, and only while the drawer is the navigation. A page you opened has Back and its name. See Names and titles.
- Icon buttons are the kit's default size; do not force 36.
- The glass edge is `outline-variant` at 28%; a character theme draws it in its `bar-edge` (see Colour).
