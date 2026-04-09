# Codex Prompt Template

Use these prompts when you want Codex to stop writing god modules in this repository.

All prompts assume the repo rules in `ARCHITECTURE_RULES.md` are mandatory.

## Default Implementation Prompt

```text
Read ARCHITECTURE_RULES.md and follow it strictly.

Before writing code:
- name the owning module
- list the exact files you plan to change
- list the files you are forbidden to change
- explain why the new logic belongs in the owning module

Hard constraints:
- Do not add business logic to app/src/main/java/com/example/unifiedreader/AppViewModel.kt
- Do not add side effects to app/src/main/java/com/example/unifiedreader/AppModule.kt
- Do not add source-specific branching to app/src/main/java/com/example/unifiedreader/AppRemoteSourcesCoordinator.kt
- Do not use the app module as the default home for new logic
- Put new tests in the owning module

If your design requires touching a forbidden file in a non-trivial way, stop and explain why before coding.

After implementation, explain why each changed file owns its logic.
```

## Plan-First Prompt

Use this before any non-trivial feature.

```text
Read ARCHITECTURE_RULES.md.

Do not write code yet.

Return only:
1. owning module
2. files to change
3. files explicitly forbidden
4. new abstractions to introduce
5. tests to add
6. why this design avoids app-shell growth

Constraints:
- AppViewModel must remain a thin intent/state layer
- AppModule must remain wiring-only
- AppRemoteSourcesCoordinator must remain thin delegation only
- Prefer extraction over growing existing hotspot files
```

## Forbidden-Files Prompt

Use this when a previous Codex run already touched the wrong files.

```text
Redo this change.

Read ARCHITECTURE_RULES.md and treat these files as forbidden:
- app/src/main/java/com/example/unifiedreader/AppViewModel.kt
- app/src/main/java/com/example/unifiedreader/AppModule.kt
- app/src/main/java/com/example/unifiedreader/AppRemoteSourcesCoordinator.kt
- feature/reader-epub/src/main/java/com/example/unifiedreader/feature/reader/epub/EpubReaderEntry.kt

You may wire existing APIs, but do not add new logic to those files.

Implement the feature by introducing or updating owning classes in the correct module instead.

Before coding, state:
- owning module
- changed files
- why no forbidden file needs new logic
```

## Source-Integration Prompt

Use this for Kavita, Stump, or Grimmory work.

```text
Read ARCHITECTURE_RULES.md.

Implement this source change without growing shared app-shell orchestration.

Rules:
- source-specific logic must live in source-owned classes
- shared helpers may be used only for truly shared behavior
- avoid adding another parallel branch in AppRemoteSourcesCoordinator unless it is thin delegation only
- tests must cover the source-owned logic directly

Before coding, list:
- source owner classes to add or change
- app-shell wiring changes, if any
- tests to add
```

## Reader-Feature Prompt

Use this for EPUB, PDF, or comics reader work.

```text
Read ARCHITECTURE_RULES.md.

Implement this reader change inside the owning reader module.

Rules:
- do not add reader behavior to app-shell classes
- do not mix UI, engine integration, persistence, and file I/O in one new unit
- if touching a reader hotspot file, prefer extraction first
- add tests for the extracted state/persistence logic where practical

Before coding, list:
- state holder or adapter to create or extend
- UI files to change
- persistence or integration files to change
- what will be extracted instead of added to the hotspot file
```

## Review / Reject Prompt

Use this after a weak Codex patch.

```text
Review this patch against ARCHITECTURE_RULES.md.

Focus on:
- app-shell growth
- DI side effects
- source-specific logic placed in shared layers
- large files getting larger instead of being split
- missing tests in the owning module

If the patch violates those rules, do not praise it. List the violations first and propose a smaller redesign.
```

## What To Tell Codex When It Drifts

These short replies work better than broad complaints:

- `Reject. Redo without adding business logic to AppViewModel.`
- `Reject. Move this into the owning module and keep AppModule wiring-only.`
- `Stop. List owning files and forbidden files before coding.`
- `This is working code in the wrong layer. Redesign it.`
- `Do not grow the hotspot file. Extract a state holder and retry.`

## Recommended Human Workflow

For bigger tasks, use two passes instead of one:

1. ask Codex for ownership and touched files only
2. review the plan
3. ask Codex to implement only the approved design
4. review the result against `ARCHITECTURE_RULES.md`

This repository responds better to constrained prompts than to general requests like "reuse modules" or "keep it clean".
