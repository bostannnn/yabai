# Architecture Rules

This file exists to stop future changes from collapsing back into app-shell god modules.

These rules are stricter than the broader architecture proposal. They are intended to be used as working constraints for Codex and for human review.

## Primary Goal

Keep orchestration thin and keep behavior in the module that owns it.

A change is low quality if it technically works but:

- adds new business logic to app-shell classes
- adds side effects to DI wiring
- duplicates source-specific logic across multiple coordinators
- grows an already-large file instead of extracting a focused unit

## Hard Boundaries

### `app` module

The `app` module is allowed to:

- assemble the app shell
- wire feature modules together
- host thin state reducers and navigation decisions
- provide dependency wiring

The `app` module is not allowed to become the default home for new behavior.

Do not add business logic to:

- `app/src/main/java/com/example/unifiedreader/AppViewModel.kt`
- `app/src/main/java/com/example/unifiedreader/AppModule.kt`
- `app/src/main/java/com/example/unifiedreader/AppRemoteSourcesCoordinator.kt`

Allowed changes in those files are limited to:

- delegating to a new owning class
- registering a new dependency
- adding a small state field required by existing UI
- removing logic by extracting it elsewhere

### DI providers

Hilt providers must be construction-only.

Providers must not:

- run bootstrap or migration work
- touch disk except to construct dependencies
- perform network work
- contain business rules

If startup work is needed, create an explicit bootstrapper or startup use case and call it intentionally from app startup code.

### Source-specific behavior

Kavita, Stump, and Grimmory logic must live with the source that owns it.

Prefer:

- source-specific connect/sync rules in per-source classes
- shared remote helpers only for logic that is truly shared
- one source implementation per concern instead of one giant coordinator with branching

Avoid:

- adding another `when (sourceType)` branch to app-shell code if the behavior can live in a per-source class
- adding parallel `onKavita...`, `onStump...`, `onGrimmory...` methods unless they are simple delegation and already part of an existing thin adapter

### Reader-specific behavior

Reader behavior belongs in the reader module for that format.

Prefer:

- reader state holders in the feature reader module
- integration adapters around Readium/PDF/comic engines
- small composables that consume state and callbacks

Avoid:

- placing reader rules in `app`
- mixing UI, file I/O, session restore, and engine integration in one file when extracting is practical

## Module Ownership

Use this ownership map when deciding where code goes.

- `app`
  - app shell, top-level navigation, DI wiring, thin composition
- `core:model`
  - source-agnostic domain models and pure model helpers
- `core:common`
  - generic shared utilities with no product-specific behavior
- `core:network`
  - HTTP client setup and generic network support
- `core:database`
  - Room database, entities, DAOs, migrations
- `data:kavita`
  - Kavita DTOs, API client, source-specific mapping
- `data:stump`
  - Stump DTOs, API client, source-specific mapping
- `data:grimmory`
  - Grimmory DTOs, API client, source-specific mapping
- `data:repository`
  - repository implementations that compose data sources
- `feature:library`
  - browse/filter/sort/presentation for the library experience
- `feature:reader-core`
  - shared reader abstractions and cross-reader UI primitives
- `feature:reader-comics`
  - comics reader behavior and UI
- `feature:reader-pdf`
  - PDF reader behavior and UI
- `feature:reader-epub`
  - EPUB reader behavior and UI
- `feature:downloads`
  - download workflows and download-facing UI
- `feature:settings`
  - settings UI and settings-facing state

If code does not obviously belong to one owner, stop and decide ownership before implementing.

## File Growth Rules

Large files are allowed only when there is a clear reason. They are not the default target for extension.

Default guardrails:

- avoid growing any file already over 300 lines unless the change is a narrow extraction follow-up
- if a file is over 500 lines, prefer extraction over addition
- if a change would add more than 80 lines to one existing file, justify it first
- if a change touches more than 3 modules, explain why that coupling is necessary

Current hotspot files that should shrink over time, not grow:

- `app/src/main/java/com/example/unifiedreader/AppRemoteSourcesCoordinator.kt`
- `app/src/main/java/com/example/unifiedreader/AppViewModel.kt`
- `app/src/main/java/com/example/unifiedreader/AppModule.kt`
- `feature/reader-epub/src/main/java/com/example/unifiedreader/feature/reader/epub/EpubReaderEntry.kt`

## Preferred Change Shape

For non-trivial work, prefer this sequence:

1. decide the owning module
2. list files to change
3. list files that are explicitly forbidden
4. add or extract the owning abstraction
5. wire the app shell to it
6. add tests in the owning module

This is better than:

1. modify the app shell until the feature works
2. promise to refactor later

## Tests And Review Gates

Every logic-heavy change should add or update tests in the owning module.

Review should reject a change when:

- app-shell files gained business logic that could live elsewhere
- DI providers gained side effects
- source-specific logic was added to a shared coordinator without a strong reason
- a large file got larger without extraction
- the final explanation cannot clearly justify ownership of each changed file

## Codex Working Rules

When using Codex for implementation, require it to do all of the following before writing code:

1. name the owning module
2. list the files it plans to change
3. list the files it must not change
4. explain why the new logic belongs where it plans to put it

If the proposed plan violates this file, reject it and ask for a redesign before code is written.

## Short Review Checklist

Use this checklist for PRs or Codex outputs:

- Does the change keep `app` thin?
- Did any DI provider gain side effects?
- Is the new behavior in the module that owns it?
- Did a hotspot file grow when it should have been split?
- Are tests added in the owning module?
- Could a future source or reader be added without editing the same giant file again?
