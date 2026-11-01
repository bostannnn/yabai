# Launch splash · Book hop

Approved 2026-11-01 after three motion studies. Native launch artwork belongs to the Android launch resources; it runs before Compose can draw. It is the one playful exception to the app's otherwise restrained motion.

Oni crouches, pushes off, tucks its rounded mitten arms and feet, and returns to the book. The book dips under its weight, rocks on takeoff, flutters two page leaves, and rebounds on landing. Run once for 820 ms; never repeat or hold an already-ready app just to finish the animation. Android controls the handoff. Rotation and hot resume do not add an app splash overlay.

The splash uses each saved theme's background and primary. The face, eyes, and book cover have opaque background-colored fills. Draw the back pages, character, front cover, then resting paws; a page must never show through the eyes or body.

`art/` contains the approved editable vector layers. `../../../tools/gen_launch_splash.py` converts these into the static Android fallback and Android 12+ animated vector. Regenerate with `python3 docs/design/system/tools/gen_launch_splash.py`; verify generated resources and the icon mask with `--check`.

At rest the mark retains its 151 dp height. During the jump, the artwork gently fits toward the book's base to keep every frame inside Android's 192 dp splash icon circle. The complete body, horns, and feet remain visible. No clipped jump, additional splash activity, bitmap/video player, or minimum-duration delay.

Android 12+ plays the native AVD and follows the system animator duration scale, including disabled animations. Android 9–11 uses the matching static starting pose because the system splash compat implementation cannot animate its starting-window icon.

See [verification results](verification.md) for build, mask, palette, and device checks.
