/**
 *  SOMA Smart Shades 3 (Zigbee) — Hubitat driver
 *
 *  Device: WazombiLabs / SOMA "SmartShades3"
 *    Endpoint 0x0A, Profile 0x0104 (ZHA), Device type 0x0202 (Window Covering)
 *    In clusters: 0x0000, 0x0001, 0x0003, 0x0004, 0x0005, 0x0102
 *    Battery-powered End Device
 *
 *  Hubitat WindowShade convention: position 0 = closed, 100 = open.
 *  ZCL Window Covering (0x0102 attr 0x0008) convention: 0 = fully open, 100 = fully closed.
 *  We invert between the two. If your motor is installed/mapped opposite, flip `invertPosition`.
 */

import groovy.transform.Field

@Field static final String DRIVER_VERSION = "1.1.0"

@Field static final Integer CLUSTER_BASIC          = 0x0000
@Field static final Integer CLUSTER_POWER          = 0x0001
@Field static final Integer CLUSTER_IDENTIFY       = 0x0003
@Field static final Integer CLUSTER_WINDOW_COVER   = 0x0102

@Field static final Integer ATTR_BATTERY_PERCENT   = 0x0021
@Field static final Integer ATTR_LIFT_PERCENT      = 0x0008
@Field static final Integer ATTR_OPERATIONAL_STATUS = 0x0017

@Field static final Integer CMD_OPEN               = 0x00
@Field static final Integer CMD_CLOSE              = 0x01
@Field static final Integer CMD_STOP               = 0x02
@Field static final Integer CMD_GOTO_LIFT_PERCENT  = 0x05
@Field static final Integer CMD_IDENTIFY           = 0x00

@Field static final String  SOMA_ENDPOINT          = "0x0A"

metadata {
    definition(
        name:      "SOMA Smart Shades 3",
        namespace: "uDevel",
        author:    "uDevel",
        importUrl: "https://raw.githubusercontent.com/uDevel/hubitat-soma-shades-3/main/soma-smart-shades-3.groovy"
    ) {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "HealthCheck"
        capability "Refresh"
        capability "Sensor"
        capability "WindowShade"

        attribute "lastCheckin", "string"

        command "identify", [[name: "seconds", type: "NUMBER",
                              description: "Identify duration in seconds (1-255). Default 30."]]

        fingerprint profileId: "0104",
                    endpointId: "0A",
                    inClusters: "0000,0001,0003,0004,0005,0102",
                    outClusters: "",
                    manufacturer: "WazombiLabs",
                    model: "SmartShades3",
                    deviceJoinName: "SOMA Smart Shades 3"

        fingerprint profileId: "0104",
                    endpointId: "0A",
                    inClusters: "0000,0001,0003,0004,0005,0102",
                    outClusters: "",
                    manufacturer: "SOMA",
                    model: "SmartShades3",
                    deviceJoinName: "SOMA Smart Shades 3"
    }

    preferences {
        input name: "invertPosition", type: "bool",
              title: "Invert position",
              description: "Flip if fully open reports as closed or vice versa.",
              defaultValue: false

        input name: "closedThreshold", type: "number",
              title: "Closed threshold (%)",
              description: "Hubitat position at or below this is reported as closed.",
              defaultValue: 2, range: "0..20"

        input name: "openThreshold", type: "number",
              title: "Open threshold (%)",
              description: "Hubitat position at or above this is reported as open.",
              defaultValue: 98, range: "80..100"

        input name: "batteryReportMinutes", type: "number",
              title: "Battery report interval (minutes)",
              description: "Max time between battery reports. Must be \u2265 60 (device minimum is 1 hour).",
              defaultValue: 60, range: "60..1440"

        input name: "settleSeconds", type: "number",
              title: "Motion settle timeout (seconds)",
              description: "After the last position report, hold opening/closing this long before deciding the shade has stopped.",
              defaultValue: 3, range: "1..30"

        input name: "txtEnable", type: "bool", title: "Descriptive text logging", defaultValue: true
        input name: "logEnable", type: "bool", title: "Debug logging (auto-off in 30 min)", defaultValue: true
        input name: "traceEnable", type: "bool",
              title: "Trace logging (raw + parsed + outgoing at INFO level)",
              description: "When on, every parse() frame, decoded map, and outgoing he cmd is logged at INFO.",
              defaultValue: false
    }
}

// ---------- lifecycle ----------

def installed() {
    log.info "SOMA Smart Shades 3 installed"
    sendEvent(name: "windowShade", value: "unknown")
    sendEvent(name: "position", value: 0)
    runIn(2, "configure")
}

def updated() {
    log.info "SOMA Smart Shades 3 updated (v${DRIVER_VERSION})"
    if (logEnable) runIn(1800, "logsOff")
    else unschedule("logsOff")
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "Debug logging disabled"
}

// ---------- capability commands ----------

def open() {
    logInfo "open()"
    state.motorMoving = true
    markCommandTarget(100)
    sendEvent(name: "windowShade", value: "opening")
    return traceOut("open", [
        "he cmd 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x0102 ${CMD_OPEN} {}",
        "delay 300",
        refreshPositionCmd()
    ].flatten())
}

def close() {
    logInfo "close()"
    state.motorMoving = true
    markCommandTarget(0)
    sendEvent(name: "windowShade", value: "closing")
    return traceOut("close", [
        "he cmd 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x0102 ${CMD_CLOSE} {}",
        "delay 300",
        refreshPositionCmd()
    ].flatten())
}

def stopPositionChange() {
    logInfo "stopPositionChange()"
    state.motorMoving = false
    runIn(1, "settleWindowShade", [overwrite: true])
    return traceOut("stop", [
        "he cmd 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x0102 ${CMD_STOP} {}",
        "delay 300",
        refreshPositionCmd()
    ].flatten())
}

def startPositionChange(String direction) {
    if (direction == "open") return open()
    if (direction == "close") return close()
    log.warn "startPositionChange: unknown direction ${direction}"
    return []
}

def identify(seconds = 30) {
    Integer secs = clamp((seconds ?: 30) as Integer, 1, 255)
    logInfo "identify(${secs}s)"
    // IdentifyTime is uint16 little-endian; low byte + high byte (0 for values <= 255)
    String payload = zigbee.convertToHexString(secs, 2) + "00"
    return traceOut("identify", [
        "he cmd 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x0003 ${CMD_IDENTIFY} {${payload}}"
    ])
}

def setPosition(position) {
    Integer hubPos = clamp(position as Integer, 0, 100)
    Integer currentPos = (device.currentValue("position") ?: 0) as Integer
    if (hubPos == currentPos) {
        logInfo "setPosition(${hubPos}) \u2014 already at target, no-op"
        return []
    }

    Integer zclPct = hubToZcl(hubPos)
    logInfo "setPosition(${hubPos}) -> ZCL ${zclPct}%"

    state.motorMoving = true
    markCommandTarget(hubPos)
    sendEvent(name: "windowShade", value: hubPos > currentPos ? "opening" : "closing")

    String payload = zigbee.convertToHexString(zclPct, 2)
    return traceOut("setPosition", [
        "he cmd 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x0102 ${CMD_GOTO_LIFT_PERCENT} {${payload}}",
        "delay 500",
        refreshPositionCmd()
    ].flatten())
}

// ---------- refresh / configure ----------

def refresh() {
    logInfo "refresh()"
    return traceOut("refresh", [
        refreshPositionCmd(),
        "delay 200",
        refreshBatteryCmd()
    ].flatten())
}

def refreshPositionCmd() {
    return "he rattr 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x0102 0x0008 {}"
}

def refreshBatteryCmd() {
    return "he rattr 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x0001 0x0021 {}"
}

def configure() {
    logInfo "configure()"
    // Clamp defensively in case an older stored value predates the range widening.
    Integer batteryMaxMin = clamp(((batteryReportMinutes ?: 60) as Integer), 60, 1440)
    Integer batteryMaxSec = batteryMaxMin * 60

    List<String> cmds = []

    // Binding so the device sends unsolicited reports to the hub
    cmds += "zdo bind 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x01 0x0102 {${device.zigbeeId}} {}"
    cmds += "delay 200"
    cmds += "zdo bind 0x${device.deviceNetworkId} ${SOMA_ENDPOINT} 0x01 0x0001 {${device.zigbeeId}} {}"
    cmds += "delay 200"

    // Reporting: currentPositionLiftPercentage  (uint8, min 0s, max 1h, delta 1%)
    cmds += zigbee.configureReporting(CLUSTER_WINDOW_COVER, ATTR_LIFT_PERCENT,
            DataType.UINT8, 0, 3600, 1, [destEndpoint: 0x0A])

    // Reporting: operational status  (bitmap8, min 0s, max 1h, any change)
    // If the device doesn't support it, this fails silently — we fall back to position-diff inference.
    cmds += zigbee.configureReporting(CLUSTER_WINDOW_COVER, ATTR_OPERATIONAL_STATUS,
            DataType.BITMAP8, 0, 3600, 1, [destEndpoint: 0x0A])

    // Reporting: batteryPercentageRemaining  (uint8, min 1h, max configurable, delta 2 = 1%)
    cmds += zigbee.configureReporting(CLUSTER_POWER, ATTR_BATTERY_PERCENT,
            DataType.UINT8, 3600, batteryMaxSec, 2, [destEndpoint: 0x0A])

    cmds += "delay 500"
    cmds += refreshPositionCmd()
    cmds += "delay 200"
    cmds += refreshBatteryCmd()

    sendEvent(name: "checkInterval", value: batteryMaxSec * 2 + 600,
              displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

    return traceOut("configure", cmds)
}

def ping() { refresh() }

// ---------- parse ----------

def parse(String description) {
    sendEvent(name: "lastCheckin", value: new Date().format("yyyy-MM-dd HH:mm:ss"), displayed: false)

    Map descMap = zigbee.parseDescriptionAsMap(description)
    traceMap(descMap)

    if (!descMap) {
        logDebug "parse: could not decode"
        return
    }

    Integer cluster = descMap.clusterInt ?: (descMap.cluster ? Integer.parseInt(descMap.cluster, 16) : null)
    Integer attr    = descMap.attrInt    ?: (descMap.attrId  ? Integer.parseInt(descMap.attrId, 16)  : null)

    if (descMap.profileId == "0000") {
        logDebug "ZDO frame: ${descMap}"
        return
    }

    // Catchall ZCL frames (cluster-specific commands from the device) come in as
    // profileId != null, clusterId set, attrId absent. The most common one from
    // SOMA is a Default Response (cmd 0x0B) acknowledging our Open/Close/Stop.
    if (descMap.profileId && descMap.attrId == null && descMap.command) {
        Integer zclCmd = Integer.parseInt(descMap.command, 16) & 0xFF
        if (zclCmd == 0x0B && descMap.data) {
            Integer forCmd = Integer.parseInt(descMap.data[0], 16) & 0xFF
            Integer status = Integer.parseInt(descMap.data[1], 16) & 0xFF
            Integer respCluster = descMap.clusterInt ?:
                    (descMap.clusterId ? Integer.parseInt(descMap.clusterId, 16) : null)
            Map<Integer, Map<Integer, String>> cmdNames = [
                (CLUSTER_WINDOW_COVER): [(0x00): "Open", (0x01): "Close",
                                         (0x02): "Stop", (0x05): "GoToLiftPct"],
                (CLUSTER_IDENTIFY):     [(0x00): "Identify"]
            ]
            String cmdName = cmdNames.get(respCluster, [:])
                                     .get(forCmd, "0x${String.format('%02X', forCmd)}")
            String statusOk = status == 0x00 ? "SUCCESS" : "0x${String.format('%02X', status)}"
            logInfo "ack: ${cmdName} -> ${statusOk}"
            return
        }
        logDebug "catchall: cluster=${descMap.clusterId} cmd=0x${descMap.command} data=${descMap.data}"
        return
    }

    if (cluster == CLUSTER_WINDOW_COVER && attr == ATTR_LIFT_PERCENT) {
        return handleLiftReport(descMap)
    }
    if (cluster == CLUSTER_WINDOW_COVER && attr == ATTR_OPERATIONAL_STATUS) {
        return handleOperationalStatus(descMap)
    }
    if (cluster == CLUSTER_POWER && attr == ATTR_BATTERY_PERCENT) {
        return handleBatteryReport(descMap)
    }
    if (cluster == CLUSTER_BASIC) {
        logDebug "Basic cluster attr ${descMap.attrId} = ${descMap.value}"
        return
    }
    logDebug "unhandled: cluster=${descMap.clusterId ?: descMap.cluster} attr=${descMap.attrId} value=${descMap.value}"
}

private handleLiftReport(Map descMap) {
    if (descMap.value == null) return
    Integer zclPct = Integer.parseInt(descMap.value, 16) & 0xFF
    if (zclPct > 100) {
        logDebug "Ignoring out-of-range lift value ${zclPct}"
        return
    }
    Integer hubPos = zclToHub(zclPct)
    Integer prev   = (state.lastPosition != null) ? (state.lastPosition as Integer) : hubPos

    // Device echoes the commanded target back before the motor actually moves.
    // Suppress those "target announcement" reports so `position` doesn't flap.
    if (isTargetAnnouncement(hubPos)) {
        logDebug "target announcement: ZCL ${zclPct} hubPos=${hubPos} (suppressed)"
        return
    }
    clearCommandTarget()

    sendEvent(name: "position", value: hubPos, unit: "%")

    // Infer motion from position deltas. If the device also reports operational status,
    // that handler wins because it arrives first with a definitive moving/not-moving bit.
    if (state.motorMoving != true) {
        if (hubPos > prev) {
            logInfo "moving: position=${hubPos} (opening, ZCL ${zclPct})"
            sendEvent(name: "windowShade", value: "opening")
        } else if (hubPos < prev) {
            logInfo "moving: position=${hubPos} (closing, ZCL ${zclPct})"
            sendEvent(name: "windowShade", value: "closing")
        } else {
            logInfo "position=${hubPos} (ZCL ${zclPct}, unchanged)"
        }
    } else {
        logInfo "moving: position=${hubPos} (ZCL ${zclPct})"
    }

    state.lastPosition = hubPos

    // Finalize open/closed/partial after the stream of reports stops.
    Integer settle = (settleSeconds ?: 3) as Integer
    runIn(settle, "settleWindowShade", [overwrite: true])
}

private handleOperationalStatus(Map descMap) {
    if (descMap.value == null) return
    Integer status = Integer.parseInt(descMap.value, 16) & 0xFF
    // bits 2..3 describe lift motion: 0=stopped, 1=opening, 2=closing
    Integer lift = (status >> 2) & 0x03

    if (lift == 0) {
        logInfo "operationalStatus=${String.format('0x%02X', status)} lift=stopped"
        state.motorMoving = false
        settleWindowShade()
    } else if (lift == 1) {
        logInfo "operationalStatus=${String.format('0x%02X', status)} lift=opening"
        state.motorMoving = true
        sendEvent(name: "windowShade", value: "opening")
    } else if (lift == 2) {
        logInfo "operationalStatus=${String.format('0x%02X', status)} lift=closing"
        state.motorMoving = true
        sendEvent(name: "windowShade", value: "closing")
    } else {
        logDebug "operationalStatus=${String.format('0x%02X', status)} lift=${lift} (reserved)"
    }
}

def settleWindowShade() {
    state.motorMoving = false
    Integer hubPos = (device.currentValue("position") ?: 0) as Integer
    Integer closeTh = (closedThreshold ?: 2) as Integer
    Integer openTh  = (openThreshold  ?: 98) as Integer

    String shadeState
    if (hubPos <= closeTh)      shadeState = "closed"
    else if (hubPos >= openTh)  shadeState = "open"
    else                        shadeState = "partially open"

    logInfo "settled: position=${hubPos} shade=${shadeState}"
    sendEvent(name: "windowShade", value: shadeState)
}

private handleBatteryReport(Map descMap) {
    if (descMap.value == null) return
    Integer raw = Integer.parseInt(descMap.value, 16) & 0xFF
    if (raw == 0xFF) {
        logDebug "battery: 0xFF (unknown), ignoring"
        return
    }
    // ZCL batteryPercentageRemaining is in 0.5% units
    Integer pct = clamp((raw / 2) as Integer, 0, 100)
    logInfo "battery=${pct}%"
    sendEvent(name: "battery", value: pct, unit: "%")
}

// ---------- logging helpers ----------

// Treat unsaved preferences as the declared defaults (txt/log = true, trace = false)
private boolean isTrace() { traceEnable == true }
private boolean isTxt()   { txtEnable   == null ? true : (txtEnable   == true) }
private boolean isDebug() { logEnable   == null ? true : (logEnable   == true) }

private void traceMap(Map descMap) {
    if (isTrace())      log.info  "parse map: ${descMap}"
    else if (isDebug()) log.debug "parse map: ${descMap}"
}

private List traceOut(String label, List cmds) {
    if (isTrace()) {
        log.info "--> ${label} outgoing:"
        cmds.each { log.info "    ${it}" }
    } else if (isDebug()) {
        log.debug "--> ${label} outgoing: ${cmds}"
    }
    return cmds
}

private void logInfo(String msg) {
    if (isTrace() || isTxt()) log.info msg
}

private void logDebug(String msg) {
    if (isTrace())      log.info  msg
    else if (isDebug()) log.debug msg
}

// ---------- command-target tracking ----------
// The motor replies to open/close/GoToLiftPercentage with an immediate attribute
// report of the commanded target, BEFORE physical motion begins. Without handling,
// the `position` attribute flaps (current -> target -> real motion). We remember
// the expected target for ~2s and suppress reports that match it.

@Field static final Long TARGET_WINDOW_MS = 2500L

private void markCommandTarget(Integer hubTargetPos) {
    state.expectedTarget = hubTargetPos
    state.expectedTargetUntilMs = now() + TARGET_WINDOW_MS
}

private void clearCommandTarget() {
    state.remove("expectedTarget")
    state.remove("expectedTargetUntilMs")
}

private boolean isTargetAnnouncement(Integer hubPos) {
    if (state.expectedTarget == null || state.expectedTargetUntilMs == null) return false
    if (now() > (state.expectedTargetUntilMs as Long)) {
        clearCommandTarget()
        return false
    }
    return hubPos == (state.expectedTarget as Integer)
}

// ---------- helpers ----------

private Integer zclToHub(Integer zclPct) {
    Integer v = invertPosition ? zclPct : (100 - zclPct)
    return clamp(v, 0, 100)
}

private Integer hubToZcl(Integer hubPos) {
    Integer v = invertPosition ? hubPos : (100 - hubPos)
    return clamp(v, 0, 100)
}

private Integer clamp(Integer v, Integer lo, Integer hi) {
    if (v == null) return lo
    return Math.min(hi, Math.max(lo, v))
}
