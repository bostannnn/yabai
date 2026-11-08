# AppAnchoredCard

A small card that opens at something on the page rather than at an edge: a word you selected in a book (the translation card). `surface-container-high`, the theme's card radius, `inset` 20, a soft shadow; at most 380 wide, never wider than the window less two gutters.

- Kit: `AppAnchoredCard(anchor, onDismissRequest)`; where it goes is the pure `placeAnchoredCard` (tested).
- **Under the thing, 12 below it**, centred on it; over it when there isn't room below; always inside the gutter. Without an anchor it sits in the middle.
- **No scrim**: what it points at stays readable. A tap anywhere else or Back closes it.
- **Pass-through** (`dismissOnOutsideTap = false`), for a card that follows something the page owns, like a text selection: touches outside the card go to the page, and the page's own tap closes it. A drag that misses a selection handle, or a long press on another word, then works as on a bare page instead of closing the card.
- Opens with a fade from 0.96 (`AppMotion.spatial`), closes the same way.
- For more than a glance (a passage, a long answer) use the side sheet or the reader's floating panel instead; a card is for a few lines.
