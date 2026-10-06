# N0RMA: how it works, and what it cannot do

N0RMA is a detect-only tool. It listens with the radios already in your phone (Bluetooth and Wi-Fi) and tells you, in plain words, when something looks unusual. It never connects to, jams or interferes with anything, and nothing leaves your phone.

This page says exactly what each check does, the numbers it uses, and where it is blind. If a claim here is wrong, please open an issue.

## What it can and cannot see

| It can hear | It cannot hear |
|---|---|
| Bluetooth LE trackers: Apple Find My (AirTag and compatible), Tile, Samsung SmartTag, Chipolo | Cellular or GPS trackers (they have no Bluetooth or Wi-Fi signal a phone can hear) |
| Drones that broadcast Remote ID over Bluetooth or Wi-Fi | Drones that don't broadcast Remote ID, or fake or spoof it |
| Wi-Fi networks with camera-like or drone-like names or maker prefixes | Cameras that record locally or are not on Wi-Fi, or that use a normal-looking name |
| Apps on this phone that match a public stalkerware list | New, renamed or custom-built stalkerware; anything on another person's device or account |

A quiet screen is **not** a guarantee of safety.

## The checks

**Trackers.** Bluetooth advertisements are classified by their service UUIDs and manufacturer data. Normal Apple devices that are with their owner are counted and ignored. A tracker you have marked "this is mine" never raises a flag. For anything else:
- WATCH: an unknown tracker is currently in range (heard within the last 5 minutes).
- ALERT: it has been heard for at least 5 minutes, at least 5 times, and was at some point stronger than -70 dBm (roughly within a room or two).

**Following.** A tracker that is merely nearby is often someone else's. The stronger sign is the same one turning up wherever you go. Each time an unknown tracker is heard (at most once a minute) N0RMA records which "place" you are in. A place is a Wi-Fi fingerprint: hashed IDs of the 8 strongest nearby networks. There is no GPS and no location permission, and neighbors' network addresses are not stored in readable form. Two fingerprints are the same place when at least 30% of their networks overlap. A tracker counts as **following** when it is heard at **3 or more different places**, with **at least 2 sightings at each**, spread over **at least 30 minutes**. Then the status is ALERT with a timeline.
- Staying at home, or moving between only two places, never triggers it.
- Weakness: if your home Wi-Fi changes a lot (a router reboot, a new neighbor), one place can look like two. Thresholds are conservative guesses and have not been tuned on real stalking data.
- Needs Wi-Fi scanning on. Android allows a scan roughly every 30 seconds (newer versions throttle further).

**Drones.** Remote ID is an unauthenticated broadcast: anyone can transmit one. N0RMA therefore says "broadcast **claims** a drone", never "a drone is here". Position and operator location are shown live only and are never written to the history.

**Camera-like Wi-Fi.** Matches network names and maker prefixes typical of cheap cameras. WATCH when seen; ALERT when stronger than -60 dBm (very close). A match is a reason to look, not proof. Many ordinary devices look like this and many hidden cameras do not.

**Stalkerware scan (on tap).** Compares installed app package names to the Coalition Against Stalkerware indicator list (bundled in the app, CC BY 4.0, nothing downloaded) and lists non-system apps that hold accessibility, notification-listener or device-admin power. This needs the `QUERY_ALL_PACKAGES` permission, which Google Play does not allow, so N0RMA is distributed through F-Droid and GitHub. Matching is by package name only; a clean result is not a guarantee.

**Signal strength and distance.** Radar and "hot/cold" use signal strength. Indoors, walls, bodies and antennas can make a far device look near and the reverse. Treat the radar as a rough hint; direction comes only from the compass sweep.

## Evidence export

History can export a plain-text report: a summary, the following timeline, and every watch/alert event from the last 30 days. Each log line carries a hash that also covers the previous line, and the summary and timeline are covered by the starting hash, so editing, deleting or reordering anything after export is detectable.

What this does **not** prove: who created the report, or that the phone's own detections were correct. A hash chain only shows the text was not changed afterward. To make it stronger, send the final "CHAIN END" value to yourself or an advocate immediately; that fixes the time and content.

## Privacy

No internet permission, no accounts, no analytics. History stays on the phone and is deleted after 30 days (or immediately with "Delete all history"). Reports and exports go only where you send them.

## How well does it work? (honest status)

- Tested on **one** phone (TCL 5087Z, Android 11) and with unit tests of the detection logic. Android 12 and newer change Bluetooth and Wi-Fi scan permissions and throttling; it has not been verified there.
- Not yet tested against a full set of real AirTag, SmartTag, Tile, Chipolo, Pebblebee or Google Find Hub trackers, and the IETF DULT (unwanted-tracker) specification is not implemented.
- **False-positive and false-negative rates have not been measured.** We will publish them when there is a test corpus; until then, treat every number above as a starting point.

## How a determined person could defeat it

- A tracker that advertises rarely, or only briefly, can slip past short scan windows.
- A tracker wired to a car's power, or placed where you rarely go, may never be near you long enough.
- Modified firmware can change what the tracker broadcasts.
- A cellular or GPS tracker is invisible to this tool entirely.
- The most common stalking uses shared accounts, location sharing and stalkerware, not trackers; the in-app Digital safety checklist covers those.

## Credits

Stalkerware indicators: Coalition Against Stalkerware / Echap, [stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators), CC BY 4.0. For advice on staying safe: the National Domestic Violence Hotline (US 1-800-799-7233) and [techsafety.org](https://www.techsafety.org).
