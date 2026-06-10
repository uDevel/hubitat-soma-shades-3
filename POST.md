# [RELEASE] SOMA Smart Shades 3 (Zigbee) driver

**Title suggestion:** `[RELEASE] SOMA Smart Shades 3 (Zigbee) driver`
**Category:** `Custom Drivers` (https://community.hubitat.com/c/comappsanddrivers/community-drivers)

---

SOMA pushed a firmware update that lets the **SOMA Smart Shades 3** motor join a Zigbee 3.0 network directly — no more SOMA Connect bridge required. Hubitat's built-in **Generic Zigbee Shade** driver mostly works, but the position value tends to jump around while the shade moves, and it doesn't keep the tile on `opening` / `closing` through the whole travel.

This is a purpose-built driver that fixes all of that.

## Supported devices

| Device | Manufacturer string | Model | Protocol | Status |
|---|---|---|---|---|
| SOMA Smart Shades 3 | `WazombiLabs` | `SmartShades3` | Zigbee 3.0 | ✅ Tested |
| SOMA Smart Shades 3 | `SOMA` | `SmartShades3` | Zigbee 3.0 | ✅ Fingerprint declared, same hardware |

If your motor reports a different manufacturer string, please post the fingerprint line from **Get Info → Logs** and I'll add it.

## Features

- **Full shade control** — `open`, `close`, set to any position (0–100%), start/stop, and refresh.
- **Live movement status** — the tile shows `opening` / `closing` the whole time the shade is moving, not just when you press the button, so dashboards and Rule Machine can react while it's in motion.
- **Instant final status** — when a move finishes, the tile updates to `open` / `closed` / `partially open` right away.
- **Rock-steady position** — no flickering or jumping in the position value while the shade moves. Physical pulls and moves made from the SOMA app are tracked too, not just commands from Hubitat.
- **Clear logs** — every command is confirmed in the log (e.g. `ack: Close -> SUCCESS`), which makes troubleshooting easy.
- **Extra info on the device page** — motor speed (as set in the SOMA app), battery voltage, and shade type.
- **Battery level** — via the standard `Battery` capability.
- **Invert-position toggle** — for shades mounted in reverse.
- **Three logging levels** — descriptive, debug, and trace, for easy troubleshooting.

## Installation

**Option A — Hubitat Package Manager (recommended).** In HPM, choose **Install → From a URL** and paste the package manifest:
```
https://raw.githubusercontent.com/uDevel/hubitat-soma-shades-3/main/packageManifest.json
```
After that, HPM offers updates automatically under **Update**. (If you've added my repository to HPM, it'll also turn up in keyword search.)

**Option B — Manual install (works today):**

1. **Hubitat UI** → **Drivers Code** → **+ New Driver** → **Import** → paste:
   ```
   https://raw.githubusercontent.com/uDevel/hubitat-soma-shades-3/main/soma-smart-shades-3.groovy
   ```
   → **Import** → **Save**.
2. In the **SOMA** phone app, update the motor to the latest firmware and enable **Zigbee mode** for each shade.
3. In Hubitat: **Devices** → **Add Device** → **Zigbee** → **Start Zigbee pairing**, then put the SOMA motor into pairing mode (long-press per SOMA's instructions).
4. If the device pairs as a generic "Device", change **Type** on the device page to **SOMA Smart Shades 3** → **Save Device** → click **Configure**.
5. Verify with the dashboard tile or by calling `refresh` from the device page.

[details="Advanced: verify the fingerprint"]
On the device page, click **Get Info**, then open the top-nav **Logs** page. The driver will print a fingerprint line — if the `manufacturer` or `inClusters` don't match either fingerprint in the driver, it's probably a new firmware revision and I'll add yours so future pairings auto-detect.
[/details]

## Preferences

- **Invert position** — flip if your shade reports open when it's actually closed.
- **Battery report interval** (60–1440 min, default 60) — how often the shade reports its battery level. The shade's own minimum is 1 hour.
- **Motion settle timeout** (1–30 s, default 3) — how long to wait after movement stops before locking in the final status.
- **Descriptive text logging** — readable status lines.
- **Debug logging** — extra detail; turns itself off after 30 minutes.
- **Trace logging** — deepest detail, for troubleshooting.

## Commands

| Command | What it does |
|---|---|
| `open` | Fully open the shade |
| `close` | Fully close the shade |
| `setPosition(0–100)` | Move to a specific position (0 = closed, 100 = open) |
| `startPositionChange("open"\|"close")` | Start moving in a direction |
| `stopPositionChange` | Stop the shade mid-move |
| `identify(seconds=30)` | Make the motor reveal itself (blink / buzz / jog) — handy when you have several shades |
| `readSettings` | Pull the shade's current settings into the logs |
| `refresh` | Re-read position and battery |
| `configure` | Sets up the shade's reporting — click once after pairing |

## How it works

A typical close from 76% logs like this (with descriptive logging on):

```
close()
ack: Close -> SUCCESS
target announcement: hubPos=0 (suppressed)
moving: position=74
moving: position=66
moving: position=57
...
moving: position=0
reached target 0 — finalizing
settled: position=0 shade=closed
```

The `target announcement: ... (suppressed)` line is the key difference from the generic driver. The motor briefly reports the *target* before it actually moves; without suppression the position would jump `76 → 0 → 74 → 66 …`, which makes dashboard tiles blink and trips up "shade is at 50%" automations. The `reached target` line is the new instant-finalize — the tile shows `closed` the moment it arrives.

## Source code

📦 **GitHub:** https://github.com/uDevel/hubitat-soma-shades-3
📄 **Raw driver:** https://raw.githubusercontent.com/uDevel/hubitat-soma-shades-3/main/soma-smart-shades-3.groovy
🪪 **License:** MIT

Issues, feature requests, and fingerprint contributions are welcome on GitHub Issues.

## Related threads

- [Soma Smart Shades 3 now supports Zigbee](https://community.hubitat.com/t/soma-smart-shades-3-now-supports-zigbee/140540) — original community announcement
- [Zigbee Window Shade driver with positioning for modules/motors that don't report position](https://community.hubitat.com/t/release-zigbee-window-shade-driver-with-positioning-for-modules-motors-that-dont-report-position/162203) — inspiration for some of the UX decisions

---

*Current version: 1.2.1. Tested on SOMA Smart Shades 3 firmware `3.0.17+0` on a Hubitat C-7.*
