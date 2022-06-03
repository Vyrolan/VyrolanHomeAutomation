metadata {
    definition(
            name: "Voice Variables Child",
            namespace: "vyrolan",
            author: "Vyrolan",
            importUrl: "https://raw.githubusercontent.com/Vyrolan/VyrolanHubitat/main/Hubitat/Drivers/VoiceVariablesChild.groovy"
    ) {
        capability "Switch"
        capability "Switch Level"

        attribute "Value", "number"
        attribute "LastUpdated", "number"
    }

    preferences {
        section("Settings:") {
            input(
                    name: "levelScaling",
                    type: "number",
                    title: "Level Scaling Factor",
                    description: "Factor used to scale the Level for setting the Value",
                    defaultValue: 1
            )
            input(
                    name: "resetValue",
                    type: "number",
                    title: "Reset Value",
                    description: "Value to reset to immediately after value is set (for event-like indicators)"
            )
            input(
                    name: "debugLogEnable",
                    type: "bool",
                    title: "Enable debug logging",
                    defaultValue: true
            )
            input(
                    name: "infoLogEnable",
                    type: "bool",
                    title: "Enable info logging",
                    defaultValue: true
            )
            input(
                    name: "autoDisableDebugLog",
                    type: "number",
                    title: "Auto-disable debug logging",
                    description: "Automatically disable debug logging after this number of minutes (0 = Do not disable)",
                    defaultValue: 15
            )
            input(
                    name: "autoDisableInfoLog",
                    type: "number",
                    title: "Disable Info Logging",
                    description: "Automatically disable info logging after this number of minutes (0 = Do not disable)",
                    defaultValue: 15
            )
        }
    }
}

// [Driver API] Called when Device is first created
void installed() {
    initialize()
}

// [Driver API] Called when Device's preferences are changed
void updated() {
    infoLog("Preferences changed...")
    initialize()
}

// [Driver API] Called when Device receives a message
void parse(String description) {}

// [Capability Initialize]
void initialize() {
    unschedule()
    if (settings.debugLogEnable && settings.autoDisableDebugLog > 0)
        runIn(settings.autoDisableDebugLog * 60, disableDebugLog)
    if (settings.infoLogEnable && settings.autoDisableInfoLog > 0)
        runIn(settings.autoDisableInfoLog * 60, disableInfoLog)
}

void setLevel(level) {
    def scaling = new BigDecimal(settings.levelScaling)
    def newValue = level * scaling

    def resetValue = null
    if (settings.resetValue != null)
        resetValue = new BigDecimal(settings.resetValue)

    debugLog("New scaled value is ${newValue}")
    sendEvent(name: "level", value: level, unit: "%")
    sendEvent(name: "Value", value: newValue)
    sendEvent(name: "LastUpdated", value: new Date().getTime())
    parent.updateChildValue(device.name, newValue, resetValue)

    if (resetValue != null)
        runIn(2, "reset", [data: [value: resetValue]])
}

void reset(data) {
    debugLog("Resetting value to " + data["value"])
    sendEvent(name: "Value", value: data["value"])
}

void setLevel(level, duration) {
    setLevel(level)
}

void on() {
    debugLog("Switched on")
}

void off() {
    debugLog("Switched off")
}


// ================================================================================================
// ================================================================================================
// ================================================================================================
// ====                                                                                        ====
// ====         LIBRARY FUNCTIONS BELOW                                                        ====
// ====                                                                                        ====
// ================================================================================================
// ================================================================================================
// ================================================================================================
void debugLog(String msg) {
    if (settings.debugLogEnable)
        log.debug("${device.label ? device.label : device.name}: ${msg}")
}

void infoLog(String msg) {
    if (settings.infoLogEnable)
        log.info("${device.label ? device.label : device.name}: ${msg}")
}

void warnLog(String msg) {
    log.warn("${device.label ? device.label : device.name}: ${msg}")
}

void errorLog(String msg) {
    log.error("${device.label ? device.label : device.name}: ${msg}")
}

void disableDebugLog() {
    infoLog("Automatically disabling debug logging after ${settings.autoDisableDebugLog} minutes.")
    device.updateSetting("debugLogEnable", [value: "false", type: "bool"])
}

void disableInfoLog() {
    infoLog("Automatically disabling info logging after ${settings.autoDisableInfoLog} minutes.")
    device.updateSetting("infoLogEnable", [value: "false", type: "bool"])
}
