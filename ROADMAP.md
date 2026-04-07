# Unified Reader Roadmap

## Goal

Build a production-ready Android reader for CBZ, PDF, and ePub with:

- Kavita integration for comics and PDF
- Grimmory integration for books and ePub
- native REST APIs only
- local-first UI backed by Room

## Guiding Principles

- prioritize stable module boundaries over fast hacks
- keep readers isolated from the rest of the app
- keep API DTOs isolated inside source-specific modules
- treat sync correctness and reader performance as top priorities
- ship the smallest valuable vertical slices first
- build the core reading experience before remote integrations

## Delivery Rules

For ongoing feature work, the default workflow is:

1. implement one small vertical slice
2. review it immediately
3. add or update the smallest useful automated tests
4. run terminal verification
5. do a short manual pass only for interaction feel or visual polish

Testing expectations:

- unit tests for logic, repositories, migrations, and ViewModel state
- smoke or instrumentation tests for real app flows such as import, navigation, restore, and persistence
- manual testing only for gestures, animation feel, and device-specific UX behavior

Do not batch many features before adding tests. Each feature should leave the codebase with at least one meaningful automated check.

## Phase 0: Foundation

Deliverables:

- Android project scaffold
- module graph
- dependency management
- base app theme and navigation shell
- DI setup

Exit criteria:

- app builds
- emulator launches the shell app
- module dependencies are enforced

## Phase 1: Domain and Persistence

Deliverables:

- canonical domain models
- Room schema
- DAOs for servers, libraries, items, progress, offline assets
- repository contracts

Exit criteria:

- Room migrations planned from version 1
- repositories can expose fake data from local DB

## Phase 2: Local UI Shell

Deliverables:

- app navigation shell
- source tabs and placeholder library screens
- details screen shell
- reader entry points
- settings screen shell
- fake/sample local data providers

Exit criteria:

- user can move through the app end-to-end without network integration
- UI state flows from Room or fake repositories, not hardcoded composables

## Phase 3: Comics Reader

Deliverables:

- CBZ reader shell
- horizontal paging
- zoom and pan
- loupe
- page prefetch
- local progress checkpointing
- local sample CBZ loading

Exit criteria:

- large pages remain smooth on mid-range hardware
- page progress persists locally without jank

## Phase 4: ePub Reader

Deliverables:

- Readium integration
- typography controls
- theme overrides
- immersive mode
- local sample ePub loading

Exit criteria:

- resume position is accurate
- font and theme settings apply consistently

## Phase 5: PDF Reader

Deliverables:

- Pdfium rendering pipeline
- zoom and pan
- page persistence
- adjacent page pre-rendering
- local sample PDF loading

Exit criteria:

- heavy PDFs are readable without major frame drops

## Phase 6: Local Library Experience

Deliverables:

- source-separated home tabs
- local library list
- detail screens
- cover loading
- metadata caching
- continue reading rails from local state

Exit criteria:

- app feels coherent and usable with only local/sample content

## Phase 7: Server Connections

Deliverables:

- Kavita auth and API client
- Grimmory auth and API client
- source-specific DTOs and mappers
- server management UI
- remote library ingest into Room

Exit criteria:

- user can connect at least one Kavita server and one Grimmory server
- remote content populates existing local-first UI and readers without major refactors

## Phase 8: Sync and Offline

Deliverables:

- progress upload worker
- progress pull worker
- download worker
- offline asset storage
- conflict resolution rules
- Kavita page progress sync
- Grimmory CFI and percent sync

Exit criteria:

- read progress survives offline gaps and reconnects safely
- downloads are resumable or at least recover cleanly

## Phase 9: Hardening

Deliverables:

- error states
- retry and backoff
- telemetry/logging hooks
- cache cleanup
- performance profiling
- test coverage expansion

Exit criteria:

- libraries with large datasets stay responsive
- reader sessions are stable under app backgrounding and restore

## Suggested Milestone Order

1. Build the project shell and module graph
2. Add canonical models and Room
3. Build app navigation and local sample-data flows
4. Ship the comics reader
5. Ship the ePub reader
6. Ship the PDF reader
7. Polish the local library UX
8. Integrate Kavita and Grimmory
9. Add offline and sync hardening

## Testing Plan

### Unit tests

Test:

- DTO to domain mapping
- sync conflict rules
- progress normalization
- repository decisions
- reader preference persistence
- migration and bootstrap behavior

### Integration tests

Test:

- Room plus repository behavior
- refresh flows
- offline/download state handling
- WorkManager-driven sync paths
- Room-backed local archive import and reader restore

### UI tests

Test:

- source tabs
- library rendering
- navigation into readers
- direct book-to-reader flow
- import action and resulting local library update
- reader restore after reopen/background

### Manual testing

Test on emulator and real device:

- huge comic pages
- large libraries
- heavy PDFs
- app background and restore
- unstable network
- offline mode

## Tooling

Use Android Studio as the primary environment for:

- emulator management
- profiling
- debugging
- Compose inspection
- instrumentation tests

Command-line Gradle should be used for CI and fast local verification.

## Current Completion Snapshot

Completed:

- Phase 0 foundation
- Phase 1 basic domain and persistence scaffold
- Phase 2 local UI shell
- initial Phase 6 local library UX scaffold

In place now:

- app launches on emulator
- fake repository and `ViewModel` drive the shell UI
- placeholder readers for CBZ, PDF, and EPUB are wired and navigable
- terminal build command is validated:
  - `./gradlew :app:assembleDebug`

Next recommended milestone:

- Phase 3 Comics Reader
