# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A single-file **Hubitat Elevation device driver** (`soma-smart-shades-3.groovy`) for the SOMA Smart Shades 3 Zigbee roller-shade motor. The driver runs inside Hubitat's sandboxed Groovy environment — it is pasted into the hub's **Drivers Code** editor, not compiled or run locally.

There is **no build system, no test runner, and no lint step.** Verification means: save the driver in Hubitat (the editor compiles it in the sandbox and surfaces errors), pair a real device, run `Configure`, and watch the live **Logs**. Do not invent build/test commands.

## Repository layout

- `soma-smart-shades-3.groovy` — the entire driver. This is the only shipped code.
- `packageManifest.json` / `repository.json` — Hubitat Package Manager (HPM) distribution metadata.
- `README.md` — user-facing install/usage/device-spec reference.
- `POST.md` — Hubitat community forum announcement draft.
- `tasks/` — maintainer's local dev notes (todo.md, lessons.md). **Gitignored — not present in a fresh clone.** If you do have it, `tasks/lessons.md` holds the Hubitat-sandbox gotchas; the key ones are inlined below.

## Releasing a new version

Version lives in **multiple places that must stay in sync** — bump all of them together:

1. `DRIVER_VERSION` constant at the top of `soma-smart-shades-3.groovy`.
2. `packageManifest.json`: top-level `version`, `drivers[0].version`, `dateReleased`, and a new `releaseNotes` entry.

The `importUrl` / HPM `location` URLs point at `main` on GitHub raw, so changes go live to existing installs when merged to `main`. Commit messages follow conventional style (e.g. `feat: add identify command (v1.1.0)`).

## Architecture — the non-obvious parts

These are the design decisions that aren't apparent from a single function and that you must preserve:

**Position is inverted between two conventions.** Hubitat `WindowShade`: `position` 0 = closed, 100 = open. ZCL Window Covering (cluster `0x0102` attr `0x0008`): 0 = fully open, 100 = fully closed. Every position crosses `zclToHub()` / `hubToZcl()` (gated by the `invertPosition` preference). Never read or write a raw position without going through these.

**Commands use raw `he cmd` / `he rattr` strings, not `zigbee.command()`.** The device lives on **endpoint `0x0A`**, but Hubitat's `zigbee.*` helpers target endpoint 1. So all outgoing frames are hand-built strings addressed to `SOMA_ENDPOINT`. When adding a command, follow the existing `he cmd 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x<cluster> 0x<cmd> {<payload>}` pattern.

**Target-announcement suppression.** SOMA motors echo the *commanded target* back as a position report before physical motion starts, which makes `position` flap. On every command, `markCommandTarget()` records the expected target for `TARGET_WINDOW_MS` (2.5s); `handleLiftReport()` calls `isTargetAnnouncement()` to drop reports matching it. Don't remove this when touching position handling.

**Motion state (`opening`/`closing`) is held through the whole travel**, then finalized. Flow: command entry sets `windowShade=opening|closing` + `state.motorMoving=true` → intermediate lift reports keep that state → a `settleWindowShade()` timer (the `settleSeconds` pref, default 3s, `runIn(..., overwrite:true)`) finalizes to `open`/`closed`/`partially open` using `openThreshold`/`closedThreshold`. If the device reports `OperationalStatus` (`0x0017`) it is the authoritative motion signal and wins; current SOMA firmware doesn't, so position-delta inference in `handleLiftReport()` is the live path.

**`parse()` dispatch order matters.** It special-cases ZDO frames (`profileId == "0000"`) and ZCL Default Response catchall frames (cmd `0x0B`, ack decoding) *before* the cluster/attribute routing. The ack decoder is **cluster-aware** (`cmdNames` keyed by cluster) so Window-Covering and Identify acks don't collide — preserve that when adding clusters.

## Hubitat sandbox gotchas

- **No `private`/`static` modifiers on script-level declarations.** The driver is a top-level Groovy script, not a class. Use `@Field static final Type NAME = value` (with `import groovy.transform.Field`) for compile-time constants — this is why every constant uses `@Field`.
- **Preference `defaultValue` is a UI hint, not a stored value.** Settings stay `null` until the user clicks **Save Preferences** once. Always coerce nulls to the declared default when reading a preference (see the `isDebug()` / `isTxt()` helpers and the `?: <default>` pattern throughout). Never gate behavior on a bare `if (somePref)`.

## Logging

Three independent levels, all routed through helpers — use them, don't call `log.*` directly for normal output:

- `logInfo()` — descriptive text (gated by `txtEnable`).
- `logDebug()` — parse maps + outgoing dumps (gated by `logEnable`, auto-off after 30 min via `logsOff`).
- `traceEnable` — same content as debug but routed to `INFO` so it survives debug filtering; `traceMap()` / `traceOut()` add raw-frame and per-line outgoing-command dumps.
