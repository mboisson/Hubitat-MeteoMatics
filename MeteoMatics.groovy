/**
 * Name: MeteoMatics 
 * Version: 1.0
 * Author: Maxime Boissonneault
 * 
 * Description: Polls weather information from MeteoMatics
 *
 * Driver: https://raw.githubusercontent.com/mboisson/Hubitat-MeteoMatics/main/MeteoMatics.groovy
 *
 * Readme: https://TODO
 *
 * Change Log
 *
 **/

import groovy.transform.Field
import java.text.SimpleDateFormat

@Field static final Map RefreshIntervalOpts = [
    defaultValue: 60,
    options: [ 
        1: 'Every 1 Min', 
        5: 'Every 5 Mins', 
        10: 'Every 10 Mins', 
        15: 'Every 15 Mins', 
        60: 'Every Hour', 
        120: 'Every 2 Hours', 
        240: 'Every 4 Hours', 
        360: 'Every 6 Hours', 
        720: 'Every 12 Hours', 
        1440: 'Every 24 Hours', 
    ]
]

@Field static final Map TemperatureUnitOpts = [
    defaultValue: 'C',
    options: [ 
        'C': 'Celcius',
        'F': 'Farenheit',
        'K': 'Kelvin',
    ]
]
@Field static final Map WindSpeedUnitOpts = [
    defaultValue: 'kmh',
    options: [ 
        'kmh': 'km/h',
        'kn': 'knots',
        'ms': 'm/s',
        'bft': 'Beaufort',
    ]
]
@Field static final Map PressureUnitOpts = [
    defaultValue: 'hPa',
    options: [ 
        'hPa': 'hectopascal',
        'Pa': 'pascal',
    ]
]
@Field static final Map WeatherIconMap = [
    0: "A weather symbol could not be determined",
    1: "Clear sky", 
    2: "Light clouds",
    3: "Partly cloudy",
    4: "Cloudy",
    5: "Rain",
    6: "Rain and snow / sleet",
    7: "Snow",
    8: "Rain shower",
    9: "Snow shower",
    10: "Sleet shower",
    11: "Light fog",
    12: "Dense fog",
    13: "Freezing rain",
    14: "Thunderstorms",
    15: "Drizzle",
    16: "Sandstorm",
]
@Field static final List AttributesToQuery = [
    "wind_speed_10m",
    "wind_dir_10m",
    "wind_gusts_10m_1h",
    "wind_gusts_10m_24h",
    "t_2m",
    "t_max_2m_24h",
    "t_min_2m_24h",
    "msl_pressure",
    "precip_1h",
    "precip_24h",
    "weather_symbol_1h",
    "weather_symbol_24h",
    "uv",
    "sunrise",
    "sunset"
]

preferences {
    input name: "username", type: "text",   title: "Username", required: true
    input name: "password", type: "text",   title: "Password", required: true
    input name: "lat", type: "text",   title: "Latitude", required: true, description:"-90..90"
    input name: "lon", type: "text",   title: "Longitude", required: true, description:"-180..180"
    input name: "pollTime", type: "enum", title: "Poll frequency", required: true, multiple: false, defaultValue: RefreshIntervalOpts.defaultValue, options: RefreshIntervalOpts.options
    input name: "temperatureUnit", type: "enum", title: "Temperature Unit", required: true, multiple: false, defaultValue: TemperatureUnitOpts.defaultValue, options: TemperatureUnitOpts.options
    input name: "windSpeedUnit", type: "enum", title: "Wind Speed Unit", required: true, multiple: false, defaultValue: WindSpeedUnitOpts.defaultValue, options: WindSpeedUnitOpts.options
    input name: "pressureUnit", type: "enum", title: "Pressure Unit", required: true, multiple: false, defaultValue: PressureUnitOpts.defaultValue, options: PressureUnitOpts.options
    input name: "logEnable", type: "bool",   title: "Enable debug logging", defaultValue: false, required: true
}

metadata {
      
   definition (
   name: "MeteoMatics",
   namespace: "mboisson",
   author: "Maxime Boissonneault",
   importUrl: "https://raw.githubusercontent.com/mboisson/Hubitat-MeteoMatics/main/MeteoMatics.groovy") {
        
   capability "Refresh"
   capability "Initialize"
   capability "Temperature Measurement"
   capability "Pressure Measurement"

   attribute "uv", "number"
   attribute "weather_1h", "string"
   attribute "weather_1h_idx", "number"
   attribute "weather_24h", "string"
   attribute "weather_24h_idx", "number"
   attribute "temperature", "decimal"
   attribute "temp_max_24h", "decimal"
   attribute "temp_min_24h", "decimal"
   attribute "pressure", "decimal"
   attribute "wind_speed", "decimal"
   attribute "wind_direction", "decimal"
   attribute "wind_gusts_1h", "decimal"
   attribute "wind_gusts_24h", "decimal"
   attribute "precipitations_1h", "decimal"
   attribute "precipitations_24h", "decimal"
   attribute "rain_1h", "decimal"
   attribute "rain_24h", "decimal"
   attribute "snow_1h", "decimal"
   attribute "snow_24h", "decimal"
   attribute "sunrise", "string"
   attribute "sunset", "string"
   }
}

String getAuthorizationB64() {
  return "${settings.username}:${settings.password}".bytes.encodeBase64()
}

def queryAPI() {
    loc = "${settings.lat},${settings.lon}"
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")    
    dateString = sdf.format(now())
//    log.debug "now:${dateString}"
    date = "${dateString}P0D:PT24H"
    max_items = 10
    
    parameters = ""
    AttributesToQuery.eachWithIndex { item, index ->
        parameter = "${item}:${getUnit(item)}"
        parameters = "${parameter},${parameters}"
        if (index % max_items == max_items-1) {
            // remove last comma
            parameters = parameters.replaceFirst(".\$","")
            queryAPI(date, parameters, loc)
            parameters = ""
        }
    }
    if (parameters != "") {
        // remove last comma
        parameters = parameters.replaceFirst(".\$","")
        queryAPI(date, parameters, loc)
    }
}
def queryAPI(date, parameters, loc) {
    Map params = [
        uri: "https://api.meteomatics.com/${date}/${parameters}/${loc}/json?model=mix&request_type=GET",
        headers: [authorization: "Basic ${getAuthorizationB64()}"],
        requestContentType: 'application/json',
        contentType: 'application/json'
    ]
    log.info "querying API"
    log.debug params
    asynchttpGet('processAPIResponse', params)
}

def processAPIResponse(response, data) {
    log.info "response received"
    if(response.getStatus() == 200) { // || response.getStatus() == 207) {
        response.json.data.each {
            parameter = it.parameter.split(':')[0]
            coordinates = it.coordinates
            coordinates.each {
                dates = it.dates
                lon = it.lon
                lat = it.lat
                dates.each {
                    date = it.date
                    value = it.value
//                    log.debug "${parameter} ${date} ${lon} ${lat} ${value}"
                    processParameterUpdate(parameter, value)
                }
            }
        }
    }       
}

def getWeatherText(value) {
    // indexes larger than 100 simply indicate it is night
    value = value % 100
    return WeatherIconMap[value]
}
private String getPrecipitationType() {
//    log.debug "entering getPrecipitationType()"
    weather_idx = device.currentValue("weather_1h_idx")
    type = ""
//    log.debug "weather_idx: ${weather_idx}"
    switch (weather_idx) {
        case 5:
        case 8:
        case 14:
        case 15:
        case 105:
        case 108:
        case 114:
        case 115:
            type = "rain";
            break;
        case 6:
        case 7:
        case 9:
        case 10:
        case 106:
        case 107:
        case 109:
        case 110:
            type "snow";
            break;
        default:
            type ""
    }
    if (device.currentValue("temperature") >= 0) {
        type = "rain"
    }
    else {
        type = "snow"
    }
//    log.debug "type: ${type}"
    return type
}
def processParameterUpdate(key, val) {
//    log.debug "Processing ${key}: ${val}"
    switch(key) {
        case "weather_symbol_1h":
            sendEvent(name: "weather_1h", value: getWeatherText(val))
            sendEvent(name: "weather_1h_idx", value: val)
            break;
        case "weather_symbol_24h":
            sendEvent(name: "weather_24h", value: getWeatherText(val))
            sendEvent(name: "weather_24h_idx", value: val)
            break;
        case "uv":
            sendEvent(name: "uv", value: val)
            break;
        case "t_2m":
            sendEvent(name: "temperature", value: val, unit:"${getUnit(key)}")
            break;
        case "t_max_2m_24h":
            sendEvent(name: "temp_max_24h", value: val, unit:"${getUnit(key)}")
            break;
        case "t_min_2m_24h":
            sendEvent(name: "temp_min_24h", value: val, unit:"${getUnit(key)}")
            break;
        case "msl_pressure":
            sendEvent(name: "pressure", value: val, unit:"${getUnit(key)}")
            break;
        case "wind_speed_10m":
            sendEvent(name: "wind_speed", value: val, unit:"${getUnit(key)}")
            break;
        case "wind_gusts_10m_1h":
            sendEvent(name: "wind_gusts_1h", value: val, unit:"${getUnit(key)}")
            break;
        case "wind_gusts_10m_24h":
            sendEvent(name: "wind_gusts_24h", value: val, unit:"${getUnit(key)}")
            break;
        case "precip_1h":
            sendEvent(name: "precipitations_1h", value: val, unit:"mm")
//            log.debug "precipitationType:${getPrecipitationType()}"
//            if (getPrecipitationType() == "rain") {
//                sendEvent(name: "rain_1h", value: val, unit:"mm")
//            }
//            else {
//                sendEvent(name: "snow_1h", value: val, unit:"mm")
//            }
            break;
        case "precip_24h":
            sendEvent(name: "precipitations_24h", value: val, unit:"mm")
//            log.debug "precipitationType:${getPrecipitationType()}"
//            if (getPrecipitationType() == "rain") {
//                sendEvent(name: "rain_24h", value: val, unit:"mm")
//            }
//            else {
//                sendEvent(name: "snow_24h", value: val, unit:"mm")
//            }

            break;
        case "sunrise":
            sendEvent(name: "sunrise", value: val)
            break;
        case "sunset":
            sendEvent(name: "sunset", value: val)
            break;
        case "wind_dir_10m":
            sendEvent(name: "wind_direction", value: val)
            break;
    }
}



def initialize(){
    queryAPI()
    scheduleQuery()
      
}

def scheduleQuery() {
    log.debug "settings.pollTime:${settings.pollTime}"
    unschedule(queryAPI)
    pollTime = settings.pollTime as Integer
    pollMinutes = pollTime % 60
    pollHours = pollTime / 60 as Integer
    
    log.debug "pollTime:${pollTime} pollMinutes:${pollMinutes} pollHours:${pollHours}"
    if (pollMinutes > 0) {
        log.info "Scheduling queryAPI every ${pollMinutes} minutes"
        schedule("0 */${pollMinutes} * ? * *", queryAPI)
    }
    if (pollHours > 0) {
        log.info "Scheduling queryAPI every ${pollHours} hours"
        schedule("0 0 */${pollHours} ? * *", queryAPI)
    }
}

def getUnit(attr) {
    switch (attr) {
        case "weather_symbol_1h":
        case "weather_symbol_24h":
        case "uv":
            return "idx";
        case "t_2m":
        case "t_max_2m_24h":
        case "t_min_2m_24h":
            return settings.temperatureUnit;
        case "msl_pressure":
            return settings.pressureUnit;
        case "wind_speed_10m":
        case "wind_gusts_10m_1h":
        case "wind_gusts_10m_24h":
            return settings.windSpeedUnit
        case "precip_1h":
        case "precip_24h":
            return "mm";
        case "sunrise":
        case "sunset":
            return "sql";
        case "wind_dir_10m":
            return "d";
    }
    return None
}
def updated() {
    initialize()   
}

def refresh() {
    queryAPI()
}

def poll() {
    queryAPI()
}

// Commands
command "poll"

// Event Handlers



