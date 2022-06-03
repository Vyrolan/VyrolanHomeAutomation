metadata {
    definition(
            name: "Empty Driver",
            namespace: "vyrolan",
            author: "Vyrolan",
            importUrl: "https://raw.githubusercontent.com/Vyrolan/VyrolanHubitat/main/Hubitat/Drivers/EmptyDriver.groovy"
    ) {
        capability "Initialize"
        capability "Refresh"

        attribute "foo", "string"
        attribute "bar", "number"
    }

    preferences {
        section("Settings:") {
            input(
                    name: "foo",
                    type: "string",
                    title: "Foo",
                    description: "Foo Desc",
                    required: true,
                    displayDuringSetup: true
            )
            input(
                    name: "bar",
                    type: "bool",
                    title: "Bar",
                    defaultValue: false,
                    displayDuringSetup: true
            )
            input(
                    name: "baz",
                    type: "enum",
                    title: "Baz",
                    description: "Baz Desc",
                    options: [
                            "x": "Value X",
                            "y": "Value Y"
                    ],
                    required: true,
                    defaultValue: "x",
                    displayDuringSetup: true
            )
        }
        section("Auto-Update and Logging:") {
            input(
                    name: "autoUpdateInterval",
                    type: "number",
                    title: "Auto Update Interval Minutes (0 = Disabled)",
                    description: "Number of minutes between automatic updates to pull latest status",
                    defaultValue: 10,
                    displayDuringSetup: true
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

    autoUpdate()
}

// [Capability Refresh]
void Refresh() {
    updateStates()
}

void updateStates() {
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
void autoUpdate() {
    try {
        updateStates()
    }
    catch (Exception e) {
        errorLog("Automatic status update failed...will continue to try after configured interval (${settings.autoUpdateInterval} mins). Error: ${e.message}")
    }

    if (settings.autoUpdateInterval > 0)
        runIn(settings.autoUpdateInterval * 60, autoUpdate)
}

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
