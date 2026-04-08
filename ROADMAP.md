# Unified Reader Roadmap

This file is the product backlog for future app work. It should stay focused on user-facing improvements, not only technical milestones.

## Product Direction

Build a reader that feels strong in three areas:

- library browsing that helps users pick what to read next
- local and offline reading that works reliably
- progress and state management that stays correct across sources

## Current Priorities

### 1. Shelves on every main screen

Add lightweight shelves that make the app feel alive and reduce taps.

- `In Progress` shelf for books and comics
- `Next In Series` shelf for comics
- show these on each relevant source/home screen, not only in one place
- opening a shelf item should resume directly into the reader when possible
- shelf ordering should be driven by recent activity and unfinished status

Why this matters:

- gives users an immediate "what should I read now?" entry point
- makes the library feel personalized instead of purely browse-based
- surfaces series continuity for comics without needing manual search

### 2. Local downloading for comics

Bring comics downloads to parity with the book flow.

- allow remote comics to be downloaded for offline reading
- store download state clearly in the library UI
- support deleting the local copy without losing the remote item
- keep local reading progress when switching between streamed and downloaded copies
- handle interrupted downloads and retry cleanly

Why this matters:

- comics are a primary use case and need first-class offline support
- users should not have to re-import files manually just to read offline

### 3. Mark items as read or unread

Add explicit read-state controls beyond inferred progress.

- action on book/comic cards and details views
- allow both `Mark as read` and `Mark as unread`
- update local progress immediately
- sync the read-state change back to connected servers when supported
- use the read state in shelves, filters, and sorting

Why this matters:

- users often want to manage their library manually
- read/unread is needed before building better shelves and discovery

### 4. Improve library progress visuals

Make files with progress much easier to recognize directly in the library UI.

- add stronger visual indicators for items that are in progress
- show progress consistently in both grid and detailed library views
- surface useful status at a glance, such as page, percent, or finished state
- make active reading items stand out without overwhelming unread items
- ensure the visual treatment works for books and comics

Why this matters:

- users should be able to spot partially read items immediately
- the library should communicate reading state without opening item details
- better progress visibility makes shelves and continue-reading flows feel more connected

## Next Ideas

### Library and discovery

- add a series details screen with issue order, progress badges, and quick jump to the next unread issue
- add filters for `Unread`, `Finished`, `In Progress`, and `Downloaded`
- add search improvements such as series-first matching for comics and author-first matching for books
- add a dedicated `Recently Added` shelf per source
- add a `Downloaded` shelf so offline content is easy to reach

### Reader quality

- finish the real PDF reader experience beyond the current placeholder path
- keep improving EPUB polish, including typography presets and theme control
- add per-format reader settings that are easier to discover from the library flow
- improve restore behavior after backgrounding, process death, and source refresh

### Offline and sync

- add a download queue screen with progress, failures, retry, and storage visibility
- show sync state more clearly when progress is pending upload or has failed
- define conflict behavior for explicit unread resets vs. remote progress updates
- support batch download actions for a full comic series or selected items

### Source integrations

- improve remote source onboarding with clearer connection health and error states
- add stronger series grouping and navigation for comics across supported servers
- make remote metadata refresh more visible and user-triggerable

## Suggested Order

1. Shelves on every main screen
2. Mark read and unread
3. Read-state filters and sorting updates
4. Improve library progress visuals
5. Local comic downloads
6. Series details screen
7. Download queue and storage management
8. PDF and EPUB reader polish
9. Sync conflict visibility and recovery UX

## Delivery Notes

When implementing roadmap items, keep using small vertical slices:

1. ship the narrowest useful version first
2. add the smallest meaningful automated test coverage
3. verify the real user flow from library to reader and back
4. avoid broad refactors unless the feature is blocked without them

## Open Questions

- should shelves live on the unified library screen, each source tab, or both?
- should `Next In Series` be based only on remote series metadata, local progress, or a hybrid model?
- when marking an item unread, should local reader session preferences stay intact or be cleared?
- should downloaded comics appear as one merged item or as separate local/remote states behind one card?
