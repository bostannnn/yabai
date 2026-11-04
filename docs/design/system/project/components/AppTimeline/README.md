# AppTimeline

Time as one line down the start: a dot per pile (a century or a decade) in time order, and the named periods (Russian Revolution, World War II, Cold War) placed where they fall, smaller and in italics.

- Kit: `AppTimeline(entries, onToggle)` with `AppTimelineEntry(label, count, selected, period)`.
- Rows 40 tall; the line 2 `outline-variant` in a 20 wide rail; a pile's dot 10 `outline-variant`, a period's 8 `outline`, both ringed in the ground. Selected: the dot 14 `primary`, the label SemiBold. Label 14 Medium (a period 13 italic `on-surface-variant`), the count at the end.
- Filters › When, in the panel itself. A whole century with no pile of its own (20th century) is a stop just before its first decade; a period with no known years goes after the piles.
