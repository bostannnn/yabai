# Settings

The settings hub and its pages, on the frame in Layout and spacing.

- **No "Settings" title.** The hub starts with its first group, Appearance, at content top (84). On Compact and Medium the bar holds only the menu button; on Expanded there is no bar content at all.
- **Expanded: two panes on one top line.** The hub is the 320 list pane on the gutter; the open page follows 24 later and is at most 800 wide. Both start at content top, so the hub's first group label and the page's first line share a top edge.
- **Gutter 24** from Medium, 16 on phones; the panes are a gutter apart. Group labels sit on the panel's edge, and so does a page's one-line intro.
- A page opened on Compact or Medium shows Back and its name in the bar, and the bar has no glass until something scrolls under it.
- The open page is marked by the selected hub row (`primary-container`); the page itself carries no title.

## The hub

- **Every row says its value**, not what its page is for: a server's row its state ("Synced 09:12", "Synced yesterday", "Offline", "Checking", "Syncing", "Not connected") after the status dot (`AppSettingsRow(supportingTone)`: Good while connected, Neutral otherwise); Filter chips how many each library shows ("8 in Kavita, 6 in Grimmory"); Source order the order ("Kavita, Grimmory, Local"). Nothing claims which source opens on launch: launch restores where you were.
- **Groups:** Appearance (Theme); Sources (one row per server, then Reading progress sync, which is about those servers); Library (Filter chips, Tags, Source order); About (Version, Check for updates daily, Open-source licenses).

## A server's page

Connected, top to bottom:

- **Server.** One row: the address as the title, "account · backup address" under it, then the `AppStatusPill` (Connected, Offline, Checking) and a tonal Edit that opens the form in place (Done closes it). A connection or sync error shows as one `error` line above the group. There is no second Server group at the bottom.
- **Library.** Sync: "Synced today, 09:12" (the step and an `AppProgressBar` while it runs), a filled **Sync now**, and on Kavita a ••• with Fast sync. Cached covers: its line, a tonal **Preload** (Cancel while it runs), and a ••• with Remove cached covers. One button per row; the destructive one only in •••.
- **Failed covers** are one row under Cached covers, `error` icon: "3 covers didn't download", the first two titles "and 1 more", and a text **Retry**. No raw error lines.
- **Reading defaults.** One row per library: Left to right / Right to left, then Paged / Scroll (`AppSegmentedRow`s beside the name from 640 wide, stacked under it below). One line under the group, flush with the panel's edge like the group label: "Comics in each library open this way, unless you picked something else for a book or a series in the reader."

Not connected, the page is the sign-in form alone.

## Reading progress sync

What waits to upload, and why. With nothing waiting, one row says "Everything is uploaded".

- **Waiting uploads.** An `AppStatusPill` with how many and since when (`error-container` once one failed), "Last tried …", then Retry now, the page's one filled button. Then a group per server, a book's uploads of one kind on one row, "Waiting" or "Failed" at the end, and the server's last error under the group.
- **Books no longer in your library.** An upload whose book is no longer in the app's copy of its server's library can't go anywhere: the server removed the book, so no retry helps. Those leave the count and the server groups for their own group, last on the page, "No longer in your library": one row per book, "Grimmory · Progress, 2 reading sessions", nothing at the end. Under it one line says why, and a tonal **Keep on this device** stops the uploads and keeps everything: the journal keeps the sessions and the entry, the progress stays, and the app stops trying. Nothing is deleted, so it needs no confirm. A server whose library the app holds no books from (signed out, never synced) proves nothing, so its uploads stay waiting.
- **The hub row**, last in Sources, says what is waiting ("1 item waiting to upload", in `error` once one failed); with only gone books, "1 book no longer in your library"; otherwise "Up to date".
