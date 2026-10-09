# Privacy policy - N0RMA for Android
_Last updated: 2026-10-09_

**Short version: N0RMA collects nothing and sends nothing. It has no internet permission.**

## What the app does with data
- It listens to Bluetooth and Wi-Fi signals around you and works out, on your phone, whether anything looks like a tracker, drone or camera-like network.
- Sightings, room sweeps, survey points, your beep log, your room inspection checklists, your list of devices you marked as yours, and settings are stored **only on your phone** (in the app's private storage). Events, beeps and survey points are deleted automatically after 30 days. **Settings -> History -> Delete all history** erases everything at once; uninstalling the app erases it too.
- Drone "operator positions" from Remote ID broadcasts are shown on screen while heard and are **never saved**.
- There are no accounts, no ads, no analytics, no crash reporting and no third-party SDKs.

## Permissions and why
| Permission | Why |
|---|---|
| Bluetooth scan / "Nearby devices" | Listen for trackers and Remote ID drones |
| Location (precise) | Android 11 and older require it for Bluetooth and Wi-Fi scanning; also used, on request, to set your home and during a survey walk. N0RMA never uploads it |
| Nearby Wi-Fi devices (Android 13+) | Scan Wi-Fi networks |
| Foreground service + notifications | Keep scanning in the background and chime when something is flagged |
| Vibrate | Optional vibrate-only alerts, and the optional vibration guide in the finder |
| Camera | Only for the **Lens finder** (Rooms tab), and only when you open it. The camera and flash run as a torch so small lens glints stand out. Each picture is analysed in memory on the phone and discarded: **nothing is saved, recorded, uploaded or shared**, and the app has no internet permission to send it anywhere. Android asks you first, and the light and camera are released the moment you close the finder. You can deny the permission and everything else still works |

## Things you choose to do
- **Address search** (Settings -> Home location): Android's own location service looks the address up. That lookup leaves your phone; typing coordinates or using GPS does not. N0RMA itself sends nothing.
- **Exports** (KML / WiGLE CSV): saved as files where you choose. N0RMA never uploads them. If you upload a WiGLE file yourself, WiGLE publishes it on a public map.
- **"Open the DeFlock map"** (Smart tab): opens the community license-plate-camera map (maps.deflock.org) in your browser. That visit is between you and your browser; N0RMA sends nothing.
- **Reports** (evidence report, sweep report): built on the phone and handed to the share sheet when you tap Export. N0RMA never sends them anywhere on its own.
- **"Open WiGLE"**: copies a network's MAC address and opens wigle.net in your browser. That visit is between you and your browser.

## Children
N0RMA is not directed at children and collects no personal information from anyone.

## Changes and contact
Changes to this policy will be committed to this repository. Questions: open an issue at https://github.com/sloppytopp/n0rma-android/issues.
