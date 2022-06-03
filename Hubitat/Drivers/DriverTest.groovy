package Hubitat.Drivers

metadata {
  definition(
          name: "Hubitat.Drivers.DriverTest",
          namespace: "vyrolan",
          author: "Vyrolan",
          importUrl: "https://raw.githubusercontent.com/Vyrolan/VyrolanHubitat/main/Hubitat.Drivers.VoiceVariables.groovy"
  ) {
    capability "Initialize"
    capability "Refresh"

    attribute "foo", "number"
    attribute "bar", "number"
    attribute "baz", "string"

    command "DeviceUpdateSetting", ["number", "number", "string"]
    command "SetState", ["number", "number", "string"]
    command "DriverUpdateDataValue", ["number", "number", "string"]
    command "DeviceUpdateDataValue", ["number", "number", "string"]
    command "UpdateAttribute", ["number", "number", "string"]
  }

  preferences {
    section("Settings:") {
      input(
              name: "foo",
              type: "number",
              title: "Foo",
              defaultValue: 27
      )
      input(
              name: "bar",
              type: "number",
              title: "Bar",
              defaultValue: 3.14
      )
      input (
              name: "baz",
              type: "string",
              title: "Baz",
              defaultValue: "baz"
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
void parse(String description) { }

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
void refresh() {
  updateStates()
}

String valueToString(Object value) {
  if (value == undefined)
    return "undefined";
  else if (value == null)
    return "null";
  else if (value instanceof Number || value instanceof Integer || value instanceof Long)
    return "Int/Long/Number " + value;
  else if (value instanceof BigDecimal || value instanceof Float || value instanceof Double)
    return "Float/Double/Decimal " + value;
  return value.toString();
}

void updateStates() {
  debugLog("Global: foo=" + valueToString(foo) + ", bar=" + valueToString(bar) + ", baz=" + valueToString(baz))
  debugLog("Settings: foo=" + valueToString(settings.foo) + ", bar=" + valueToString(settings.bar) + ", baz=" + valueToString(settings.baz))
  debugLog("State: foo=" + valueToString(state.foo) + ", bar=" + valueToString(state.bar) + ", baz=" + valueToString(state.baz))
  debugLog("getDataValue: foo=" + valueToString(getDataValue("foo")) + ", bar=" + valueToString(getDataValue("bar")) + ", baz=" + valueToString(getDataValue("baz")))
  debugLog("device.getDataValue: foo=" + valueToString(device.getDataValue("foo")) + ", bar=" + valueToString(device.getDataValue("bar")) + ", baz=" + valueToString(device.getDataValue("baz")))
  debugLog("device.currentState: foo=" + valueToString(device.currentState("foo")) + ", bar=" + valueToString(device.currentState("bar")) + ", baz=" + valueToString(device.currentState("baz")))
  debugLog("device.latestValue: foo=" + valueToString(device.latestValue("foo")) + ", bar=" + valueToString(device.latestValue("bar")) + ", baz=" + valueToString(device.latestValue("baz")))
  debugLog("device.latestState: foo=" + valueToString(device.latestState("foo")) + ", bar=" + valueToString(device.latestState("bar")) + ", baz=" + valueToString(device.latestState("baz")))
  debugLog("============================================================================================")
}

void DeviceUpdateSetting(BigDecimal foo, BigDecimal bar, String baz) {
  device.updateSetting("foo", [value: foo, type: "number"])
  device.updateSetting("bar", [value: bar, type: "number"])
  device.updateSetting("baz", [value: baz, type: "string"])
}
void SetState(BigDecimal foo, BigDecimal bar, String baz) {
  state.foo = foo
  state.bar = bar
  state.baz = baz
}
void DriverUpdateDataValue(BigDecimal foo, BigDecimal bar, String baz) {
  updateDataValue("foo", foo.toString())
  updateDataValue("bar", bar.toString())
  updateDataValue("baz", baz)
}
void DeviceUpdateDataValue(BigDecimal foo, BigDecimal bar, String baz) {
  device.updateDataValue("foo", foo.toString())
  device.updateDataValue("bar", bar.toString())
  device.updateDataValue("baz", baz)
}
void UpdateAttribute(BigDecimal foo, BigDecimal bar, String baz) {
  sendEvent(name: "foo", value: foo)
  sendEvent(name: "bar", value: bar)
  sendEvent(name: "baz", value: baz)
}

void updateChildState(String key, String status, String label = "") {
  def child = getChildDevice("${device.id}-${key}")
  if (child) {
    debugLog("Child Device '${label == "" ? key : label}' has status '${status}'")
    child.updateState(status, label)
  }
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
    log.debug("${device.label?device.label:device.name}: ${msg}")
}

void infoLog(String msg) {
  if (settings.infoLogEnable)
    log.info("${device.label?device.label:device.name}: ${msg}")
}

void warnLog(String msg) {
  log.warn("${device.label?device.label:device.name}: ${msg}")
}

void errorLog(String msg) {
  log.error("${device.label?device.label:device.name}: ${msg}")
}

void disableDebugLog() {
  infoLog("Automatically disabling debug logging after ${settings.autoDisableDebugLog} minutes.")
  device.updateSetting("debugLogEnable", [value: "false", type: "bool"])
}

void disableInfoLog() {
  infoLog("Automatically disabling info logging after ${settings.autoDisableInfoLog} minutes.")
  device.updateSetting("infoLogEnable", [value: "false", type: "bool"])
}
