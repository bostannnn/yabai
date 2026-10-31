# Lists

Every list in one place, beside the sources in the rail and the drawer: the reading order you are in the middle of, then your lists, then each server's lists.

- **No screen title.** The rail says Lists; the bar holds "Search lists", New list (`add`) and a menu.
- **The card** is the reading order opened most recently that still has an unread book: what is next, the order's position and an `AppProgressBar`, and Continue. It is an `AppPanel` with `inset` 20; it is not shown when no order is under way.
- **Your lists** (on this tablet, any source): Want to read first, then Stalled (books started and not opened for 21 days, worked out on its own: the `tune` badge), then the lists you made, most recently opened first. **Kavita reading lists** follow, holding only Kavita issues; a tap opens the list in the Kavita library.
- Sections are `AppSectionHeading` on the gutter line with `section-gap` 24 between them; tiles are `AppListCover` in the library grid's columns and gap, the name at 14 SemiBold and one caption line.
- A list's page: `AppListCover` 132, the name in `display-title`, its description and "14 books · 6 read", Continue, Edit, the Order chip (Your order / Date added / Title) and Hide finished; the books are `AppBookRow`s in an `AppPanel`, numbered in Your order.
