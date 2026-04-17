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

## Preferences

- **Invert position** — flip if motor reports backwards (shade says "open" when actually closed).
- **Closed / open threshold** — tolerance for rounding into the `closed` / `open` states.
- **Battery report interval** — max seconds between battery reports (min is fixed at 1h).

## Known quirks

- The ZCL lift attribute is `0 = fully open`, `100 = fully closed`. Hubitat's `position` is the opposite, so the driver inverts. If your motor is mounted/mapped opposite of expectation, toggle **Invert position**.
- Tilt is not exposed — roller shades don't have tilt.
- Battery reports are slow (battery-powered end device). Give it up to an hour after pairing.
