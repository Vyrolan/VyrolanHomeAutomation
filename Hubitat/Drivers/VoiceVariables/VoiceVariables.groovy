metadata {
    definition(
            name: "Voice Variables",
            namespace: "vyrolan",
            author: "Vyrolan",
            importUrl: "https://raw.githubusercontent.com/Vyrolan/VyrolanHubitat/main/Hubitat/Drivers/VoiceVariables.groovy"
    ) {
        capability "Initialize"

        attribute "values", "string"
    }

    preferences {
        section("Settings:") {
            input(
                    name: "variableList",
                    type: "string",
                    title: "Variable List",
                    description: "Comma-separated list of your variable names...each will be created as a dimmer child device.",
                    required: true,
                    displayDuringSetup: true
            )
        }
        section("Logging:") {
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

    ensureChildren()
}

void ensureChildren() {
    if (settings.variableList == null)
        return

    validKeys = []

    // create any missing variable children
    added = []
    for (String variable in settings.variableList.split(",")) {
        String childId = "${device.id}-${variable.replace(" ", "_")}"
        validKeys.add(childId)
        def child = getChildDevice(childId)
        if (!child) {
            added.add(variable)
            debugLog("Creating Child Device '${variable}' with id '${childId}'")
            addChildDevice("Voice Variables Child", childId, [name: variable, isComponent: true])
        }
    }

    debugLog("Valid Keys = " + validKeys.toString())

    // delete any removed variables
    toRemove = []
    for (def child in getChildDevices()) {
        debugLog("Child: " + child + " with DNI " + child.device.deviceNetworkId)
        if (!validKeys.contains(child.device.deviceNetworkId))
            toRemove.add(child)
    }
    for (def r in toRemove) {
        debugLog("Deleting Child Device '${r.name}' with id '${r.device.deviceNetworkId}'")
        deleteChildDevice(r.device.deviceNetworkId)
    }

    if (added.size() == 0 && toRemove.size() == 0)
        return

    def currentValues = device.latestValue("values")
    if (currentValues != null)
        currentValues = new groovy.json.JsonSlurper().parseText(currentValues)
    else
        currentValues = [:]

    for (def r in toRemove)
        currentValues.remove(r.name)
    for (def a in added)
        currentValues[a] = 0

    currentValues["lastUpdate"] = new Date().getTime()
    sendEvent(name: "values", value: groovy.json.JsonOutput.toJson(currentValues))
}

void updateChildValue(String name, BigDecimal value, BigDecimal resetValue) {
    debugLog("Setting ${name} to ${value} (reset=${resetValue})")
    // groovy.json.JsonOutput.toJson(obj)
    // new groovy.json.JsonSlurper().parseText(device.latestValue("supportedFanSpeeds"))
    def currentValues = new groovy.json.JsonSlurper().parseText(device.latestValue("values"))
    currentValues[name] = value
    currentValues["lastUpdate"] = new Date().getTime()
    sendEvent(name: "values", value: groovy.json.JsonOutput.toJson(currentValues))

    if (resetValue != null) {
        currentValues[name] = resetValue
        debugLog("Scheduling reset of values to " + currentValues)
        runIn(2, "reset", [data: ["values": groovy.json.JsonOutput.toJson(currentValues)]])
    }
}

void reset(data) {
    debugLog("Resetting values to " + data["values"])
    sendEvent(name: "values", value: data["values"])
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
