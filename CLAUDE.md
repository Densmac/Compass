# Compass

Android compass (`com.densmac.compass`), Compose + MVVM + Hilt, minSdk 31. Design and rationale: `docs/PLAN.md`.

- Domain logic is pure Kotlin in `domain/` and unit-tested; add new logic there.
- Heading state is read only in draw lambdas (`CompassMotion`, `CompassDial`). Never read it in composition.
- Nothing blocking on main: sensors on a HandlerThread, location on an executor, altitude on IO.
- Test on the physical phone over adb, not an emulator (real magnetometer and vibrator).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`

## Release signing

`keystore.properties` and the key are never committed. The encrypted backup and restore steps are in the private `Densmac/compass-signing` repo. Every update must be signed with that key.
