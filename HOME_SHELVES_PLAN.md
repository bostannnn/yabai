# Home Shelves Plan

## Goal

Add quick-access rows at the top of the library for server-driven or server-compatible "home shelves":

- Grimmory: `Continue Reading`
- Grimmory: selected created shelf or magic shelf
- Stump: `Continue Reading`
- Stump: `Your Next Read`
- Kavita: `On Deck`

The current goal is planning only. No implementation is included in this document.

## Summary

All three integrations can support this feature, but they are not equally mature:

- Grimmory already syncs enough book and progress data to build `Continue Reading` locally.
- Grimmory also exposes shelf and magic-shelf endpoints, so user-selected shelves can be fetched from server.
- Stump exposes `keepReading` and `onDeck` in its GraphQL-backed home UI, but this app does not call them yet.
- Kavita exposes a dedicated `POST /api/Series/on-deck` endpoint, but this app does not call it yet.

## Current State In This Repo

### Grimmory

- Sync currently imports libraries and all books with progress.
- Relevant local code:
  - [data/grimmory/src/main/kotlin/com/example/unifiedreader/data/grimmory/GrimmoryApi.kt](/Users/andrewbostan/Documents/yabai/data/grimmory/src/main/kotlin/com/example/unifiedreader/data/grimmory/GrimmoryApi.kt)
  - [data/grimmory/src/main/kotlin/com/example/unifiedreader/data/grimmory/GrimmoryDtos.kt](/Users/andrewbostan/Documents/yabai/data/grimmory/src/main/kotlin/com/example/unifiedreader/data/grimmory/GrimmoryDtos.kt)
  - [app/src/main/java/com/example/unifiedreader/GrimmoryCatalogSyncService.kt](/Users/andrewbostan/Documents/yabai/app/src/main/java/com/example/unifiedreader/GrimmoryCatalogSyncService.kt)
- Available synced fields already include:
  - `lastReadTime`
  - `pdfProgress`
  - `epubProgress`
  - `addedOn`

### Stump

- Sync currently imports libraries, series, media items, and per-item progress.
- Relevant local code:
  - [data/stump/src/main/kotlin/com/example/unifiedreader/data/stump/StumpApi.kt](/Users/andrewbostan/Documents/yabai/data/stump/src/main/kotlin/com/example/unifiedreader/data/stump/StumpApi.kt)
  - [data/stump/src/main/kotlin/com/example/unifiedreader/data/stump/StumpDtos.kt](/Users/andrewbostan/Documents/yabai/data/stump/src/main/kotlin/com/example/unifiedreader/data/stump/StumpDtos.kt)
  - [app/src/main/java/com/example/unifiedreader/StumpCatalogSyncService.kt](/Users/andrewbostan/Documents/yabai/app/src/main/java/com/example/unifiedreader/StumpCatalogSyncService.kt)
- The app does not currently fetch Stump home-scene shelves.

### Kavita

- Sync currently imports libraries, all series, series detail, and chapter progress.
- Relevant local code:
  - [data/kavita/src/main/kotlin/com/example/unifiedreader/data/kavita/KavitaApi.kt](/Users/andrewbostan/Documents/yabai/data/kavita/src/main/kotlin/com/example/unifiedreader/data/kavita/KavitaApi.kt)
  - [data/kavita/src/main/kotlin/com/example/unifiedreader/data/kavita/KavitaDtos.kt](/Users/andrewbostan/Documents/yabai/data/kavita/src/main/kotlin/com/example/unifiedreader/data/kavita/KavitaDtos.kt)
  - [app/src/main/java/com/example/unifiedreader/KavitaCatalogSyncService.kt](/Users/andrewbostan/Documents/yabai/app/src/main/java/com/example/unifiedreader/KavitaCatalogSyncService.kt)
- The app does not currently fetch `On Deck`.

## Recommended Product Shape

Show a small set of optional rows above the normal library grid for the selected source only.

Recommended initial rows:

- Grimmory
  - `Continue Reading`
  - one configurable shelf source:
    - static shelf
    - magic shelf
- Stump
  - `Continue Reading`
  - `Your Next Read`
- Kavita
  - `On Deck`

Recommended UX rules:

- Hide a row when it has no items.
- Keep rows source-specific.
- Do not mix row items into the main library sort/filter grid.
- Tapping a row item should open the same reader or series flow as normal library items.

## Data Strategy By Source

### Grimmory

#### 1. Continue Reading

Use local derivation first.

Why:

- The sync already downloads all Grimmory books with progress.
- This avoids a second network path for a feature that can already be computed.
- It will remain stable even if Grimmory changes dashboard internals.

Suggested rule:

- Include Grimmory items with non-null progress.
- Exclude completed items.
- Sort by `lastReadTime` descending.
- Cap row size, for example 10 to 20 items.

#### 2. Static Shelf

Fetch from Grimmory shelf endpoints.

Confirmed server capability:

- `GET /api/v1/shelves`
- `GET /api/v1/shelves/{shelfId}/books`

Suggested product behavior:

- Let user choose one shelf in settings.
- Cache shelf summaries locally.
- Load books for the selected shelf on demand when Grimmory source is active.

#### 3. Magic Shelf

Fetch from Grimmory mobile/app shelf endpoints.

Confirmed server capability:

- `GET /api/v1/app/shelves/magic`
- `GET /api/v1/app/shelves/magic/{magicShelfId}/books`

Suggested product behavior:

- Treat this as the same configuration slot as static shelf.
- User chooses either:
  - no shelf row
  - static shelf
  - magic shelf

### Stump

#### 1. Continue Reading

Preferred source: Stump server `keepReading`.

Why:

- This matches Stump’s own home UI.
- It avoids guessing which item in a series should be shown.
- It should preserve Stump’s intended ranking.

Fallback:

- If `keepReading` is difficult to integrate at first, derive a basic row from already-synced media progress.

#### 2. Your Next Read

Preferred source: Stump server `onDeck`.

Why:

- Stump’s own UI exposes this as the recommendation-style row.
- Mobile labels it `Your Next Read`.

Important note:

- In repo naming, Stump uses both `OnDeck` and `Your Next Read` for the same concept.
- Internally, keep the model/source name aligned with `onDeck`; render the UI label as `Your Next Read`.

### Kavita

#### 1. On Deck

Preferred source: Kavita server `POST /api/Series/on-deck`.

Why:

- It is a dedicated API for the exact feature.
- It avoids rebuilding recommendation logic in the app.

Optional later enhancement:

- Read `GET /api/Stream/dashboard` if you want user-configured Kavita dashboard parity in the future.
- This is not needed for the first version.

## Architecture Proposal

### Add A Shared Home Rows Model

Introduce a small app-level model for source-specific top rows.

Suggested shape:

```kotlin
data class HomeShelfRow(
    val id: String,
    val sourceType: SourceType,
    val title: String,
    val items: List<ReadableItem>,
    val loading: Boolean = false,
    val errorMessage: String? = null,
)
```

This should stay UI-facing and avoid leaking raw server DTOs upward.

### Add Source-Specific Row Loaders

Create one loader/coordinator per source:

- `GrimmoryHomeShelvesLoader`
- `StumpHomeShelvesLoader`
- `KavitaHomeShelvesLoader`

Responsibilities:

- fetch or derive row contents
- map remote data to existing `ReadableItem`
- preserve item identity so reader/progress logic still works
- expose rows as state

### Avoid Polluting Main Catalog

Do not store row contents as fake libraries or fake items inside the normal source catalog.

Why:

- they are presentation rows, not primary catalog structure
- mixing them into the main grid will complicate filtering and sorting
- some rows are duplicates of already-synced items

Instead:

- keep row state separate in `AppUiState`
- render rows above the existing library grid

## UI Integration Plan

### New UI Section

Add a top section in the library screen for `selectedSource`.

Suggested order:

1. source home rows
2. existing filter/sort summary
3. existing library grid

### Row Rendering

Use horizontal scrollers with existing cover cards where possible.

Per-row behavior:

- compact horizontal list
- show title
- show loading skeleton or spinner per row
- show nothing if empty

### Item Click Behavior

Reuse the existing item selection path:

- Grimmory row item opens book
- Stump row item opens media item or series-mapped item flow already used by library
- Kavita row item opens series or chapter target according to mapped item strategy

## Mapping Concerns

### Grimmory

- Shelf endpoints may return book summaries, not the exact same object shape as `/api/v1/books`.
- Prefer mapping by remote book id.
- If the shelf payload is incomplete, enrich from already-synced Grimmory catalog when possible.

### Stump

- `keepReading` and `onDeck` likely return media entities aligned with Stump home UI, not the exact series-detail DTO currently used here.
- You will need a lightweight mapper from Stump home GraphQL results to `ReadableItem`.
- Favor stable `remoteId` matching so row items can reuse existing progress/session state.

### Kavita

- `on-deck` returns series-level data.
- Current app catalog is chapter-oriented, then grouped into series containers.
- Best approach for the first version:
  - map `On Deck` entries to the existing Kavita series container concept, not raw chapters
  - reuse current series grouping identity where possible

## Settings Plan

Add optional settings only where needed.

### Grimmory

Add one configurable "extra top row" selector:

- `None`
- `Shelf`
- `Magic Shelf`

When `Shelf` or `Magic Shelf` is selected, show a second selector for the concrete shelf.

### Stump

No settings needed for first version.

- always show `Continue Reading` if non-empty
- always show `Your Next Read` if non-empty

### Kavita

No settings needed for first version.

- always show `On Deck` if non-empty

## Delivery Order

Recommended implementation order:

### Phase 1

- Grimmory `Continue Reading` from local synced data

Why first:

- no new remote contract required
- smallest risk
- proves top-row UI without changing server integrations

### Phase 2

- Kavita `On Deck`

Why second:

- dedicated upstream endpoint
- cleanest server-backed implementation
- high user value

### Phase 3

- Stump `Your Next Read`
- Stump `Continue Reading`

Why third:

- requires new GraphQL queries and DTOs
- still straightforward because upstream UI code clearly uses these concepts

### Phase 4

- Grimmory static shelf
- Grimmory magic shelf
- Grimmory row selection in settings

Why later:

- more settings work
- more choices for the user
- requires handling two shelf families cleanly

## Testing Plan

### Unit Tests

Add tests for:

- Grimmory continue-reading row derivation
- empty-row suppression
- stable row ordering
- mapping from server row payloads to `ReadableItem`
- Kavita series-container row mapping
- Stump `onDeck` and `keepReading` mapping

### UI Tests

Add coverage for:

- rows render above library grid
- rows only appear for selected source
- empty rows are hidden
- clicking a row item opens the expected item
- switching source updates visible rows correctly

### Regression Risks

Highest-risk areas:

- duplicate identities between row items and library items
- Kavita series-level row mapping vs chapter-level catalog
- Stump row payload shape drift vs current series-detail model
- Grimmory shelf payloads not matching full catalog payloads

## Open Questions

- For Kavita `On Deck`, should tapping a row open:
  - the series container view
  - the next unread chapter directly
- For Stump `Your Next Read`, should the UI label remain exactly `Your Next Read`, even if the internal API term is `onDeck`?
- For Grimmory, should the configurable extra row allow more than one selected shelf in the future, or exactly one for now?
- Should top rows ignore library filters and search entirely, or partially respect source-level filters?

## Recommended First Cut

If the goal is fastest useful release with low risk:

1. Build a generic top-row UI container.
2. Ship Grimmory `Continue Reading` from local progress.
3. Ship Kavita `On Deck` from server.
4. Ship Stump `Your Next Read` from server.
5. Add Stump `Continue Reading`.
6. Add Grimmory shelf selection last.

This keeps the first release focused and avoids turning the library screen into a large refactor.
