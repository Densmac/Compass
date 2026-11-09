# Compass — Build Plan (rev 3, as built)

> Rev 3 corrections, which override anything below:
> - **Screen-up by rotation is 0:+Y, 90:+X, 180:−Y, 270:−X.** The rev 2 table had 90/270 swapped. `remapCoordinateSystem` arguments say where device axes map *to*.
> - Audit finding #1 was half wrong: `(AXIS_X, AXIS_Z)` does give the rear-camera bearing. Its 65° jump concern was right, and the pointing vector stands.
> - Ticks are every **3°** (counted from the references), majors every 30°; haptic detents follow: 3° (<60°/s), 15° (<180°/s), 30°, cardinals, north.
> - Moving tilt crosshair removed at the owner's request; only the fixed crosshair remains.
> - Sound: synthesized clicks (`res/raw`) at every 30°, with a soft second note 51 ms later on cardinals, matching the owner's recording. Setting: Tick sounds.
> - Info labels are written in full (South Latitude, East Longitude).
> - Primitive-less vibrators (the OnePlus test phone) fall back to `View.performHapticFeedback`.
> - Detents use the raw bearing (filter lag made ticks late); the readout shows the displayed dial angle.
> - Sensor stop timeout is 500 ms on the frame flow, none on the derived UI flow, so they do not stack.
> - Calibration warning clears at accuracy ≥ MEDIUM (some phones never report HIGH).
> - North fades are a continuous function of angle; location uses HIGH_ACCURACY since network fixes have no altitude.
> - The wheel can be grabbed and flung (`DialSpin`): it follows the finger; at release the landing is chosen (whole turns to suit the fling) and one ease-out lands exactly on the real heading, with no correction after. Very weak nudges spring straight home. Ticks and sounds come from the wheel while it spins, and the readout follows it.
> - Appearance setting: Dark (default) or Light.
> - Baseline Profile and Macrobenchmark are not built. A `benchmark` build type measures release frame times by hand.

App: **Compass** · package `com.densmac.compass` · repo `~/StudioProjects/Compass` (created at Phase 0)
Reference: 3 screenshots (heading 242°SW, 310°NW, 0°N). Dark, minimal, orange accent.

## 1. What the screen is (read from the references)

| Element | Behaviour |
|---|---|
| Heading readout (top) | `242°SW` — big bold numerals + cardinal (1–2 letters, 8-point). Turns **orange** at north. Tabular figures so digits don't jitter. |
| Dial disc | Dark graphite disc (≈`#1B1C1E`) with a soft radial highlight and a thin inner bevel ring, on pure black. **Counter-rotates by −heading**, so N on the dial always points to real north. The fixed 12 o'clock position is the current heading. |
| Degree labels | `0, 30 … 330` on the outer band. Move with the dial, stay **upright**. |
| Ticks | Minor tick every 2° (verify against refs in Phase 3 — 2° vs 3°), major every 30° (longer, brighter). Radial, inside the labels. |
| "Heat" spread | Ticks within ≈±30° of 12 o'clock tint orange with smooth falloff (brightest/tallest at 12, fading to grey). |
| N/E/S/W | Inner ring, move with dial, upright. N orange, others white. |
| North marker | Orange triangle at the dial's 0°, just inside the "0" label. |
| Deviation arc | Orange arc on the outer rim from 12 o'clock to the north marker, the **shorter** way. |
| North state | One shared threshold (|Δ| < 1°, with 0.5° hysteresis) drives everything together: arc collapses to a short **pill/dash** at 12 o'clock, the 0 marker sits directly under it, a broad warm glow fades into the top of the disc, readout turns orange. All fade over ~150 ms — no pop at 359.x/0. |
| Rim dots | 4 small grey dots on the rim at the dial's cardinal points (rotate with dial). |
| Centre crosshair | Fixed thin grey crosshair + smaller white crosshair offset by tilt (a built-in level). |
| Info rows | `South Latitude 12°30′00″` (hemisphere letter + "L"), `East Longitude 45°15′00″`, `Elevation 250.0 m`. Hairline dividers. Label grey, value white bold. |
| Settings | Gear, top-right. |
| Bottom bar | Tabs **Compass** / **Level**. Dark grey bar, selected white, unselected grey. |

### 1.1 Measured layout (from 1152×2575 refs; express as fractions, clamp for short/tall/tablet)
- Dial diameter ≈ **82.5% of width** (cap: `min(0.825·w, 0.42·h, 520dp)`), centre at ≈ 43% of height.
- Heading readout centre ≈ 14.5% of height; cap height ≈ 0.075·w.
- Gear: 24dp icon, ~16dp from right, aligned to top inset + 56dp.
- Info rows: side padding ≈ 11% of width, row pitch ≈ 5.6% of height, start ≈ 69% of height.
- Bottom bar ≈ 9% of height incl. nav inset.
- Short screens: rows compress first, then dial shrinks; never overlap. Respect cutouts/insets (edge-to-edge).

## 2. Non-negotiables → how each is met
- **Smooth**: sensor work on its own thread; dial animates in the **draw phase only**; static tick ring cached in a GraphicsLayer; frame loop runs only while moving.
- **No ANR**: nothing blocking on main — sensors on a HandlerThread, location callbacks on a background executor, altitude/DataStore on IO. StrictMode `penaltyDeath` in debug.
- **North always north**: dial rotation = −heading; heading computed from an explicit pointing vector, correct for every display rotation and for upright use.
- **Haptics**: detent ticks from the sensor-side heading, speed-tiered, never a continuous buzz.
- **MVVM**: ViewModel + UDF, `StateFlow`, Hilt, interfaces for repos, pure-Kotlin domain with tests.

## 3. Stack (mirrors TRUE-OBD-KMP, already proven on this machine)
- Gradle 9.6.0, AGP 9.4.1 with **built-in Kotlin** (do **not** apply `org.jetbrains.kotlin.android`); plugins: `android.application`, `kotlin.compose`, `ksp`, `hilt` — same set TRUE-OBD-KMP's app uses. Kotlin 2.3.21, KSP 2.3.12, JDK 17 toolchain.
- compileSdk 36, targetSdk 36, **minSdk 31** (raised from 28: guarantees `VibrationEffect.Composition` + `PRIMITIVE_LOW_TICK`, SplashScreen API; cuts a whole haptics fallback tier).
- Compose BOM 2026.05.00, Material3, activity-compose 1.13.0, lifecycle 2.10.0 (`viewmodel-compose`, `runtime-compose`).
- Hilt 2.59.2. **No navigation-compose** — two tabs = `rememberSaveable` tab state + `AnimatedContent`.
- Coroutines 1.10.2, DataStore Preferences 1.1.7.
- play-services-location 21.3.0; `androidx.core:core-location-altitude` **pinned** (1.0.1 per Codex — confirm latest stable at Phase 0).
- Font: Inter (OFL) bundled in `res/font`.
- Tests: JUnit, Turbine, coroutines-test. Baseline Profile + Macrobenchmark **deferred to Phase 8**, only after the dial passes on device.
- Single `:app` module. Packages: `data/`, `domain/`, `ui/`, `di/`.

## 4. Architecture

```
SensorManager ─(HandlerThread)─▶ OrientationRepository: cold callbackFlow<Orientation>
                                   (pointingBearingMag, pitch, roll, accuracy, |B| µT, timestampNs)
Fused location ─(bg Executor)──▶ LocationRepository: Flow<GeoFix>   (lat, lon, wgs84Alt, mslAlt?, timeMs)
DataStore ─────────(IO)────────▶ SettingsRepository: Flow<Settings>
                                     ▼
domain (pure): PointingBearing, HeadingFilter(One-Euro), Declination, DetentDetector, Formatters
                                     ▼
CompassViewModel
  headingTarget: StateFlow<HeadingSample>   (wrapped 0..360 final heading + reference, conflated)
  uiState:       StateFlow<CompassUiState>  (changes ≤ once per whole degree)
  haptic events emitted inside the same upstream chain (no separate long-lived collector)
                                     ▼
CompassScreen: collectAsStateWithLifecycle → frame-loop display angle → draw-phase reads only
```

### 4.1 Orientation — explicit pointing vector (fixes Codex #1, #2)
- Sensor preference: `TYPE_ROTATION_VECTOR` → `TYPE_GEOMAGNETIC_ROTATION_VECTOR` → accel+mag `getRotationMatrix`. `SENSOR_DELAY_GAME`, callbacks on a dedicated `HandlerThread`.
- `R = getRotationMatrixFromVector(rv)` maps **device → world (East, North, Up)**. Column `c` of R is device axis `c` in world coords. Bearing of any device vector `v`: `atan2(v_E, v_N)`.
- **Screen-up vector `u`** by `display.rotation` (canonical table, same as Google's samples):

  | rotation | screen-up `u` (device axes) | equivalent remap |
  |---|---|---|
  | 0 | +Y | — |
  | 90 | −X | `(AXIS_Y, AXIS_MINUS_X)` |
  | 180 | −Y | `(AXIS_MINUS_X, AXIS_MINUS_Y)` |
  | 270 | +X | `(AXIS_MINUS_Y, AXIS_X)` |

- **Pointing vector `p = u + (−Z)`** (screen-up plus rear-camera direction), projected onto the horizontal plane. Flat → `u` dominates; upright → `−Z` dominates; both share the same horizontal bearing whenever the top is tilted away from the user, so the transition is **continuous — no mode switch, no 65° jump**. Horizontal magnitude ≥ 1 in normal use.
- If `|p_horizontal| < 0.3` (unusual poses, e.g. top edge tipped back toward the user while upright), **hold** the last heading and flag `headingUnstable` (dims the readout). No remap ordering issue: one vector, one rotation table.
- Pitch/roll from the gravity direction (column 2 of R / `TYPE_GRAVITY`) for crosshair + Level.
- Emits accuracy (`onAccuracyChanged`) and |B| µT (from `TYPE_MAGNETIC_FIELD` at a low rate) for warnings.
- **Required tests** (instrumented with recorded rotation vectors + one manual device pass): face-up, upright, face-down, 45° tilt, each display rotation 0/90/180/270, landscape-natural tablet. Assert known physical bearings (N/E/S/W ±2°).

### 4.2 Heading filter (domain, pure, unit-tested)
- Filter on the unit circle (`sin`/`cos` → `atan2`) — no wrap discontinuity.
- One-Euro filter: `minCutoff ≈ 1.0 Hz`, `beta ≈ 0.02`, `dCutoff = 1 Hz` (tune on device). Runs on the sensor thread.
- Also maintains a **continuously unwrapped sensor heading** (accumulates shortest per-sample deltas, timestamped). This is the **single authoritative source for haptic crossings** (Codex #4). The display spring is never used for haptics.

### 4.3 True north / declination (fixes Codex #3, #6)
- `GeomagneticField(lat, lon, fix.altitude /* WGS84 geodetic, as the API expects */, fix.time)`.
- Recompute when: a new fix moves >1 km, the fix's altitude changes >500 m, the North setting changes, or the cached value is >24 h old. Fix older than 6 h → still used but label shows "True (approx.)".
- Final heading = filtered magnetic + declination (when True). Readout degree, cardinal, dial and arc all derive from that **one** final value.
- Reference is always visible: tiny `TRUE` / `MAG` caption under the readout. When declination first becomes available the dial **animates** (spring) to the new value, and haptics are **suppressed for 400 ms** so the correction doesn't fire a burst of ticks.
- Location: Fused `requestLocationUpdates(request, backgroundExecutor, listener)` — callbacks never on main. `PRIORITY_BALANCED_POWER_ACCURACY`, 10 s, only while the Compass tab is STARTED. Last-known shown immediately.
- Elevation: `AltitudeConverterCompat.addMslAltitudeToLocation` on `Dispatchers.IO`, wrapped in `runCatching` (IOException → show ellipsoid altitude with `≈`, or hide if `!hasAltitude()`).
- Location permission is optional; compass works without it (rows show `—` + "Enable location").

### 4.4 Lifecycle ownership (fixes Codex #5)
- Repos expose **cold** flows; `awaitClose { unregisterListener / removeLocationUpdates }`.
- The **only** subscriber is the UI via `collectAsStateWithLifecycle` (`minActiveState = STARTED`) on a `stateIn(viewModelScope, WhileSubscribed(2_000))`. Haptic emission happens in an `onEach` inside that chain — no independent collectors that could keep sensors alive.
- Level tab has its own VM with the same pattern; switching tabs removes the other tab from composition → its sensors stop.
- Verified by a test: background the app → `SensorManager` listener count goes to 0 within 2.5 s (fake SensorManager wrapper).

### 4.5 ViewModel
- `headingTarget: StateFlow<HeadingSample>` (conflated, ~50 Hz).
- `uiState: StateFlow<CompassUiState>` — `@Immutable`, `distinctUntilChanged` on **rounded** degree + slow fields → readout recomposes ≤ once per degree.
- `CompassUiState(degree, cardinal, isNorth, reference, latText, lonText, elevText, calibrationNeeded, interference, unstable, permission)`.
- No `Context` in the VM.

### 4.6 Haptics (fixes Codex #9, #10)
- minSdk 31 ⇒ `VibratorManager.defaultVibrator`, `VibrationEffect.startComposition()`, `PRIMITIVE_LOW_TICK/TICK/CLICK` all available. Still check `areAllPrimitivesSupported(...)` **per primitive** at startup; any unsupported → that tier falls back to `View.performHapticFeedback(CLOCK_TICK)` (no permission, respects system setting). Never build a composition containing an unsupported primitive.
- `<uses-permission android:name="android.permission.VIBRATE"/>` (normal permission).
- Tiers (from the unwrapped sensor heading):
  | Boundary | Primitive | Fires when angular speed |
  |---|---|---|
  | 2° minor | LOW_TICK 0.35 | < 45°/s |
  | 10° | TICK 0.5 | < 180°/s |
  | 30° major | TICK 0.75 | always |
  | N/E/S/W | CLICK 0.7 | always |
  | North lock | CLICK 1.0 + TICK 0.4 (40 ms gap) | on entering north state |
- Rules: one event per crossed boundary; if one sample crosses several, play only the **highest-priority** one (never replay skipped ones); re-arm requires moving ≥0.6° back past the boundary; global minimum gap 40 ms (minors dropped first); suppressed for 400 ms after reference switch, accuracy change, or a sample jump >20°. Worst case ≈ 22 minor ticks/s at 44°/s — crisp, not a buzz. Tune on real hardware.
- Vibrate calls on a single-thread executor. Settings: on/off, Subtle/Normal (scales amplitude).

### 4.7 Rendering (fixes Codex #7, #8)
- **Display angle loop**: a `LaunchedEffect` tied to lifecycle. It **suspends** (via `snapshotFlow { target }.first { it differs }`) when settled, then runs `withFrameNanos` stepping a critically-damped spring (ω ≈ 18 rad/s) toward the nearest unwrap of the target (`target + 360k` closest to displayed — never the long way). Stops when |Δ| < 0.02° and |v| < 0.05°/s. Target comes from `collectAsStateWithLifecycle`, so it is real Compose state. `displayed` is a `mutableFloatStateOf` read only in draw/graphicsLayer lambdas → invalidates draw, never recomposition.
- Layers:
  1. **Disc** — `drawWithCache`: gradient brushes, rim ring, shadow built in the cache block (depends only on size). No heading reads in the cache block.
  2. **Tick ring** — base grey ticks + majors recorded into a `rememberGraphicsLayer()` **only when size/density change**, with explicit size and `pivotOffset` = centre; per frame only `rotationZ = −displayed` (cheap RenderNode property change).
  3. **Heat overlay** — in `onDrawBehind`: the ~32 ticks within ±32° of 12 o'clock, orange, smoothstep alpha + length boost. Precomputed sin/cos tables.
  4. **Labels** — 12 degree labels + 4 cardinals laid out once (`TextMeasurer`, `remember(size, density, fontScale)`); per frame only `drawText(layoutResult, topLeft)` at rotated positions.
  5. **North marker, rim dots, deviation arc, north pill/glow** — small paths/arcs in `onDraw`.
  6. **Crosshairs** — fixed + tilt-offset (own low-pass).
  Layers 3–6 issue ~60 draw ops/frame on the UI thread — well within budget; only layer 2 is GPU-only rotation.
- Readout: `Text` with `fontFeatureSettings = "tnum"`; colour animated on north state.
- Colours: bg `#000000`, disc `#1B1C1E`→`#141516`, tick `#5A5C60`, major `#C9CACC`, text `#F2F2F2`, secondary `#8A8B8E`, accent `#FF5A1F`, divider `#2A2B2D`, nav `#1C1C1E`.
- Dark only, edge-to-edge.
- **Refresh rate**: no forced global display mode. Measure on device first; only if an LTPO panel drops to 60 Hz while the dial moves, set a frame-rate hint on the dial's view **only while the loop is running**.

### 4.8 Warnings / calibration
- Accuracy `UNRELIABLE`/`LOW`, or |B| outside 25–65 µT for >1.5 s → pill under readout "Calibrate: move phone in a figure-8" (tap → sheet with animation). Clears at `HIGH`.
- No magnetometer → friendly full-screen message; Level still works.

### 4.9 Level tab
- `TYPE_GRAVITY` (fallback low-passed accelerometer). Same visual language: disc + bubble crosshair, pitch/roll readouts, orange + CLICK haptic within ±0.5° (with hysteresis). Own VM.

### 4.10 Settings (bottom sheet)
North reference (True/Magnetic) · Haptics (on/off, Subtle/Normal) · Units (m/ft) · Coordinates (DMS/decimal) · Keep screen on. DataStore; first frame uses defaults, never blocks.

## 5. Performance & ANR guardrails
- StrictMode thread + VM policies in debug, `penaltyDeath` on main-thread disk/network.
- No `runBlocking`. DataStore via `stateIn(initial = defaults)`.
- R8 full mode. LeakCanary in debug (cheap, kept).
- Phase 8: Baseline Profile + Macrobenchmark `FrameTimingMetric` on a fake spinning source. Target P90 frame < 8 ms at 120 Hz, no janky frames in steady state, and **0 frames produced** when the phone is still (loop idles).

## 6. Testing
- Unit: pointing-vector bearing for all rotations/poses, One-Euro (convergence, wrap at 359/1), unwrap/shortest delta, DetentDetector (hysteresis, priority, no replay, speed tiers, suppression windows), declination refresh rules, cardinal mapping (8-point boundaries), DMS rounding carry (59.9995″).
- ViewModel tests with fakes + Turbine; lifecycle test (listeners unregistered when backgrounded).
- Screenshot tests at 0°, 242°, 310° compared side-by-side with the references at the same viewport (1152×2575 @ matching density).
- Device matrix: OnePlus (the reference device), a Pixel, a Samsung; landscape-natural tablet; upright use.

## 7. Phases (each ends with a build + device check)
0. **Repo + build gate**: `~/StudioProjects/Compass`, `git init`, wrapper 9.6.0, catalog, Hilt+KSP with built-in Kotlin, StrictMode, CLAUDE.md, this plan → `docs/PLAN.md`. Gate: `./gradlew assembleDebug testDebugUnitTest` green, app launches to themed empty screen.
1. Domain + tests (pointing bearing, filter, unwrap, detents, formatters, declination rules).
2. Orientation repo + lifecycle + debug readout; verify bearings on device in all poses.
3. Dial rendering with a fake heading slider — polish against the refs (measured layout, tick count, north state).
4. Real heading → frame loop → haptics on hardware.
5. Location, elevation, permission flow, True/Mag.
6. Calibration/interference UI, Settings sheet.
7. Level tab.
8. Baseline profile, macrobenchmark, refresh-rate check, icon (adaptive + monochrome), splash.

## 8. Decisions (defaults chosen)
- Default **True North** (Magnetic without a fix), reference always labelled.
- Portrait-locked on phones.
- **minSdk 31**.
- Minor tick 2° (confirm in Phase 3).
- Inter typeface. Hilt kept; navigation-compose dropped.

## 9. Codex audit log (rev 1 → rev 2)
| # | Sev | Finding | Resolution |
|---|---|---|---|
| 1 | BLOCKER | `AXIS_X, AXIS_Z` remap gives the screen-normal, and switching at 65° jumps | Replaced with explicit pointing vector `u + (−Z)` — continuous, no mode switch (§4.1) |
| 2 | MAJOR | Display-rotation table / ordering unspecified | Table added, single-vector approach, per-rotation tests (§4.1) |
| 3 | MAJOR | Declination refresh too narrow; silent ref switch | Refresh rules, WGS84 altitude, visible TRUE/MAG, animated + haptic-suppressed switch (§4.3) |
| 4 | MAJOR | Which heading drives haptics unclear | Unwrapped sensor heading is authoritative (§4.2, §4.6) |
| 5 | MAJOR | `shareIn(appScope)` can keep sensors alive | Cold flows, single lifecycle-aware subscriber, test (§4.4) |
| 6 | MAJOR | Fused callbacks default to main | Background executor; converter on IO with error handling (§4.3) |
| 7 | MAJOR | Unconditional frame loop; provider lambda doesn't invalidate | Loop idles when settled; target is collected Compose state (§4.7) |
| 8 | MAJOR | drawWithCache / GraphicsLayer misuse risks | Cache block heading-free; layer recorded on size change with pivot (§4.7) |
| 9 | MAJOR | LOW_TICK is API 31, minSdk 28 | minSdk → 31, per-primitive checks, VIBRATE permission (§3, §4.6) |
| 10 | MAJOR | 2° detents can buzz | Speed tiers, priority, no replay, 40 ms gap (§4.6) |
| 11 | MAJOR | Built-in Kotlin + KSP/Hilt build risk; unpinned altitude lib | Same plugin set as TRUE-OBD-KMP; Phase 0 build gate; version pinned (§3, §7) |
| 12 | MINOR | No measured layout | §1.1 added |
| 13 | MINOR | North state misread as a dot | Pill + shared threshold + fades (§1) |
| 14 | MINOR | Over-engineering; forced display mode | Dropped nav-compose, deferred benchmarks, conditional frame-rate hint (§3, §4.7) |
