# Homewatch for Android - design (draft 1)

**Goal:** a standalone, detect-only Android app that gives someone an honest answer to "is anything near my home
tracking or watching me?" using the phone's own Bluetooth and Wi-Fi radios. No account, no cloud, no analytics.
Sister project of github.com/sloppytopp/homewatch (Linux, MIT). Never jams/spoofs/transmits.

## Platform
Test device: TCL 5087Z, Android 11 (API 30), arm64, BLE yes, no Wi-Fi Aware; OEM power manager kills background apps ("Restrict") -> needs foreground service + battery-optimization exemption prompt. Toolchain installed locally: JDK 17 (~/jdk), Android SDK (~/Android/Sdk), platform 35.
Kotlin + Jetpack Compose, minSdk 26 (Android 8), target latest. Package `io.github.sloppytopp.homewatch`.
Build with Gradle CLI; test on a real phone over USB (emulators have no BLE/Wi-Fi scanning).

## Detection (port of the Python tool; shared test vectors)
- BLE: ASTM F3411 Remote ID (service data 0xFFFA); trackers: Apple Find My *separated* payload (len >= 0x10), Tile
  (0xFEED/0xFD84), Samsung SmartTag (0xFD5A), Chipolo. "Owner nearby" Apple payloads are ignored.
- Wi-Fi: Remote ID vendor IE (OUI FA:0B:BC) from ScanResult information elements (API 30+), drone/camera SSID + vendor
  patterns, new-AP log. Honest limits: Android throttles scans; no full-LAN scan (Android blocks it).
- Same status model: 5 tiles -> here 4 (drone, tracker, camera-like network source, RF/sensor note). Worst-source-wins;
  tracker only ALERT when persistent (>=5 min) and close; Remote ID wording is "claims a drone" (spoofable).
- Operator coordinates shown live only, never persisted. Events kept 30 days, on-device (Room/SQLite).

## UI (dark, low-glare, safe to use outdoors at night)
- True-black background; muted desaturated green/amber/red; no pulsing by default. Each tile also has a word
  (OK/WATCH/ALERT) + icon - never color alone.
- Night mode: dim red-on-black. Alert style: soft chime | vibrate only | silent. Never a voice.
- Screens: tiles, proximity radar ("very close/close/far", labelled rough), drone map (real Remote ID positions vs
  home), events log, "if something is flagged" help, "I heard my sensor beep" log + report, hot/cold finder for a
  tracker, settings.
- **Home location**: (1) "Use my current location" (GPS, nothing sent) - most accurate; (2) address search field -
  shows found coordinates + distance from phone GPS, warns on mismatch, one-line disclosure that the address goes to
  a lookup service; (3) manual lat/lon. Stored on-device only.

## Service
Foreground service with a persistent, low-key notification (Android requirement). Permissions: BLUETOOTH_SCAN
(neverForLocation where possible), NEARBY_WIFI_DEVICES / ACCESS_FINE_LOCATION (Wi-Fi scan + home GPS), POST_NOTIFICATIONS.
Plain-language permission explainer before each request.

## Distribution / money (DECIDED 2026-10-04: free core + one-time Pro unlock)
Pay path: Google Play Billing in the Play build; Stripe Payment Link + offline-verified signed license key in the direct-download build. Never a Stripe link inside the Play build.
Free core detection forever (safety tool). One-time "Pro" (report export for police, scheduled sweeps,
multi-sensor, RTL-SDR) and/or donate link. GitHub APK + F-Droid free; Google Play ($25 dev account, 12-tester closed
test for 14 days, background-location/foreground-service declarations, anti-stalking framing).

## Later
Pair with the Linux/Raspberry Pi sensor; RTL-SDR over USB-OTG; ESP32 satellite nodes for room-level hints.

## Testing
JUnit with the same packet fixtures as the Python tests (Remote ID, AirTag separated/nearby, Tile, SmartTag,
malformed input never crashes); screenshot check of dark/night themes; manual field test on a physical phone.
