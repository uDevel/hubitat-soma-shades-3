# SOMA Smart Shades 3 — Hubitat Zigbee Driver

## Device facts (from zigbee2mqtt, HA issue, Hubitat community)

- Endpoint: `0x0A` (10) — single endpoint
- Profile: `0x0104` (ZHA)
- Device type: `0x0202` (Window Covering)
- Manufacturer string: `WazombiLabs` (early firmware) or `SOMA`
- Model: `SmartShades3`
- Battery-powered End Device
- Input clusters:
  - `0x0000` Basic
  - `0x0001` Power Configuration (battery)
  - `0x0003` Identify
  - `0x0004` Groups
  - `0x0005` Scenes
  - `0x0102` Window Covering
- No output clusters

## Protocol notes

**Window Covering cluster (0x0102)**
- Cmd `0x00` UpOrOpen
- Cmd `0x01` DownOrClose
- Cmd `0x02` Stop
- Cmd `0x05` GoToLiftPercentage — 1-byte payload, 0–100
- Attr `0x0008` currentPositionLiftPercentage (uint8) — ZCL defines `0 = fully open`, `100 = fully closed`

**Power Configuration cluster (0x0001)**
- Attr `0x0021` batteryPercentageRemaining — uint8, in half-percent units (divide by 2)

## Hubitat convention (inversion required)

Hubitat `WindowShade` capability:
- `position`: `0 = closed`, `100 = open` — **opposite** of ZCL
- `windowShade` enum: `open | closed | partially open | opening | closing | unknown`

Translation: `hubitatPosition = 100 - zclLiftPercentage`.

## Driver plan

1. Metadata — capabilities: `WindowShade`, `Battery`, `Refresh`, `Configuration`, `HealthCheck`, `Actuator`, `Sensor`
2. Fingerprint for pairing auto-detection
3. Preferences — invert toggle, logging, position-close threshold
4. Commands — `open`, `close`, `setPosition`, `startPositionChange`, `stopPositionChange`
5. `parse()` — decode cluster 0x0102 attr reports and cluster 0x0001 battery
6. `configure()` — bind + configureReporting for position and battery
7. `refresh()` — read current position + battery
8. Raw `he cmd` strings because default `zigbee.command()` sends to endpoint 1; SOMA uses 0x0A

## Tasks

- [x] Gather device spec
- [x] Write v1 driver groovy
- [ ] User pairs device, shares fingerprint from Device Info if it differs
- [ ] Verify open/close/setPosition/stop behavior
- [ ] Verify battery reporting after pair + 1h
- [ ] Iterate on reporting min/max intervals if too chatty or too sparse
