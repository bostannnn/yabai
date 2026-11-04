# Journal

The journal's month or year: the period with its arrows and Month / Year, then its panels in three groups, each under an `AppGroupLabel`: **Time**, **Books** and **Taste**.

- **No "Journal" eyebrow.** The period, "October 2026", is the first content and stays as the title: it is the content, and its arrows move it.
- **Gutter 24** from Medium, 16 on phones; content top 84; bottom 24. Panels use `inset` 20 at every width, `grid-gap` 16 between panels and rows, `section-gap` 24 between groups.
- **Rows, never columns.** Where the journal is 880 wide or more, a group is rows on a 12-column grid and every row ends flush: one big panel with up to two small ones stacked beside it, the stack as tall as the big panel (or the big panel as tall as the stack). Below 880 every panel takes the full width, in the same order.
  - **Time:** the summary; then Reading days (7) beside When you read over Pages per day (5). The year: the year's Reading days (8) beside Reading time by month over Pages per month (4).
  - **Books:** What you read (7) beside Finished over Needs rating (5); then Lists you finished. The year: Finished (8) beside Needs rating (4); then Lists you finished.
  - **Taste:** Ratings (4) beside Most read (8).
- **A panel with nothing in it steps aside**, and what is left in its row widens to fill it; a row with nothing goes, and so does a group. A period with nothing at all shows one empty state.
- **The summary's rows end flush:** every figure in one row where they fit (all five on Expanded), else rows of three, two on phones, the cells of a shorter last row widening to fill it.
- **Charts** are 72 tall at least and grow with their panel; **the month's calendar fills its panel**, its days as wide as a seventh of it; its days never taller than 64; the year's months go four to a row, three on phones.
- What you read shows the longest six, then Show all; "Opened for a minute or two" closes the panel. Book rows are `AppBookRow`; Finished and Needs rating are rows of covers that scroll sideways.
