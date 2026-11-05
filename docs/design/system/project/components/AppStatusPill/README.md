# AppStatusPill

A source's or a queue's state as a dot and a word, with no container: an 8 dot, 8 before the label in 14 Medium. Good is the dot in `primary` with `on-surface` text; neutral (Checking, Offline, Waiting, Not connected) a ring in `outline` (1.5 wide) with `on-surface-variant` text, because in some themes (Blueprint) `outline` is close to `primary` and a filled dot would read as good; a problem the dot and the text in `error`. 28 tall, so it lines up with the controls beside it.

- Kit: `AppStatusPill(label, tone)` with `AppStatusTone.Good`, `Neutral` or `Problem`; the kit picks the colours.
- The source page (at the end of its Server row) and the sync page use it. The hub's server rows use the same dot in front of their line, through `AppSettingsRow(supportingTone)`, not a pill of their own.
