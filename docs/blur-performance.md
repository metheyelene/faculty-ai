# Liquid Glass blur — performance model

How the app uses RenderEffect backdrop blur (Haze 1.0.2) and what it costs.

## Where blur runs

| Surface | Blur | Notes |
|---|---|---|
| Floating bottom dock | **Yes** (Haze child) | The single blurred surface in the app |
| Glass surfaces (cards, chips, inputs, dialogs) | No | Translucent fill + hairline border + lit edge; no per-view RenderEffect |
| GlassTopBar | No | THICK strength — near-opaque so it stays readable without blur |
| Dialogs / sheets | No | THICK glass over a dimmed background |

Blur is a **progressive enhancement**, never a readability dependency: every blurred
surface has a fallback tint (`fallbackTint` / classic translucent surface) that renders
identically legible on devices without hardware blur support.

## Capability gates

A device gets the real blur only when **all** are true:

1. **API 31+** — RenderEffect exists (`Build.VERSION.SDK_INT >= 31`, see `rememberBlurSupported()`).
2. **Motion allowed** — the system "Remove animations" accessibility setting is off
   (`rememberReducedMotion()`); reduced motion forces the static fallback surface.
3. **Dock visible** — the Haze source modifier is attached to the NavHost only while
   `dockProgress > 0.01` (see `FacultyAINavHost.hazeActive`).

Gate 3 is the important one: the source layer re-renders its subtree into a graphics
layer every frame while attached. Detaching it on secondary screens (Notes, Calendar,
Settings, detail routes), dialogs, onboarding, and whenever the keyboard is open means
those states carry **zero** blur overhead.

## Cost model (Haze 1.0, per Chris Banes' benchmarks on Pixel 6)

- Blur itself: dock-sized child only — roughly a 60dp × screen-width strip, not fullscreen.
- Source capture: fullscreen graphics-layer re-record per frame **while attached**.
- Progressive/masked variants are not used — uniform blur is the cheapest mode.

## What to measure (on-device)

```bash
# Frame timing while fling-scrolling each top-level screen with the dock present
adb shell dumpsys gfxinfo com.bits.facultyai framestats
```

Checklist:

- [ ] Home / Timetable / Attendance / Students fling scroll: p50 frame < 8ms, p90 < 16ms
- [ ] Assistant screen with keyboard open: dock hidden, no blur layer in hierarchy
      (verify with Layout Inspector: `HazeNode` absent on secondary screens)
- [ ] Dock exit animation: no hitch when the source detaches at `dockProgress == 0`
- [ ] API 26–30 device/emulator: fallback surface, no RenderEffect calls
- [ ] Reduced-motion enabled: fallback surface, no continuous animations

## Tuning knobs

| Knob | Where | Effect |
|---|---|---|
| `blurRadius = 22.dp` | `KineticBottomNavigation.glassDockSurface` | Lower = cheaper, sharper |
| `noiseFactor = 0.08f` | same | Visual grain; near-free |
| `glassBlurTint` alpha | `Color.kt` | Higher alpha = more legible, less "glassy" |
| `hazeActive` predicate | `FacultyAINavHost` | Tighten/loosen when the source is attached |
