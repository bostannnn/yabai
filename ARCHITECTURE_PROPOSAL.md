# Unified Reader Architecture Proposal

## Objective

Build a single Android application for reading CBZ, PDF, and ePub files while acting as a client for:

- Kavita for comics and PDF
- Grimmory for books and ePub

The app must use native REST APIs only, with no OPDS layer.

## Product Scope

### Supported formats

- CBZ
- PDF
- ePub

### Core capabilities

- Browse libraries from Kavita and Grimmory
- Read content with format-specific readers
- Stream content when appropriate
- Download content for offline reading
- Sync reading progress in both directions
- Persist local state in Room

### Explicitly out of scope for initial versions

- Guided View
- 3D page curl transitions
- Advanced PDF annotation
- Cross-source merged recommendation feed

## Recommended Tech Stack

- Language: Kotlin
- UI: Jetpack Compose
- Networking: Ktor
- Serialization: kotlinx.serialization
- Database: Room
- Background work: WorkManager
- Concurrency: Coroutines and Flow
- Dependency injection: Hilt or Koin
- Preferences: DataStore
- Covers/thumbnails: Coil
- ePub: Readium Kotlin
- PDF: Pdfium

## High-Level Architecture

Use one shared data and domain layer, with separate reader modules per format.

### Layers

1. UI layer
- Compose screens and reader surfaces

2. Domain layer
- Canonical models
- Repository contracts
- Sync rules

3. Data layer
- Kavita API provider
- Grimmory API provider
- Repository implementations
- Room persistence

4. Reader engines
- Comics reader
- PDF reader
- ePub reader

## Module Layout

Keep modules small and responsibility-focused.

- `app`
- `core:model`
- `core:common`
- `core:network`
- `core:database`
- `data:kavita`
- `data:grimmory`
- `data:repository`
- `feature:library`
- `feature:reader-comics`
- `feature:reader-pdf`
- `feature:reader-epub`
- `feature:downloads`
- `feature:settings`

## Why Small Modules

Small modules reduce refactor pressure later, but only if boundaries are correct.

Good module boundaries should separate:

- domain models from API DTOs
- readers from library browsing
- sync logic from UI
- persistence from networking
- source-specific integrations from shared business logic

Do not create tiny modules for the sake of it. Too many micro-modules create build and dependency overhead.

Recommended rule:

- small enough that each module has one clear responsibility
- large enough that normal changes do not require touching many modules

## Canonical Domain Model

Never leak Kavita or Grimmory response shapes into UI code.

Core models:

- `Library`
- `Series`
- `ReadableItem`
- `ReadingProgress`
- `OfflineAsset`
- `ReaderPreferences`

Important enums:

- `SourceType`: `KAVITA`, `GRIMMORY`
- `FormatType`: `CBZ`, `PDF`, `EPUB`
- `LocatorType`: `PAGE`, `CFI`, `PERCENT`
- `SyncState`: `CLEAN`, `DIRTY`, `SYNCING`, `FAILED`

## Persistence Strategy

Room is the persistent source of truth for the app UI.

Use Room for:

- servers
- libraries
- series
- items
- reading progress
- offline assets
- download tasks
- sync events

Do not use Room as the live state owner for every reader gesture.

Reader sessions should keep in-memory state for:

- current page
- zoom
- pan
- current locator
- prefetch window

Persist checkpoints to Room on:

- debounce interval
- screen exit
- app background
- sync handoff

## Source Integrations

### Kavita

- discover libraries
- browse series and issues
- fetch covers and metadata
- stream comic pages or assets
- sync progress by page number

Progress target:

- `POST /api/Reader/progress`

### Grimmory

- discover book libraries
- browse books
- fetch covers and metadata
- open/download ePub assets
- sync progress by CFI or percentage

Progress target:

- `PATCH /api/v1/books/{id}/progress`

## Reader Modules

### Comics reader

- `HorizontalPager`
- tiled image rendering for large pages
- hardware-accelerated zoom and pan
- long-press loupe
- page prefetch window: current page plus 5 ahead
- immersive mode

### PDF reader

- Pdfium-backed page rendering
- page cache
- zoom and pan
- adjacent page pre-rendering

### ePub reader

- Readium Kotlin navigator
- CSS injection
- custom typography
- theme overrides
- immersive mode
- locator bridge to app progress model

## Sync Design

Sync should be conflict-aware, not simple mirroring.

Rules:

- local reader updates write to Room immediately as dirty state
- WorkManager uploads dirty progress
- background sync also pulls remote progress
- repository resolves conflict before saving final state

Conflict rules:

- prefer newer trustworthy update timestamps
- do not allow silent progress regression
- prefer CFI over percent when both exist for ePub
- completed state usually wins over partial progress

Suggested workers:

- `ProgressUploadWorker`
- `ProgressPullWorker`
- `LibraryRefreshWorker`
- `DownloadWorker`

## Offline Strategy

### Comics and PDF

- default to streaming
- allow full offline download when supported
- persist download manifest and integrity state

### ePub

- download full file for offline reading
- store local path, checksum, and version state

## Initial Delivery Plan

### Phase 1

- project setup
- DI setup
- Room schema
- auth and server management
- canonical models

### Phase 2

- Kavita browsing
- Grimmory browsing
- source-separated library UI
- metadata and cover loading

### Phase 3

- comics reader
- zoom, loupe, prefetch
- local progress checkpointing
- Kavita progress sync

### Phase 4

- ePub reader
- typography and themes
- Grimmory progress sync

### Phase 5

- PDF reader
- offline download manager
- background workers

### Phase 6

- sync conflict hardening
- cache cleanup
- error handling polish
- performance profiling

## Testing Strategy

You should test at four levels.

### 1. Unit tests

Test:

- mappers
- repository logic
- sync conflict resolution
- progress normalization
- download state transitions

Use:

- JUnit
- kotlinx.coroutines test
- fake API services
- in-memory Room

### 2. Integration tests

Test:

- Room plus repository behavior
- network-to-database sync paths
- offline and resume flows

Use:

- fake servers or mocked API clients
- WorkManager test helpers

### 3. UI tests

Test:

- library rendering
- source switching
- item details
- reader entry points
- offline toggle behavior

Use:

- Compose UI tests

### 4. Manual device and emulator testing

Test:

- large libraries
- huge comic pages
- heavy PDFs
- font/theme changes
- offline mode
- background sync timing

## Tooling and Running the App

Yes, Android Studio is the normal way to run and debug this project.

Recommended setup:

- install Android Studio
- install Android SDK
- create at least one emulator
- also test on a real Android device when possible

Why Android Studio:

- easiest emulator setup
- layout inspector
- profiler
- Logcat
- Compose tooling
- test runner integration

CLI builds are possible with Gradle, but Android Studio is the practical default for development.

## Practical Guidance

- Keep module boundaries clean from day one
- Avoid over-fragmenting into too many modules
- Treat sync correctness and reader performance as first-class priorities
- Ship excellent paging, zoom, typography, and offline behavior before adding visual effects
