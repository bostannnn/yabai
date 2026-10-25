# AppSectionHeading (not yet in the kit)

The heading on the page ground above a grid or a list: `section-heading` 20 Bold (18 on phones) with an optional count in 15 SemiBold `on-surface-variant`, and optional actions at the end.

- Only the library uses this heading, as `LibraryGridHeadingText` in feature:library; it moves into the kit as `AppSectionHeading(text, count, actions)` when a second screen needs it.
- It sits **on the gutter line**, flush with the grid under it, `heading-gap` 8 above it.
- Use it for the library's All books, a search's Results, and the shelf browser.
- Not inside panels (a panel's title is its own) and not in sheets (`AppPanelHeader`, then `sheet-label`s).
