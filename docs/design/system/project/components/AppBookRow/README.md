# AppBookRow

One row for a book in a list: a 40 wide cover, the title at 15 SemiBold, one line under it at 13 `on-surface-variant`, an optional trailing value or action.

- Kit: `AppBookRow(title, cover, onClick, contentPadding, supporting, trailing)` and `AppBookRowLine(text, color, maxLines)` for the usual line; `AppBookRowDefaults.coverWidth` 40.
- The journal's What you read, a journal day's books and the downloads list all use it.
- 14 from the cover to the text; the caller pads the row to its surface's inset. Progress, when shown, is an `AppProgressBar` under the line.
