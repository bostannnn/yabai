# AppProgressBar

A thin bar: 4 tall, `primary` on `surface-container-highest`, pill ends.

- Kit: `AppProgressBar(fraction, height, progressColor, trackColor)`; a null fraction is Material's moving bar on the same track. The update download (and its row in About), first launch's sync, downloads, the journal's What you read and the EPUB contents footer use it.
- The reading card's chapter-split bar and the cover's progress strip stay their own: they draw on a cover tint and a cover.
