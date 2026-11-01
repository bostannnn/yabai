# Library home

A source's library: the bar with its search field, the reading card, the shelf tabs and strip, then All books and the grid.

- **Gutter 24** from Medium (16 on phones): the bar's glass, the reading card, the tabs, the heading and the grid share both edges.
- Content top 84, bottom 24. The grid's gap is 16.
- All books is `AppSectionHeading`, on the gutter line, with Filters, a chip per applied value and sort at its end. Filters narrows the library by anything its books carry, places, times, themes and moods included (see Filters); there is no separate Browse page.
- **On Expanded, Filters docks** beside the library: a 400 wide panel on the right gutter from content top to content bottom, the bar, the reading card, the shelves and the grid taking the width left of it (the grid keeps the 176 minimum cover width, so it loses columns). The Filters chip shows and hides it.
- The reading card's inside inset is `inset` 20 at every width and its corners `radius-container` 20.
- No source name anywhere on the screen: the search field says it.
- **The jump rail** runs down the right edge for a sort that groups: the letters for Title, Author and Series, and for Pages the page ranges the filter's slider stops at (0, 10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1k), each where its range starts (150 holds 150 to 199), then `?` for books with no page count. Only sections with books show.
- **Sorted by Pages** (shortest first, unknown lengths last), the line under each cover gives its length instead of its progress: "212 pages", a series "212 pages · 8 issues". A series' length is all its issues together, for the sort and the Pages filter alike.
- **The card follows a reading order** when the book you read last was finished and is in the reading order under way, and that order's next book is from this library: its overline is "Next in The Long Eclipse · 7 of 14", one line under the progress names the two after it ("Then Neon Ronin #13, The Long Eclipse #3"), and its buttons are Read (or Continue) and Open list.
