# Book hop verification · 2026-11-01

- The normal app debug APK builds successfully with package `com.example.unifiedreader`.
- Seven focused app unit checks pass: launch palette parity, finite AVD targets, opaque layers, and return to rest.
- Thirteen focused component checks pass: splash readiness hold, layout, Material imports, and design rules.
- `python3 docs/design/system/tools/gen_launch_splash.py --check` passes: 820 ms, 90 keyframes, maximum conservative masked radius 94.77 / 96 dp.
- The native Android 15 test passes for every launch palette: the animated vector inflates and the eyes render opaque on a transparent canvas.
- Native launch recordings at system animator duration scale 2 show the complete body and rounded limbs, book rocking/page flutter, landing, and no clipped horns. With scale 0 the icon stays in its resting pose.
- The headless emulator's scale-1 recording dropped most early animation frames. These debug emulator checks establish visual correctness; they are not startup performance measurements.
- Android 9–11's matching static vector was checked structurally, not run on a device.

Device checks used a temporary isolated Android 15 AVD and the application ID `com.example.unifiedreader.splashpreview`. The shared emulator and the user's installed release were not changed. The normal APK was rebuilt after the isolated preview build, and the temporary AVD was removed. Local logs and recordings remain under `output/splash-verification/`.

The full existing app instrumentation sources fail to compile because `LibraryTopBarSearchTest` still passes removed `seriesTitle` / `downloadBadgeCount` arguments and omits `contextTitle` / `downloadsButtonState`. The new native splash test was compiled and run independently; the unrelated toolbar test was left unchanged. This is not a full instrumentation-suite pass.
