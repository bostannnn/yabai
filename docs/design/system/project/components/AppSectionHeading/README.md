# AppSectionHeading

The heading on the page ground above a grid or a list: `section-heading` 20 SemiBold (18 on phones) with an optional count in 15 Regular `on-surface-variant`, and optional actions at the end of its row.

- Kit: `AppSectionHeading(text, count)`; the caller puts actions (Filters, sort, Hide finished) after it in the same row.
- It sits **on the gutter line**, flush with the grid under it, `heading-gap` 8 above it.
- Use it for the library's All books, a search's Results, the shelf browser, and the Lists screen's sections (Your lists, Kavita reading lists) and a list page's books.
- Not inside panels (a panel's title is its own) and not in sheets (`AppPanelHeader`, then `sheet-label`s).
