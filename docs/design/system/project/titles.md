# Names and titles

The rail and the drawer already say where you are, so a screen never repeats its own name. The library dropped its "Kavita" and "Grimmory" headers in 9.11; every other screen follows the same rule.

## The rule

1. **No screen-name titles in content.** Settings, Journal and a source's library start with their content: the first group, the period, the reading card. No "Settings" title, no "Journal" eyebrow.
2. **The bar names only a page you went into.** A page opened from another screen shows Back and its name in the bar: "Theme", "Kavita", "Filter chips", "In progress". A top-level screen's bar has no name: the library's bar holds its search field ("Search Kavita"), the journal's and the settings hub's bars hold only the menu button on Compact and Medium, and nothing at all beside the rail on Expanded.
3. **A title that is the content stays.** A series' name, a Kavita list's name, the journal's period ("October 2026"), a list's name on its page, a book's title on its page and a first-launch question ("Where are your books?") are content, set in `display-title` (the period in `period-title`).
4. **Say it once per surface.** A sheet's header names the sheet ("Filters", "Downloads"); nothing under it repeats that. A reader panel's header does not repeat the book title the reader bar shows. The shelf browser's tabs name the shelf and count it, so its bar does not.

## Examples

| Screen | Bar | First content |
|---|---|---|
| Library home | ☰ · Search Kavita · Downloads · ••• | the reading card |
| Series | ← · Search Neon Ronin · ••• | the series name in `display-title`, its summary |
| Shelf browser | ← · Search shelves · ••• | the shelf tabs (In progress 3 · Up next 1 · Finished 12) |
| Journal | ☰ (Compact, Medium) | ‹ October 2026 › with Month · Year |
| Lists | ☰ · Search lists · New list · ••• | the reading order under way, else Your lists |
| A list | ← · Search The Long Eclipse · ••• | the list's name in `display-title` |
| Settings hub | ☰ (Compact, Medium) | the Appearance group |
| Settings, Expanded | none | hub: the Appearance group; page: its first line, at the same top |
| Settings page (Compact, Medium) | ← Theme | the page's first line |
| Book page, filters, downloads | (sheet) | `AppPanelHeader`: Close, and the title where the sheet has one |
| Drawer | — | the sources, under a `group-label` "Sources" |
