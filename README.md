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

- **Invert position** — flip if the motor reports backwards (shade says "open" when actually closed).
- **Closed / open threshold** — tolerance for rounding into the `closed` / `open` states.
- **Battery report interval** — max minutes between battery reports (60–1440; the device-side minimum is 1 hour).
- **Motion settle timeout** — seconds after the last position report before `windowShade` is finalized (default 3, range 1–30).
- **Descriptive text logging** — human-readable `log.info` lines for commands, decoded position, and battery.
- **Debug logging** — adds the parsed `parse map:` and outgoing `he cmd` dumps. Auto-off after 30 minutes.
- **Trace logging** — same as debug but routed to `INFO` so it survives debug filtering; each outgoing command is logged one line at a time.

## Known quirks

- The ZCL lift attribute is `0 = fully open`, `100 = fully closed`. Hubitat's `position` is the opposite, so the driver inverts. If a motor is mounted/mapped opposite of expectation, toggle **Invert position**.
- Tilt is not exposed — roller shades don't have tilt.
- Battery reports are slow (battery-powered end device). Give it up to an hour after pairing.
- The motor does not expose a stall / motor-blocked attribute over Zigbee, so the driver cannot report jams. Stalls are handled internally by the motor firmware.

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
| `0x0102` | `0x0017` | operationalStatus | bitmap8, optional — lift bits 2–3: `0=stopped, 1=opening, 2=closing` |
| `0x0001` | `0x0021` | batteryPercentageRemaining | uint8 in half-percent units (divide by 2) |

## License

MIT — see [`LICENSE`](./LICENSE).
