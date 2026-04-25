# Library Home UI Refinement Plan

## Scope

This plan covers the library/home UI work discussed for the current shelves-based implementation:

- add chapter or issue name to `Continue Reading` the same way `Up Next` shows it
- restore the third metadata row in `Continue Reading` preview cards:
  `percentage · currentPage/pageCount`
- stop shelves from feeling jumpy during library hydration and async shelf recompute
- add loading skeletons for shelves and for the library browse surface

This plan is implementation-focused and follows `ARCHITECTURE_RULES.md`.

## Ownership

### Owning modules

- `feature:library-shelves`
  - shelf card text selection
  - shelf loading state
  - shelf placeholder and slot retention behavior
  - shelf-specific tests
- `feature:library`
  - library loading skeletons for the main browse surface
  - browse loading placeholders and tests
- `app`
  - thin wiring only
  - pass existing hydration signals into feature-owned state
  - no new business rules

### Files that may change

- `feature/library-shelves/src/main/java/com/example/unifiedreader/feature/library/shelves/LibraryShelvesState.kt`
- `feature/library-shelves/src/main/java/com/example/unifiedreader/feature/library/shelves/LibraryShelvesSections.kt`
- `feature/library-shelves/src/main/java/com/example/unifiedreader/feature/library/shelves/LibraryShelvesPlaceholders.kt`
- `feature/library-shelves/src/main/java/com/example/unifiedreader/feature/library/shelves/LibraryShelvesTopSlot.kt`
- `feature/library-shelves/src/main/java/com/example/unifiedreader/feature/library/shelves/LibraryShelvesZone.kt`
- `feature/library-shelves/src/test/...`
- `feature/library-shelves/src/androidTest/...`
- `feature/library/src/main/java/com/example/unifiedreader/feature/library/LibraryScreen.kt`
- new `feature/library/src/main/java/com/example/unifiedreader/feature/library/LibraryLoadingPlaceholders.kt`
- `feature/library/src/test/...`
- `feature/library/src/androidTest/...`
- `app/src/main/java/com/example/unifiedreader/LibraryRoute.kt`
- `app/src/main/java/com/example/unifiedreader/AppUiState.kt`

### Files that should not grow

- `app/src/main/java/com/example/unifiedreader/AppViewModel.kt`
- `app/src/main/java/com/example/unifiedreader/AppModule.kt`
- `app/src/main/java/com/example/unifiedreader/AppRemoteSourcesCoordinator.kt`
- `app/src/main/java/com/example/unifiedreader/AppLibraryStateRecomputeCoordinator.kt`
- `feature/library/src/main/java/com/example/unifiedreader/feature/library/LibraryFilterComponents.kt`

If a change requires logic in one of the files above, the default response should be extraction or delegation first.

## Refactor Checks Before New Feature Work

### 1. Extract library loading UI before adding skeletons

`feature:library/LibraryFilterComponents.kt` is already a hotspot and should not absorb new loading UI.

Required extraction before library skeleton work:

- move `LoadingLibraryState` out of `LibraryFilterComponents.kt`
- create a dedicated browse-loading file such as `LibraryLoadingPlaceholders.kt`
- keep `LibraryScreen.kt` responsible only for choosing which loading composable to render

### 2. Keep shelf loading policy out of app-shell recompute code

`app/AppLibraryStateRecomputeCoordinator.kt` already performs async recompute and merge behavior.
If we add more rules there for shelf loading, retention, collapse, or visibility, `app` becomes the owner of shelf behavior.

Required direction:

- shelf presentation policy must stay in `feature:library-shelves`
- `app` may pass thin inputs only:
  - `isSourceHydrated`
  - computed `LibraryShelvesState`
  - browse context key
- if more state is needed, add it to a feature-owned shelves model rather than pushing more logic into app-shell code

### 3. Avoid growing shelf view files with formatting logic

`LibraryShelvesSections.kt` and `LibraryShelvesCards.kt` are close to the line-count guardrails.
If text-selection logic expands beyond a few lines, extract a focused helper inside `feature:library-shelves`, for example:

- `LibraryShelvesText.kt`
- `ContinueReadingMetadataFormatter.kt`

## Implementation Phases

### Phase 1. Restore `Continue Reading` metadata

Owner: `feature:library-shelves`

Goal:

- make `Continue Reading` preview cards show the same chapter or issue label behavior as `Up Next`
- restore the third metadata row in preview cards using existing progress data

Expected changes:

- restore `trailingText = shelfItem.progress.detailLabel` in `ContinueReadingCarouselCard`
- align supporting text fallback with `Up Next`:
  - prefer `librarySecondaryText()`
  - fall back to `item.title` or a small feature-owned helper if the secondary text is deduped away

Tests:

- update or add compose tests for preview card text
- add unit coverage for the chosen fallback behavior if extracted into a helper

### Phase 2. Stabilize shelf visibility during loading

Owner: `feature:library-shelves`

Goal:

- shelves should not blink in and out while the selected source hydrates or while async shelf state is being recomputed

Required design:

- add an explicit shelves loading state instead of inferring loading from `hasContent`
- keep slot reservation and placeholder behavior feature-owned
- only collapse the shelf area when the feature knows loading is complete and there is truly no shelf content to show

Likely shape:

- extend `LibraryShelvesState` with a small loading signal or wrap it in a feature-owned presentation state
- keep `LibraryShelvesTopSlot` as a thin presenter of that state
- do not encode more collapse timing rules in app-shell code

Tests:

- compose test: placeholder remains visible while shelves are loading
- compose test: loaded shelves replace the placeholder without layout collapse
- compose test: empty non-loading shelves collapse only after the intended rule is met

### Phase 3. Add shelf skeletons

Owner: `feature:library-shelves`

Goal:

- shelves show meaningful loading placeholders instead of appearing and disappearing

Expected changes:

- reuse and finish the existing placeholder work in `LibraryShelvesPlaceholders.kt`
- support one-card-row and multi-card-row placeholders depending on available width
- ensure placeholders work for both initial hydration and recompute transitions

Tests:

- compose tests for placeholder rendering in both compact and wide layouts

### Phase 4. Add library browse skeletons

Owner: `feature:library`

Goal:

- replace text-only `LoadingLibraryState` with layout-matching skeletons for the current browse mode

Required extraction first:

- create `LibraryLoadingPlaceholders.kt`

Expected changes:

- grid-mode skeleton matching the active grid density
- detailed/list-mode skeleton matching detailed cards
- `LibraryScreen.kt` chooses between empty state and loading placeholders without taking on new business logic

Tests:

- compose tests for loading placeholders in grid mode
- compose tests for loading placeholders in detailed mode

## Module Boundary Notes

No new Gradle module is required for Phase 1.

For Phases 2 through 4:

- if shelf loading state and library browse skeletons stay independent, keep them in `feature:library-shelves` and `feature:library`
- if a shared loading-placeholder system starts to emerge across both features, create a dedicated module up front, for example `feature:library-loading`, instead of duplicating loading rules in both modules

The key rule is that we decide that ownership before implementation, not after app-shell code has already absorbed the behavior.

## Review Gates

Reject the implementation if any of the following happen:

- `app` gains shelf-loading business rules
- `AppLibraryStateRecomputeCoordinator.kt` grows beyond thin delegation for shelf behavior
- `LibraryFilterComponents.kt` takes on new skeleton UI
- `Continue Reading` text selection is duplicated in multiple call sites
- tests are added only in `app` for behavior owned by `feature:library-shelves` or `feature:library`

## Open Product Question

One ambiguity needs confirmation before Phase 2:

- when you say shelves should be visible at all times, should they remain visible even during active search, filters, and series-browse states, or only stop flickering during normal library loading and recompute?

That answer changes whether we only refine loading behavior or also change the current `shouldReserveLibraryShelvesSlot` product rule.
