# Current Status

## Project State

The project is bootstrapped and currently builds and runs.

Validated state as of 2026-04-05:

- Android Studio project opens and syncs
- Gradle wrapper is present and usable
- `:app:assembleDebug` succeeds from terminal when `JAVA_HOME` is set
- `:app:testDebugUnitTest` succeeds from terminal when `JAVA_HOME` is set
- emulator launch works
- app launches successfully
- implemented local-first flows work:
  - Library tab
  - Settings tab
  - Kavita and Grimmory source tabs
  - direct library-to-reader navigation
  - CBZ/CBR import
  - local comics reader with paging, zoom/pan, LTR/RTL, fit modes
  - Room-backed reader state restore
  - PDF placeholder reader
  - EPUB placeholder reader

## Current Architecture Snapshot

The codebase is still a scaffold, but it is no longer all held inside `MainActivity`.

Current structure:

- [MainActivity.kt](/Users/andrewbostan/Documents/yabai/app/src/main/java/com/example/unifiedreader/MainActivity.kt)
  - thin app host
  - renders app shell and delegates state/actions
- [AppViewModel.kt](/Users/andrewbostan/Documents/yabai/app/src/main/java/com/example/unifiedreader/AppViewModel.kt)
  - owns current app UI state
  - handles source switching, item selection, reader entry, and back behavior
- [SampleLibraryRepository.kt](/Users/andrewbostan/Documents/yabai/app/src/main/java/com/example/unifiedreader/SampleLibraryRepository.kt)
  - fake local repository for sample content
- [ImportedComicArchiveRepository.kt](/Users/andrewbostan/Documents/yabai/app/src/main/java/com/example/unifiedreader/ImportedComicArchiveRepository.kt)
  - Room-backed local imported CBZ/CBR metadata
- [RoomComicReaderStateStore.kt](/Users/andrewbostan/Documents/yabai/app/src/main/java/com/example/unifiedreader/RoomComicReaderStateStore.kt)
  - Room-backed page progress and per-item reader preferences
- [LibraryScreen.kt](/Users/andrewbostan/Documents/yabai/feature/library/src/main/java/com/example/unifiedreader/feature/library/LibraryScreen.kt)
  - library UI with detailed/grid modes and import action
- [ComicsReaderEntry.kt](/Users/andrewbostan/Documents/yabai/feature/reader-comics/src/main/java/com/example/unifiedreader/feature/reader/comics/ComicsReaderEntry.kt)
  - real local comics reader
- [PdfReaderEntry.kt](/Users/andrewbostan/Documents/yabai/feature/reader-pdf/src/main/java/com/example/unifiedreader/feature/reader/pdf/PdfReaderEntry.kt)
  - placeholder PDF reader
- [EpubReaderEntry.kt](/Users/andrewbostan/Documents/yabai/feature/reader-epub/src/main/java/com/example/unifiedreader/feature/reader/epub/EpubReaderEntry.kt)
  - placeholder EPUB reader

## Important Decisions Already Made

- Guided View was removed from scope.
- Remote integrations are intentionally deferred until the local reader experience is stronger.
- Current priority order is:
  1. local-first app structure
  2. real reader implementations
  3. Kavita/Grimmory integration
- Room is now the runtime source of truth for local imported archives and reader session state.
- `exportSchema` in the Room database is currently disabled to avoid schema-output build failures during scaffold phase.

## Delivery Workflow Rules

Future agents should follow this default workflow for every feature or bug fix:

1. implement the smallest usable vertical slice
2. do a review immediately after implementation
3. add or update the smallest relevant automated tests
4. run terminal verification
5. do only a short manual emulator pass for interaction quality

Do not defer tests until the end of a large batch of work.

Practical rule of thumb:

- if the change is logic-heavy, add unit tests
- if the change alters a real app flow, add or update a smoke/instrumentation test
- if the change is mostly gesture feel or visual polish, do a short manual pass after automated checks

Avoid broad cleanup or architecture refactors unless they are required by the feature being implemented.

For repository-specific architecture guardrails and Codex prompting rules, also follow:

- [ARCHITECTURE_RULES.md](/Users/andrewbostan/Documents/yabai/ARCHITECTURE_RULES.md)
- [CODEX_PROMPT_TEMPLATE.md](/Users/andrewbostan/Documents/yabai/CODEX_PROMPT_TEMPLATE.md)

## Known Good Build/Test Workflow

Use terminal builds instead of screenshot-based debugging whenever possible.

From the project root:

```bash
cd /Users/andrewbostan/Documents/yabai
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
```

This command was validated successfully by the user on 2026-04-05.

For normal feature work, prefer:

```bash
cd /Users/andrewbostan/Documents/yabai
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :core:database:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

If instrumentation smoke tests are added later, they should become part of the default validation path for reader/import/navigation changes.

Optional persistent shell setup:

```bash
echo 'export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

After that, builds can be run with:

```bash
cd /Users/andrewbostan/Documents/yabai
./gradlew :app:assembleDebug
```

## Android Studio Notes

The user is on Android Studio Panda 3 and menu locations differ from older versions.

Practical notes:

- prefer Search Everywhere with double `Shift`
- use `Device Manager` to create or manage emulators
- `Build > Assemble Project` is available and works as the build action
- the emulator has already been created and used successfully

## Manual Validation Checklist

These were tested and reported working:

- app launches on emulator
- sample covers load from remote URLs
- `Library` and `Settings` tabs switch correctly
- `Kavita` and `Grimmory` source tabs switch correctly
- importing local CBZ/CBR works
- tapping a book opens the reader directly
- comics reader paging, restore, and per-item reader preferences work

Still worth verifying after future refactors:

- rotation does not break current flow
- app background/restore behavior
- import flow across different Android document providers
- real gesture feel for zoom/pan on device

## Known Technical Debt

- App navigation is still local-state based, not Navigation Compose based.
- The fake repository lives in the `app` module and is not yet part of a reusable data abstraction layer.
- No instrumentation smoke tests exist yet for import and reader flows.
- PDF and EPUB readers are still placeholders.
- No Kavita or Grimmory integration is implemented yet.

## Recommended Next Step

Best next implementation target:

1. add instrumentation smoke tests for import and comics reader restore

Suggested order:

- add one emulator/device smoke test for local archive import
- add one smoke test for open reader, advance page, exit, and resume
- add lightweight logging around import, restore, and page decode failures
- only then move on to the next reader feature

Alternative next step if more cleanup is preferred first:

- move local archive and reader-state implementations out of `app` into a clearer data layer

## If the Next Agent Needs to Debug

Preferred order:

1. run terminal build
   - `./gradlew :app:assembleDebug`
2. if build fails, inspect the first real error block only
3. if app crashes, capture:
   - `FATAL EXCEPTION`
   - first `Caused by:`
4. avoid relying on screenshots of the Problems panel when raw error text is available
