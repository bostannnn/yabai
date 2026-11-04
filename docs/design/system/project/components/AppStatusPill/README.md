# AppStatusPill

A source's or a queue's state as a dot and a word, with no container: an 8 dot, 8 before the label in 14 Medium. Good is the dot in `primary` with `on-surface` text; neutral (Checking, Waiting) the dot in `outline` with `on-surface-variant` text; a problem the dot and the text in `error`. 28 tall, so it lines up with the controls beside it.

- Kit: `AppStatusPill(label, tone)` with `AppStatusTone.Good`, `Neutral` or `Problem`; the kit picks the colours.
- The source page and the sync page use it.
