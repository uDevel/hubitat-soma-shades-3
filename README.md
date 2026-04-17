# SOMA Smart Shades 3 — Hubitat driver

Zigbee driver for the SOMA Smart Shades 3 motor (`WazombiLabs` / `SOMA` — model `SmartShades3`).

## Install

1. Hubitat UI → **Drivers Code** → **+ New Driver** → paste contents of `soma-smart-shades-3.groovy` → **Save**.
2. Enable Zigbee mode on the motor in the SOMA app (firmware update may be required first).
3. Hubitat UI → **Devices** → **Add Device** → **Zigbee** → **Start Zigbee pairing**.
4. If pairing completes as a generic "Device", set the driver to **SOMA Smart Shades 3**, then click **Configure**.

## Verify pairing

After pairing open the device page, hit **Get Info**, and copy the fingerprint line from **Logs**. If the `manufacturer` or `inClusters` don't match the two fingerprints in `metadata {}`, tell me and I'll add yours so auto-detection works next time.

## Controls

- `open`, `close`, `stop`
- `setPosition(0–100)` — 0 = closed, 100 = open (Hubitat convention)
- `startPositionChange("open"|"close")`, `stopPositionChange`
- `refresh` — re-reads position and battery

## Motion tracking (opening / closing)

`windowShade` is held at `opening` or `closing` for the entire travel — not only at the moment a command is sent — so dashboards and automations can react to "shade is currently moving".

How it works:

1. **Command entry.** `open` / `close` / `setPosition` immediately set `windowShade=opening|closing` and flag motion in progress. The motor is also given its commanded target.
2. **Target-announcement suppression.** Many SOMA units echo the commanded target back as a position report *before* the motor physically moves (e.g. `position=0` for ~1 second on a close from 76%). Those echoes are detected via a 2.5-second window after each command and suppressed so the `position` attribute never flaps.
3. **Mid-travel position reports.** Every intermediate `currentPositionLiftPercentage` update emits a `position` event but keeps `windowShade` at `opening|closing`. Direction is taken from the command target; if the motor moves without a command (SOMA button / physical pull), direction is inferred from position deltas.
4. **Optional `OperationalStatus` (0x0017).** If the device supports this attribute, its `stopped | opening | closing` bits are the authoritative motion signal. SOMA 3 does not seem to report it, so position-diff inference is the active path.
5. **Settle.** When reports stop arriving, a **Motion settle timeout** timer (default 3s) finalizes `windowShade` to `open`, `closed`, or `partially open` based on the last `position` and the open/closed thresholds.
6. **Manual stop.** `stopPositionChange` clears the motion flag immediately and kicks a short 1s settle.

End result in the live log for a typical close cycle:

```
close()
ack: Close -> SUCCESS
target announcement: ZCL 100 hubPos=0 (suppressed)
moving: position=74 (ZCL 26)
moving: position=66 (ZCL 34)
...
moving: position=0 (ZCL 100)
settled: position=0 shade=closed
```

## Preferences

- **Invert position** — flip if motor reports backwards (shade says "open" when actually closed).
- **Closed / open threshold** — tolerance for rounding into the `closed` / `open` states.
- **Battery report interval** — max seconds between battery reports (min is fixed at 1h).
- **Motion settle timeout** — seconds after the last position report before `windowShade` is finalized (default 3, range 1–30).
- **Debug logging** — per-frame `parse map:` + outgoing `he cmd` dumps. Auto-off after 30 minutes.
- **Trace logging** — same as debug but routed to `INFO` so it survives debug filtering; each outgoing command is logged one line at a time.

## Known quirks

- The ZCL lift attribute is `0 = fully open`, `100 = fully closed`. Hubitat's `position` is the opposite, so the driver inverts. If your motor is mounted/mapped opposite of expectation, toggle **Invert position**.
- Tilt is not exposed — roller shades don't have tilt.
- Battery reports are slow (battery-powered end device). Give it up to an hour after pairing.
