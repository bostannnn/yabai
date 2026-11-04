# AppSegmentedRow

Two or three choices on one track: a `surface-container-low` track at `radius-control` 12, 4 padding, 36 tall segments at `radius-inner` 8 (12 less the padding); the selected one is `primary-container`. A field, not a pill: pills are for things you tap to act or pick.

- Kit: `AppSegmentedRow` + `AppSegment`.
- Any setting with two or three values is one row of these (label + segmented row); lists are only for fonts and chapters. More values than three, ordered by size, take a stepped `AppSlider` with the value's name at the label's end (the sort menu's cover size).
- Month / Year in the journal, reader settings.
