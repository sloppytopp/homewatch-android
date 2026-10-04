# Homewatch for Android
Detect-only home counter-surveillance for Android. Sister project of [homewatch](https://github.com/sloppytopp/homewatch) (Linux).
It listens for **Remote ID drones** and **separated Bluetooth trackers** (Apple Find My / AirTag, Tile, Samsung SmartTag, Chipolo)
using only the phone's own Bluetooth radio. **No account, no cloud, no analytics - nothing leaves the phone.** It never jams, spoofs or transmits.

![dark, low-glare main screen](docs/screenshot.png)

## Status: v0.1 (early)
- [x] Dark, low-glare UI (true black, muted colors; every status is word + icon + color)
- [x] Bluetooth LE scanner as a foreground service, soft chime on alerts (never a voice)
- [x] Remote ID parser + tracker classifier, unit-tested (19 tests, same packet fixtures as the Linux tool)
- [ ] Wi-Fi scanning (Remote ID beacons, camera-like networks), proximity radar, drone map, home-location setup, event history, beep log
- [ ] Optional RTL-SDR support; one-time "Pro" extras. Core detection stays free.

## Read this first
- **Not a guarantee of safety.** A quiet screen means "nothing seen by this radio". Drones without Remote ID, SD-card cameras and cellular trackers are invisible to a phone.
- **Remote ID is unauthenticated.** Anyone can fake it, so an alert means a broadcast *claims* a drone. Operator coordinates are shown live and never stored.
- **Do not interfere with a drone** (shooting at or jamming one is a federal crime). Report it to law enforcement or the FAA.
- Apple "owner nearby" Find My signals are ignored on purpose: only *separated* trackers, and only when persistent and close, raise an alert.
- Android 11 and older only deliver Bluetooth scan results while the Location switch is on; Homewatch does not read, save or send location.
- Some phones kill background apps aggressively; the app links to battery settings. Tested on a TCL 5087Z, Android 11.

## Build
JDK 17 + Android SDK (platform 35). `./gradlew assembleDebug testDebugUnitTest`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

MIT licensed. Design notes: [docs/design.md](docs/design.md).
