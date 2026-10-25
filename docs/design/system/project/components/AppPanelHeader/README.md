# AppPanelHeader

The one header for every sheet and panel: an optional Back, the title in `panel-title` (20 Bold), an optional subtitle (`label-medium`, `on-surface-variant`), actions, Close.

- Kit: `AppPanelHeader(title, subtitle, onBack, onClose, backContentDescription, closeContentDescription, actions)`. Padding 20 at the start (8 when Back is there), 8 at the end, 14 above, 6 below.
- **Every sheet uses it**: filters and a filter's facet page, downloads, a journal day, the book page and the reader panels, so Back and Close sit in the same place on every sheet.
- A sheet whose content starts with its own title (the book page, a journal day) passes no `title`: the header is just Back and Close. On the book page it is the first row of the cover-tinted top, in the tint's content colour.
- A subtitle says something new ("Showing all 35"); it never repeats the book title the reader bar shows.
