# Layout and spacing

Every screen is built on the same frame. The numbers are dp in the app (px in the previews) and live in code as `AppLayout` in `core:ui-components` (`appGutter()` for the current window's gutter); feature code reads them from there and never writes its own. `AppLayoutRuleTest` checks the gutter, the one sheet width, and that no screen starts its content at a top of its own; `DesignSystemRuleTest` keeps `AppScreenTitle` to content titles and lets literal dp in paddings and corner radii only go down, file by file, from a recorded baseline.

## Window classes

| Class | Width | Gutter | Navigation | Sheets |
|---|---|---|---|---|
| Compact | under 600 | `gutter-compact` 16 | drawer | bottom sheets |
| Medium | 600 to 839 | `gutter` 24 | drawer | side sheets, `sheet-width` 400 |
| Expanded | 840 and up | `gutter` 24 | rail (88) when the height is not Compact | side sheets; settings becomes two panes |

The emulator's 800 dp portrait is Medium; the tablet's 914 dp portrait and its landscape are Expanded.

## The frame

1. **Gutter.** Content starts `gutter` from the window edge, or from the rail's hairline when the rail shows, and ends `gutter` from the other edge. This holds for the library grid, the reading card, the shelf strip, settings, the journal, the shelf browser and first launch. A centred column with a maximum width (settings 800, journal 1140, first launch 560) keeps the gutter as its minimum. Large covers, the default size, are 176 wide at least (not 180), so the library grid keeps its columns with the 24 gutter: 4 at 800 portrait, 6 beside the rail in landscape, where the reading card sits beside the shelves.
2. **Top bar.** `bar-top` 12 under the status bar, `bar-height` 56, its glass edge on the gutter line, so the bar's sides line up with the panels below it. Its icons sit 8 inside the glass. Every bar icon button is the kit's default size.
3. **Content top.** The first content starts `content-top` 8 below the bar's 76 inset, on every screen: 84 below the status bar. No screen adds its own 4, 12 or 16.
4. **Sections.** `section-gap` 24 between sections. A heading or group label is `heading-gap` 8 above what it heads. Cover tiles and side-by-side panels are `grid-gap` 16 apart.
5. **Bottom.** `content-bottom` 24 after the last content, above the navigation bar inset.

## Alignment

- A heading sits on the left edge of the thing it heads: a section heading on the gutter line above a grid; a group label on the panel's edge, not indented. Text that sits outside a panel (a page's one-line intro, a footnote) sits on the same edge.
- Inside a panel, card, sheet or row the inset is `inset` 20, at every width. Dialog cards and empty states use `inset-dialog` 24. Menus use `inset-menu` 16 for every item, heading and control in them, so a segmented row or a slider in a menu lines up with the menu's labels.
- In a row with a leading icon, text starts at inset + icon + icon-gap (60); the divider between rows starts there too.
- **Two panes** (settings on Expanded): the list pane is `list-pane-width` 320 on the gutter; the page pane follows after `gutter`. Both start at content top, so the list's first group label and the page's first line share one top edge. Neither pane has a title above it.

## Surfaces

| Surface | Kit | Width | Header | Body inset |
|---|---|---|---|---|
| Panel / settings group | `AppPanel`, `AppSettingsGroup` | the column | a `group-label` above it, flush | 20 |
| Side sheet (book page, filters, downloads, journal day) | `AppSideSheet` | `sheet-width` 400, `sheet-margin` 12 from the window | `AppPanelHeader` (title 20 SemiBold, optional subtitle, Back and Close in the same places on every sheet) | 20 |
| Bottom sheet (any sheet under 600) | `AppSheet` | full | the same `AppPanelHeader` under the handle | 20 |
| Dialog | `AppDialogCard`, `AppListDialog` | up to `dialog-width` 380 | the card's title | 24 |
| Menu | `AppMenu` | content, up to 300 | `AppMenuHeading` per group | 16 |
| Reader panel | `AppFloatingPanel` + `AppPanelHeader` | 360 to 400, between the reader's bars | `AppPanelHeader`; no subtitle that repeats the book title the bar already shows | 20 |

Reader bars and the page frame are their own system (UI plan 8.7 and 9.6): they float 8 (Compact) or 12 from the window over the page and do not take the gutter.

## Rows

One row family, `AppSettingsRow`: leading icon (optional), title in `row-title`, a supporting line in `body-medium` `on-surface-variant`, and one trailing thing (a chevron, a switch, a value, a button, a drag handle). Minimum height `row-height` 64; a value row inside a sheet (a filter facet, a font) is 52. The chevron is always `NavigateNext`. A list of books (journal, downloads, a journal day) uses `AppBookRow`: a 40 wide cover, the title at 15 SemiBold, one line under it at 13.

## What a screen may not do

- Write a padding, gap or width in dp that is not one of the tokens above.
- Draw its own header row, close button, section label, divider, status pill, progress bar or book row: each has a kit component.
- Indent a heading past the edge of what it heads.
- Start lower or higher than content top, or show the bar's glass before anything is under it.
