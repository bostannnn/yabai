# AppProgressBar

A thin bar: 4 tall, `primary` on `surface-container-highest`, pill ends.

- Kit: `AppProgressBar(fraction, height, progressColor, trackColor)`; a null fraction is Material's moving bar on the same track. The update download (and its row in About), a server's Sync row in Settings, first launch's sync, downloads, the journal's What you read and the EPUB contents footer use it.
- A new fraction slides there with AppMotion's spatial spring instead of jumping; a bar whose fraction stays put doesn't move.
- **A sync's bar.** Moving while its length can't be known (connecting, the book list), then filled by the work it can count: saving, the uploads, each book's reading history. The fill never goes back, even when a step is retried. The step is the row's supporting text with the bar under it, at most 320 wide as in About: "Getting the book list", "Reading history · 12 of 80 books".
- The reading card's chapter-split bar and the cover's progress strip stay their own: they draw on a cover tint and a cover.
