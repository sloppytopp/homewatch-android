# Contributing
Welcome! Ground rules that keep the project trustworthy:
1. **Detect-only.** No jamming, spoofing, transmitting or tracking of other people.
2. **Nothing leaves the phone.** No internet permission, analytics or third-party SDKs.
3. **Be honest about limits.** Wording in the UI and README must not over-promise safety or precision (Remote ID is a claim; signal-strength distance is rough).
4. **Calm, accessible UI.** Dark and low-glare; status is never colour alone.
5. **Tests.** Radio parsing and detection logic live in pure Kotlin under `detect/` with JUnit tests - add a test with every fix (`./gradlew testDebugUnitTest`).

Build: JDK 17 + Android SDK 35, `./gradlew assembleDebug`. Please don't commit screenshots, logs or exports that contain real network names, MAC addresses or locations.

## Testing and test reports
The most useful contribution is a test report: see [docs/TESTING.md](docs/TESTING.md) for the protocol (own devices, a room you control, everyone consenting). Misses and false alarms are the most valuable results.
