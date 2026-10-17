# yabai (Android reader)

Kotlin + Jetpack Compose reader for EPUB/PDF/CBZ/CBR from Kavita, Grimmory, Stump, or local files.
Package `com.example.unifiedreader`. Hilt DI, Room DB, Ktor networking, Readium (EPUB),
`PdfRenderer` (PDF). 23 Gradle modules; ownership map in `ARCHITECTURE_RULES.md`.
Screens are built from the component kit in `core:ui-components` (`AppPanel`, `AppDialog`,
`AppButton`, `AppIcons`, ...), never from raw Material `Card`/`Surface`/`Button` or the Material
Icons artifacts; a unit test enforces this. UI font: bundled Manrope; icons: Material Symbols Rounded.

## Read before changing code

- `ARCHITECTURE_RULES.md` is binding: keep `app` thin, no side effects in Hilt providers,
  source-specific logic in per-source classes, don't grow the listed hotspot files, and
  performance changes need measured proof. It also holds the DB-paging pattern and the
  library presentation-coherence rules.
- `LIBRARY_DB_PAGING_PLAN.md` is the live handoff for the library paging/performance work.
- `UI_REDESIGN_PLAN.md` tracks the visual redesign: what's done, what's left, and the design decisions.
- `CURRENT_STATUS.md`, `ROADMAP.md` and the other plan docs are historical (April-July 2026).

## Build and test

The agent shell doesn't export `JAVA_HOME`; set it first (Android Studio's bundled JDK 21):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest                      # all modules, ~1000 tests
./gradlew :feature:library:testDebugUnitTest     # one module
```

- `app` tests also compile `app/src/sharedTest` (shared with `androidTest`); the ViewModel
  test factory is `createViewModel()` in `AppViewModelTestSupport.kt`.
- A test that creates a ViewModel under `Dispatchers.setMain` must call `clearViewModel()`
  before `resetMain()`, or its collectors leak and fail an unrelated `runTest` later.
- In Robolectric tests, don't suspend inside `runBlocking` on the main thread waiting for work
  that resumes on Main (anything behind a `Dispatchers.IO` hop): the looper can't run and the
  suite hangs with no timeout. Poll while idling the looper instead (`waitForUiState { ... }`).
  If `testDebugUnitTest` stops progressing, `jstack` the Gradle Test Executor JVM.
- Room schema is v28 (`core/database/.../UnifiedReaderDatabase.kt`), `exportSchema = false`,
  no destructive fallback: every version bump needs a migration.

## Release build (signed)

`app/build.gradle.kts` signs release builds from `ANDROID_SIGNING_*` env vars (CI uses the
same contract). Locally, the passwords live in the macOS Keychain and a helper outside the
repo supplies them without printing them:

```bash
~/.config/yabai/release-signing.sh run ./gradlew :app:assembleRelease
~/.config/yabai/release-signing.sh check         # re-verify the saved passwords
```

Keystore `~/yabai.jks`, alias `key0`. The release cert SHA-256 must be
`b26aaf3bed6351e72b8bc6e6d7d4655848902e22eedc7d512bbb6680597a5baa`; check with
`apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
(`~/Library/Android/sdk_new/build-tools/36.1.0/apksigner`).

## GitHub Actions

- `ci.yml` runs `testDebugUnitTest` on every push and on PRs to `main` (30 min job timeout).
- `release-apk.yml` builds a signed release APK. Pushing a version tag (`0.6.0` or `v0.6.0`)
  also publishes a GitHub Release with `yabai-<tag>.apk` attached; the tag must equal `versionName`
  in `app/build.gradle.kts` (the job fails otherwise), so bump `versionName` and `versionCode` first.
  Manual runs (`gh workflow run release-apk.yml --ref main`) only keep the APK as the run's
  `unified-reader-release-apk` artifact. Signing comes from the `ANDROID_SIGNING_*` repo secrets.

## Device

- SDK `~/Library/Android/sdk_new`; adb is `~/Library/Android/sdk_new/platform-tools/adb`
  (not on PATH). Emulator AVD `yabai_tablet_35` (Android 15, 1200x1920 tablet; RuntimeShader works):
  `ANDROID_SDK_ROOT=~/Library/Android/sdk_new ~/Library/Android/sdk_new/emulator/emulator -avd yabai_tablet_35 -no-snapshot -no-boot-anim -no-audio -gpu host`.
  Debug builds are fine there; judging feel and performance still needs the user's tablet.
- The installed app is a release build. Update it with `adb install -r` of a **signed release**
  APK, which keeps its data. A debug build has a different signature and would need an
  uninstall, which wipes the library and journal: never uninstall.
- Never install a build with a lower Room version than the device has (no downgrade path).
- On the OnePlus tablet, streaming `logcat` is suppressed; use `adb logcat -d`. `screenrecord`
  is blocked by SELinux. Library timings log under the `LibraryPerf` tag
  (`LibraryPerfLog.ENABLED`, `LibraryShelvesPerfLog.ENABLED`, both on by choice).
