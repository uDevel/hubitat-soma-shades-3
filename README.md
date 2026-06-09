# SOMA Smart Shades 3 — Hubitat driver

Zigbee driver for the **SOMA Smart Shades 3** roller shade motor (`WazombiLabs` / `SOMA` — model `SmartShades3`) on Hubitat Elevation.

Implements `WindowShade`, `Battery`, `Refresh`, `Configuration`, and `HealthCheck` with proper mid-travel `opening`/`closing` state, target-announcement suppression, and ZCL Default-Response acking.

## Install

1. In Hubitat: **Drivers Code** → **+ New Driver** → paste the contents of [`soma-smart-shades-3.groovy`](./soma-smart-shades-3.groovy) → **Save**.
2. In the SOMA phone app: update the motor to the latest firmware and enable Zigbee mode (per motor).
3. In Hubitat: **Devices** → **Add Device** → **Zigbee** → **Start Zigbee pairing**, then put the motor into pairing mode.
4. If pairing completes as a generic "Device", set **Type** to **SOMA Smart Shades 3**, **Save Device**, then click **Configure**.

### Verifying the fingerprint

On the device page, run **Get Info** and open **Logs**. If the `manufacturer` string or `inClusters` list in the logged fingerprint doesn't match one of the two declared in the driver, open an issue with the fingerprint and it can be added so future installs auto-match.

## Controls

- `open`, `close`
- `stopPositionChange` — halt mid-travel
- `setPosition(0–100)` — 0 = closed, 100 = open (Hubitat convention)
- `startPositionChange("open"|"close")`
- `identify(seconds=30)` — tell the motor to reveal itself (blink / buzz / jog — varies by firmware). Useful when multiple SOMA shades are paired. Default duration 30 s, range 1–255.
- `refresh` — re-reads position and battery
- `readSettings` — reads the device-side settings and decodes them to the log (config status, mode, lift limits); also updates the `motorSpeed`, `batteryVoltage`, and `coveringType` attributes

## Motion tracking (opening / closing)

`windowShade` is held at `opening` or `closing` for the entire travel — not only at the moment a command is sent — so dashboards and automations can react to "shade is currently moving".

How it works:

1. **Command entry.** `open` / `close` / `setPosition` immediately set `windowShade=opening|closing` and flag motion in progress. The motor is also given its commanded target.
2. **Target-announcement suppression.** Many SOMA units echo the commanded target back as a position report *before* the motor physically moves (e.g. `position=0` for ~1 second on a close from 76%). Those echoes are detected via a 2.5-second window after each command and suppressed so the `position` attribute never flaps.
3. **Mid-travel position reports.** Every intermediate `currentPositionLiftPercentage` update emits a `position` event but keeps `windowShade` at `opening|closing`. Direction is taken from the command target; if the motor moves without a command (SOMA button / physical pull), direction is inferred from position deltas.
4. **Reached target (commanded moves).** When a commanded `open` / `close` / `setPosition` reports its target position, `windowShade` is finalized immediately — no settle delay.
5. **Settle (fallback).** When reports stop arriving without a target match — a manual / SOMA-app move, a mid-travel stop, or a stall — a **Motion settle timeout** timer (default 3s) finalizes `windowShade` to `open`, `closed`, or `partially open` from the last `position`.
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
reached target 0 — finalizing
settled: position=0 shade=closed
```

## Preferences

- **Invert position** — flip if the motor reports backwards (shade says "open" when actually closed).
- **Battery report interval** — max minutes between battery reports (60–1440; the device-side minimum is 1 hour).
- **Motion settle timeout** — seconds after the last position report before `windowShade` is finalized (default 3, range 1–30).
- **Descriptive text logging** — human-readable `log.info` lines for commands, decoded position, and battery.
- **Debug logging** — adds the parsed `parse map:` and outgoing `he cmd` dumps. Auto-off after 30 minutes.
- **Trace logging** — same as debug but routed to `INFO` so it survives debug filtering; each outgoing command is logged one line at a time.

## Known quirks

- The ZCL lift attribute is `0 = fully open`, `100 = fully closed`. Hubitat's `position` is the opposite, so the driver inverts. If a motor is mounted/mapped opposite of expectation, toggle **Invert position**.
- The Zigbee Window Covering cluster has no motion-status attribute (unlike Matter's `OperationalStatus`), so `opening` / `closing` is inferred from the streamed position reports rather than read directly.
- Tilt is not exposed — roller shades don't have tilt.
- Battery reports are slow (battery-powered end device). Give it up to an hour after pairing.
- The motor does not expose a stall / motor-blocked attribute over Zigbee, so the driver cannot report jams. Stalls are handled internally by the motor firmware.
- Motor speed is configured in the SOMA app and is read-only over Zigbee: the driver surfaces it as the `motorSpeed` attribute but cannot change it (writes are acked but ignored by the motor). The app's "swipe to move" toggle is not exposed over Zigbee at all.
- The `identify` command always ack's with `ack: Identify -> SUCCESS`, but the visible behavior (blink / buzz / jog) is firmware-dependent — some firmware revisions are silent. A silent ack still confirms the command reached the motor.

## Device spec

For reference when debugging pairing or writing related drivers.

- **Endpoint:** `0x0A` (10) — single endpoint
- **Profile:** `0x0104` (ZHA)
- **Device type:** `0x0202` (Window Covering)
- **Manufacturer string:** `WazombiLabs` (early firmware) or `SOMA`
- **Model:** `SmartShades3`
- **Power:** Battery-powered End Device

**Input clusters**

| Hex | Name |
|---|---|
| `0x0000` | Basic |
| `0x0001` | Power Configuration |
| `0x0003` | Identify |
| `0x0004` | Groups |
| `0x0005` | Scenes |
| `0x0102` | Window Covering |

**Output clusters:** none.

**Window Covering cluster (`0x0102`) — commands used**

| Cmd | Name | Payload |
|---|---|---|
| `0x00` | UpOrOpen | — |
| `0x01` | DownOrClose | — |
| `0x02` | Stop | — |
| `0x05` | GoToLiftPercentage | 1 byte, `0` (fully open) – `100` (fully closed) |

**Attributes consumed**

| Cluster | Attr | Name | Notes |
|---|---|---|---|
| `0x0102` | `0x0008` | currentPositionLiftPercentage | uint8, ZCL convention (inverted from Hubitat) |
| `0x0102` | `0x0014` | velocity | uint16, app-configured motor speed % (read-only) → `motorSpeed` |
| `0x0102` | `0x0000` | windowCoveringType | enum8 → `coveringType` attribute |
| `0x0102` | `0x0007` | configStatus | bitmap8, decoded to the log (operational / online / encoder / reversed) |
| `0x0102` | `0x0010`,`0x0011` | installedOpen/ClosedLimitLift | uint16, logged (diagnostics) |
| `0x0102` | `0x0017` | mode | bitmap8, decoded to the log (LED / reversed / calibration) |
| `0x0001` | `0x0021` | batteryPercentageRemaining | uint8 in half-percent units (divide by 2) |
| `0x0001` | `0x0020` | batteryVoltage | uint8 ×100 mV → `batteryVoltage` attribute (volts) |

## License

MIT — see [`LICENSE`](./LICENSE).
