# N0RMA defensive test protocol

**Purpose:** measure what N0RMA detects and misses, and publish both. Testing is done with equipment YOU own, in a space YOU control, with everyone present agreeing. Nothing here is a how-to for attacking anyone; N0RMA is the thing being tested.

## Ground rules (please read)
- Use only your own devices, your own phone, in your own room or lab. Never test near people who haven't agreed, in a public place, a school, a hospital, or anywhere with medical devices.
- Keep any transmitting test tool short (about 60 s per run) and away from other people's phones; Bluetooth pop-ups and Wi-Fi disruption affect everyone nearby, not just you.
- Follow your local laws. If you're unsure whether something is legal where you live, don't run it.
- Don't post other people's network names, addresses or location. Describe your setup generically.

## Setup
1. Install the beta from the Releases page, verify the checksum, grant permissions.
2. Phone info to record: model, Android version, N0RMA version.
3. Start scanning on the Status tab. Keep the phone about 1-3 m from the device under test.

## Test table (fill one row per run)
| # | Device under test (model/firmware) | What it was doing (idle / its standard demo or test mode for ~60 s) | Distance | N0RMA tile that changed | Time to flag | Message shown | Correct? (hit / miss / false alarm) | Notes |
|---|---|---|---|---|---|---|---|---|

Suggested runs (do what you have):
- **AirTag / SmartTag / Tile / Chipolo / Pebblebee** away from its owner: does the Tracker tile flag it? How long until it does?
- **Camera-like Wi-Fi:** a cheap IP camera or ESP32-CAM broadcasting its own network: does "Unknown device?" flag it?
- **Bluetooth pop-up flood:** a device or app that sends BLE pairing advertisements (your own, for ~60 s, in an empty room): does "Bluetooth pop-up flood?" go to watch? Does a few normal AirPods opening alone stay OK?
- **Evil twin / rogue AP (if you own a pentest AP):** same network name from a second radio: is it noticed? (Not implemented yet - a miss here is expected and useful to record.)
- **USB attack hardware on a computer YOU own** (keystroke injector, malicious cable): this is for the future Linux USB check; record what the OS reports (class, VID:PID).
- **Control runs (very important):** normal day, no attack tools. How many false alarms in an hour at home? In a busy place?

## What to send back
Open a GitHub issue titled "Test report: <device> on <phone>" with the filled table, plus screenshots with private details removed. Say whether you're happy to be credited.

## How results are used
All reports are summarised in a public results table (hits, misses, false alarms, phone models). Misses are listed prominently; they are the most valuable result.
