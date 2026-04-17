# [RELEASE] SOMA Smart Shades 3 (Zigbee) driver

**Title suggestion:** `[RELEASE] SOMA Smart Shades 3 (Zigbee) driver`
**Category:** `Custom Drivers` (https://community.hubitat.com/c/comappsanddrivers/community-drivers)

---

SOMA pushed a firmware update that lets the **SOMA Smart Shades 3** motor join a Zigbee 3.0 network directly — no more SOMA Connect bridge required. Hubitat's built-in **Generic Zigbee Shade** driver mostly works, but it doesn't handle the motor's quirks around target-position echoes, does not hold `opening` / `closing` through travel, and doesn't decode the ZCL Default Response acks.

This is a purpose-built driver that fixes all of that.

## Supported devices

| Device | Manufacturer string | Model | Protocol | Status |
|---|---|---|---|---|
| SOMA Smart Shades 3 | `WazombiLabs` | `SmartShades3` | Zigbee 3.0 | ✅ Tested |
| SOMA Smart Shades 3 | `SOMA` | `SmartShades3` | Zigbee 3.0 | ✅ Fingerprint declared, same hardware |

If your motor reports a different manufacturer string, please post the fingerprint line from **Get Info → Logs** and I'll add it.

## Features

- **Full `WindowShade` capability** — `open`, `close`, `setPosition(0–100)`, `startPositionChange`, `stopPositionChange`, `refresh`.
- **True mid-travel motion state** — `windowShade` is held at `opening` / `closing` for the entire travel, not just at the moment a command is issued. Dashboards and Rule Machine can react to "shade is currently moving".
- **Target-announcement suppression** — SOMA motors echo the commanded target back as a position report *before* the motor physically moves (so `position` instantly jumps to the target, then back to the real position). This is suppressed so your `position` attribute never flaps.
- **ZCL Default Response decoding** — every command is acked with a human-readable log line (e.g. `ack: Close -> SUCCESS`), which makes debugging much easier.
- **Motion inference from either direction** — if the device supports the `OperationalStatus` (0x0017) attribute, that's the authoritative signal. If it doesn't (current SOMA firmware doesn't), direction is inferred from position deltas, so physical / SOMA-app-initiated motion is also tracked.
- **Settle timer** — `windowShade` is finalized to `open` / `closed` / `partially open` after motion stops, using configurable thresholds.
- **Battery reporting** — via the standard `Battery` capability.
- **Position inversion toggle** — for motors mounted backwards.
- **Tri-level logging** — descriptive text, debug, and "trace" (all-at-INFO for easier filter capture).

## Installation

**Option A — Hubitat Package Manager (recommended once listed).** Not yet submitted — I'll update this post when it's in HPM.

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

- **Invert position** — flip if the motor reports backwards.
- **Closed threshold** (default 2%) — Hubitat position at or below this reports as `closed`.
- **Open threshold** (default 98%) — Hubitat position at or above this reports as `open`.
- **Battery report interval** (60–1440 min, default 60) — max minutes between unsolicited battery reports. The device-side minimum is 1 hour.
- **Motion settle timeout** (1–30 s, default 3) — seconds after the last position report before `windowShade` is finalized.
- **Descriptive text logging** — human-readable `info` lines.
- **Debug logging** — parsed Zigbee maps and outgoing `he cmd` dumps. Auto-disables 30 min after save.
- **Trace logging** — same as debug but routed to `INFO` so it bypasses log-level filtering; outgoing commands get one line each.

## Commands

| Command | Description |
|---|---|
| `open` | Fully open |
| `close` | Fully close |
| `setPosition(0–100)` | 0 = closed, 100 = open (Hubitat convention) |
| `startPositionChange("open"\|"close")` | Begin motion in a direction |
| `stopPositionChange` | Halt mid-travel |
| `refresh` | Re-read position + battery |
| `configure` | Re-bind and re-configure reporting |

## How it works

A typical close cycle from position 76 now logs like this:

```
close()
--> close outgoing: [he cmd 0x... 0x0A 0x0102 1 {}, delay 300, he rattr ...]
ack: Close -> SUCCESS
target announcement: ZCL 100 hubPos=0 (suppressed)
moving: position=74 (ZCL 26)
moving: position=66 (ZCL 34)
moving: position=57 (ZCL 43)
...
moving: position=0 (ZCL 100)
settled: position=0 shade=closed
```

The `target announcement: ... (suppressed)` line is the key difference from a generic driver — without suppression, `position` jumps `76 → 0 → 74 → 66 …` which makes dashboards blink and breaks "shade is at 50%" automations.

## Source code

📦 **GitHub:** https://github.com/uDevel/hubitat-soma-shades-3
📄 **Raw driver:** https://raw.githubusercontent.com/uDevel/hubitat-soma-shades-3/main/soma-smart-shades-3.groovy
🪪 **License:** MIT

Issues, feature requests, and fingerprint contributions are welcome on GitHub Issues.

## Related threads

- [Soma Smart Shades 3 now supports Zigbee](https://community.hubitat.com/t/soma-smart-shades-3-now-supports-zigbee/140540) — original community announcement
- [Zigbee Window Shade driver with positioning for modules/motors that don't report position](https://community.hubitat.com/t/release-zigbee-window-shade-driver-with-positioning-for-modules-motors-that-dont-report-position/162203) — inspiration for some of the UX decisions

---

*First public release. Tested on SOMA Smart Shades 3 firmware `3.0.17+0` (ZCL v8, HW v5) on a Hubitat C-7.*
