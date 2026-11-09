# Compass

A minimal, instrument-style compass for Android, by [Densmac](https://github.com/Densmac).

North stays north: the dial counter-rotates as you turn, and the graduations click past under your thumb.

## Features

- **True or magnetic north**, always labelled, from a gyro-fused rotation vector
- **Fling the wheel**: it picks up momentum from your flick and lands exactly on the real heading
- **Haptic detents and tick sounds** as graduations pass, with a distinct feel at each cardinal point
- Latitude, longitude and elevation above sea level
- Bubble **level** tab
- **Dark and light** appearance
- Works without location permission (magnetic north only)

## Install

Download the APK from [Releases](../../releases/latest). Requires Android 12 or newer.

## Build

JDK 17 and the Android SDK (API 36):

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

To sign a release build, add a `keystore.properties` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew :app:assembleRelease`.

## How it stays smooth

Sensor work runs on its own thread and the dial animates in the draw phase only, so turning the phone never recomposes the screen. When the phone is still, nothing is drawn at all. Design notes are in [docs/PLAN.md](docs/PLAN.md).

## License

[MIT](LICENSE) © 2026 Densmac. Includes the [Inter](https://github.com/rsms/inter) typeface under the [SIL Open Font License](docs/licenses/Inter-OFL.txt).
