/**
 *  Smart Screens
 *
 *  Copyright 2018 Martin Verbeek
 
 	App is using forecast.io to get conditions for your location, you need to get an APIKEY
  	from developer.forecast.io and your longitude and latitude is being pulled from the location object
 	position of the sun is calculated in this app, thanks to the formaula´s of Mourner at suncalc github
 
 	Select the blinds you want to configure (it will use commands Open/Stop/Close) so they need to be on the device.
   	Select the position they are facing (North-East-South-West) or multiple...these are the positions that they will need protection from wind or sun
 	WindForce protection Select condition to Close or to Open (Shutters you may want to close when lots of wind, sunscreens you may want to Open
   	cloudCover percentage is the condition to Close (Sun is shining into your room)
   	Select interval to check conditions
  
    V5.00	Blind calibration process
    V5.01	CompletionTime check
    V5.02	check if state.devices exist, if not create
    V5.03	Debug switch , call notifynewversion it will reset some stuff.
    V5.04	Nextwindspeed check
    V5.10	Dynamic intro
    V5.11	Bug Fix
    V5.12	Populate state.devices with more info
    V5.13	Fix pause switch behaviour, create separate offSeason switch in state
    V5.14	Sun in sync with Wind events
    V5.20	Multiple pause switches , assign shades to pause switches
 
*/

import groovy.json.*
import java.Math.*
import java.time.LocalDateTime.*
import Calendar.*
import groovy.time.*


private def runningVersion() 	{"5.20"}

definition(
    name: "Smart Screens",
    namespace: "verbem",
    author: "Martin Verbeek",
    description: "Automate Up and Down of Sun Screens, Blinds, Shades and Shutters based on Weather Conditions",
    category: "Convenience",
    oauth: true,
    iconUrl: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/RollerShutter.png",
    iconX2Url: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/RollerShutter.png",
    iconX3Url: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/RollerShutter.png")

mappings {
    path("/oauth/initialize") {action: [GET: "getNetatmoAuth"]}
    path("/oauth/callback") {action: [GET: "getNetatmoCallback"]}
}

preferences {
    page name:"pageSetupForecastIO"
    page name:"pageConfigureBlinds"
    page name:"pageConfigureDynamic"
    page name:"pageForecastIO"
    page name:"pageStartCal"
    page name:"pageStopCal"
    page name:"pageCompleteCal"
    page name:"pageDisplayState"
}

def pageSetupForecastIO() {
    TRACE("pageSetupForecastIO()")
	if (!state.country) getCountry() 
    if (!state.devices) state.devices = [:]
    
    //def dni = "SmartScreens Pause Switch"
    //def dev = getChildDevice(dni)
    //if (dev == null) dev = addChildDevice("verbem", "domoticzOnOff", dni, getHubID(), [name:dni, label:dni, completedSetup: true])
    //pause 5

    def pageSetupLatitude = location.latitude.toString()
    def pageSetupLongitude = location.longitude.toString()
	
    def pageSetupAPI = [
        name:       "pageSetupAPI",
        type:       "string",
        title:      "API key(darksky), key(WU) or APPID(OWM)",
        multiple:   false,
        required:   true
    	]
   
   	def pageProperties = [
        name:       "pageSetupForecastIO",
        //title:      "Status",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]
     
    def inputSunFrequency = [
        name:       "z_SunFrequency",
        type:       "number",
        default:	2,
        title:      "Sun check how many times per hour",
        multiple:   false,
		submitOnChange: false,
		required:   true
    ]
   
    def inputEnableTemp = [
        name:       "z_EnableTemp",
        type:       "bool",
        default:	false,
        title:      "",
        multiple:   false,
		submitOnChange: true,
		required:   true
    ]
    
    def inputEnableNextWindSpeed = [
        name:       "z_EnableNextWindSpeed",
        type:       "bool",
        default:	false,
        title:      "",
        multiple:   false,
		submitOnChange: false,
		required:   true
    ]

    
    def inputDayStart28 = [name: "z_inputDayStart", type: "number", title: "Start day", range: "1..28", required:true]
    def inputDayStart30 = [name: "z_inputDayStart", type: "number", title: "Start day", range: "1..30", required:true]
    def inputDayStart31 = [name: "z_inputDayStart", type: "number", title: "Start day", range: "1..31", required:true]
    def inputMonthStart = [name: "z_inputMonthStart", type: "enum", title: "Start month", options: [1:"January", 2:"February", 3:"March", 4:"April", 5:"May", 6:"June", 7:"July", 8:"August", 9:"September", 10:"October", 11:"November", 12:"December" ], required:false, submitOnChange: true]
    
    def inputDayEnd28 = [name: "z_inputDayEnd", type: "number", title: "End Day", range: "1..28", required:true]
    def inputDayEnd30 = [name: "z_inputDayEnd", type: "number", title: "End Day", range: "1..30", required:true]
    def inputDayEnd31 = [name: "z_inputDayEnd", type: "number", title: "End Day", range: "1..31", required:true]
	def inputMonthEnd = [name: "z_inputMonthEnd", type: "enum", title: "End month", options: [1:"January", 2:"February", 3:"March", 4:"April", 5:"May", 6:"June", 7:"July", 8:"August", 9:"September", 10:"October", 11:"November", 12:"December" ], required:true, submitOnChange: true]    
    
    def inputSensors = [
        name:       "z_sensors",
        type:       "device.NetatmoWind",
        title:      "Which NETATMO wind devices?",
        multiple:   true,
        required:   false
    ] 
    
    return dynamicPage(pageProperties) {
    	//input "zBlind[TEST]?.test", "string", title:"TEST", required:false
        section("Darksky.net, WeatherUndergound or OpenWeatherMap API Key and Website") {
        
			input "z_weatherAPI", "enum", options:["Darksky", "OpenWeatherMap", "WeatherUnderground", "WeatherUnderground-NoPWS"], title: "Select Weather API",multiple:false, submitOnChange: true, required:true       

            if (z_weatherAPI) {

                if (z_weatherAPI == "Darksky") {
                	paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/DarkSky.png", "DarkSky Interface"
                    input pageSetupAPI

                    href(name: "hrefNotRequired",
                         title: "Darksky.net page",
                         required: false,
                         style: "external",
                         url: "https://darksky.net/dev/",
                         description: "tap to view Darksky website in mobile browser")
                }

                if (z_weatherAPI == "WeatherUnderground" || z_weatherAPI == "WeatherUnderground-NoPWS") {
                	paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/WeatherUnderGround.png", "Weather Underground Interface"
	                input pageSetupAPI

					href(name: "hrefNotRequired",
                         title: "WeatherUnderground page",
                         required: false,
                         style: "external",
                         url: "https://www.wunderground.com/weather/api/d/pricing.html",
                         description: "tap to view WU website in mobile browser")
                }

                if (z_weatherAPI == "OpenWeatherMap") {
                	paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/OpenWeatherMap.png", "Open Weather Map Interface"
	                input pageSetupAPI

					href(name: "hrefNotRequired",
                         title: "OpenWeatherMap page",
                         required: false,
                         style: "external",
                         url: "https://home.openweathermap.org/users/sign_in",
                         description: "tap to view OWM website in mobile browser")
                }
            }            
            paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/LotsOfWind.png", 
            	"Use next hour forecasted windspeed in combination with current speed \nThe highest speed is used"
            	input inputEnableNextWindSpeed
            
	        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Netatmo.png", "Netatmo Interface"
	            input inputSensors
        }
                   
        section("Temperature Control Options", hideable:true, hidden:true) {
            
            paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Temperature.png", "Enable Temperature Protection (keep it Cool)"
			input inputEnableTemp
            if (settings.z_EnableTemp) {
            	input	"z_defaultExtTemp","number",title:"Act above Outside temperature", required:true
            	input	"z_defaultIntTemp","number",title:"Act above Inside temperature (default)", required:true
            	input	"z_defaultintTempLogic","enum",title:"Act on Inside AND/OR Outside (default)\nAND - both must be higher if Inside is present\nOR - One must be higher",
                	required:true, options:["AND", "OR"]
            	input	"z_defaultTempCloud","number",title:"Cloud cover needs to be equal or below % for action", required:true, options:["10","20","30","40","50","60","70","80","90","100"], multiple:false               
            }
        }
        section("Shades Control", hideable:true) {
                        
			paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/RollerShutter.png", "Select Window Shades"
            input "z_blinds", "capability.windowShade", multiple:true
            
			paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Settings.png", "Configure Window Shades"
            if (settings.z_blinds) z_blinds.each {href "pageConfigureBlinds", title:"${it.name}", description:"", params: it}
        }   
        
        section("Off Season Options", hideable:true, hidden:true) {           
            input inputMonthStart
            if (z_inputMonthStart) {
                if (z_inputMonthStart.toString() == "2") input inputDayStart28
                else if (z_inputMonthStart.toString().matches("1|3|5|7|8|10|12")) input inputDayStart31
                else input inputDayStart30

                input inputMonthEnd
                if (z_inputMonthEnd.toString() == "2") input inputDayEnd28
                else if (z_inputMonthEnd.toString().matches("1|3|5|7|8|10|12")) input inputDayEnd31
                else input inputDayEnd30
           	}
        }
        section("Info Page") {
            href "pageForecastIO", title:"Environment Info", description:"Tap to open"
        }
        section("Options", hideable:true, hidden:true) {
            label title:"Assign a name", required:false
            input "z_TRACE", "bool", default: false, title: "Put out trace log", multiple: false, required: true
            //input "z_CallReset", "capability.switch", title: "Call Reset When On", multiple: false, required: false
            //input "z_PauseSwitch", "capability.switch", title:"Switch that Pauses all scheduling", multiple: false , submitOnChange: true, required:false
        }
        /* if (z_PauseSwitch != null) {
            section("Assign shades to pause switch",hideable:true, hidden:true) {
                z_PauseSwitch.each {
                    input "z_Pause_${it.id}", "capability.windowShade", default:false, title:"Assign shades to $it.name}", multiple:true, required:false
                }
	        }
        } */
    }
}

/*-----------------------------------------------------------------------*/
//	 Show Configure Blinds Page
/*-----------------------------------------------------------------------*/
def pageConfigureBlinds(dev) {
	
    if (dev?.name != null) state.devName = dev.name
    
    TRACE("pageConfigureBlinds() ${state.devName}")
    
    def pageProperties = [
            name:       "pageConfigureBlinds",
            title:      "Configure for ${state.devName}",
            nextPage:   "pageSetupForecastIO",
            uninstall:  false
        ]

    return dynamicPage(pageProperties) {
        z_blinds.each {
            if (it.name == state.devName) {
                def devId = it.id
                def devType = it.typeName
                def blindOptions = ["Down", "Up"]
                def completionTime
                
                if (it.hasCommand("presetPosition")) blindOptions.add("Preset")
                if (it.hasCommand("stop")) blindOptions.add("Stop")

                if (it.completionTimeState?.value) completionTime = Date.parseToStringDate(it.completionTimeState.value).format('ss').toInteger()

                if (state.devices[devId]?.completionTime > 0) {
                		blindOptions.add("Dynamic")
                        blindOptions.add("Down 25%")
                        blindOptions.add("Down 50%")
                        blindOptions.add("Down 75%")
                }
                
                def blind = it.currentValue("somfySupported")
                if (blind == 'true') {blind = true}
                    else {blind = false}

                section(it.name) {
                    paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Settings.png", "General Settings"
                    input	"z_blindType_${devId}", "enum", options:["InHouse Screen","Screen","Shutter"], title:"In-house Screen, (sun)Screen or (roller)Shutter", required:true, multiple:false, submitOnChange:true
                    input 	"z_blindsOrientation_${devId}", "enum", options:["N", "NW", "W", "SW", "S", "SE", "E", "NE"],title:"Select Orientation",multiple:true,required:true
                    
					if (blindOptions.contains("Dynamic")) {               	
                    	href ( name: "pageConfigureDynamic", page :"pageConfigureDynamic", title:"Input for Dynamic operations", description:"Tap to define input for Dynamic operations", params:state.devices[devId])
                    }
                    
                    input 	"z_blindStopSupported_${devId}", "bool", title: "Stop command supported", required:true, defaultValue:false
                    input 	"z_blindsTest_${devId}", "bool", title: "Test operations, only events are sent", required:true, defaultValue:false
                    
                    paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Door.png", "Is there a related Door/Window"
                    input "z_blindsOpenSensor_${devId}", "capability.contactSensor", required:false, multiple:false, title:"No operation when contact is open"

                    if (settings."z_blindType_${devId}" == "InHouse Screen") {
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sun.png", "Sun Protection"
                        input	"z_closeMaxAction_${devId}","enum",title:"Action to take", options: blindOptions, required:false
                        input 	"z_cloudCover_${devId}","enum",title:"Protect until what cloudcover% (0=clear sky)", options:["10","20","30","40","50","60","70","80","90","100"],multiple:false,required:false,defaultValue:30                
                    }
                    
                    if (settings."z_blindType_${devId}" == "Screen") {
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sun.png", "Sun Protection"
                        input	"z_closeMaxAction_${devId}","enum",title:"Action to take", options: blindOptions, required:false
                        input 	"z_cloudCover_${devId}","enum",title:"Protect until what cloudcover% (0=clear sky)", options:["10","20","30","40","50","60","70","80","90","100"],multiple:false,required:false,defaultValue:30                

                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/NotSoMuchWind.png", "Sun Protection Only Below Windforce"
                        input	"z_windForceCloseMax_${devId}","number",title:"Below Windspeed ${state.unitWind}",multiple:false,required:false,defaultValue:0                 
                    } 
                    
                    if (settings."z_blindType_${devId}" == "Shutter") {
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sun.png", "Sun Protection"
                        input	"z_closeMaxAction_${devId}","enum",title:"Action to take", options: blindOptions, required:false
                        input 	"z_cloudCover_${devId}","enum",title:"Protect until what cloudcover% (0=clear sky)", options:["10","20","30","40","50","60","70","80","90","100"],multiple:false,required:false,defaultValue:30

                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/LotsOfWind.png", "Wind Protection"
                        input	"z_closeMinAction_${devId}","enum",title:"Action to take", options: blindOptions, required:false
                        input 	"z_windForceCloseMin_${devId}","number",title:"Above windspeed ${state.unitWind}",multiple:false,required:false,defaultValue:999                     
                    }
                    
                    if (settings.z_EnableTemp) {
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Airco.png", "When Airco starts cooling"                        
                        input	"z_blindsThermostatMode_${devId}", "capability.thermostatMode", required:false, submitOnChange:true, multiple:false, title:"Select Thermostat with Cool mode"
                        
                        if (settings."z_blindsThermostatMode_${devId}") {
                        	input	"z_thermoStatmodeAction_${devId}","enum",title:"Action to take when cooling", options: blindOptions, required:true
                        }
                        
                        paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Temperature.png", "Keep it Cool ($location.temperatureScale)"                        
                                              
                        input	"z_extTempAction_${devId}","enum",title:"Action to take when outside temp above ${settings.z_defaultExtTemp}", options: blindOptions, required:false                     	                       
                        input 	"z_intTempLogic_${devId}", "enum", required:false, multiple:false, title:"Above inside AND/OR outside temperature", options:["AND","OR"], defaultValue: settings.z_defaultintTempLogic, submitOnChange:true

                        if (settings."z_intTempLogic_${devId}") {
                        	input	"z_intTemp_${devId}","number",title:"Above inside temperature", required:false, submitOnChange:true
                            if (settings."z_intTemp_${devId}") {
                                input 	"z_blindsTempSensor_${devId}", "capability.temperatureMeasurement", required:true, multiple:false, title:"Select Inside Temperature Sensor"
                            }
                        }
                    }

					paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sunset.png", "End of Day operation"
                    input	"z_eodAction_${devId}","enum",title:"EOD action", options: blindOptions, required:false
                    input	"z_sunsetOffset_${devId}","number",title:"Sunset +/- offset (-360..360)", multiple:false, required:false, range: "-360..360"
                    
					paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sunrise.png", "Start of Day operation"
                    input	"z_sunriseTime_${devId}","time",title:"Start Time of operations", multiple:false, required:false
                    input	"z_sunriseOffset_${devId}","number",title:"Sunrise +/- offset (-100..360)", multiple:false, required:false, range: "-100..360" 
                    input	"z_sodAction_${devId}","enum",title:"Start of day action", options: blindOptions, required:false                     
                }
                
                section("Calibration") {
                	if (state.devices[devId].completionTime == 0)
                   		href ( name: "pageStartCal", page :"pageStartCal", title:"Calibrate", description:"Tap to define the shade closing time", params:[id:devId])
                    else
                   		href ( name: "pageStartCal", page :"pageStartCal", title:"Calibrate", description:"Closing time was defined, Tap to redefine", params:[id:devId])
                }
                section("State") {
                	href ( name: "pageDisplayState", page :"pageDisplayState", title:"Device State information", description:"Tap to show", params:state.devices[devId])
                }
            }
        }
    }
}
/*-----------------------------------------------------------------------*/
//	 Show Configure Blinds Page
/*-----------------------------------------------------------------------*/
def pageConfigureDynamic(blindParams) {
	def devId = blindParams.ID    
    def pageProperties = [
            name:       "pageConfigureDynamic",
            title:      "Input dynamic behaviour for ${state.devName}",
            nextPage:   "pageConfigureBlinds",
            uninstall:  false
        ]
        
    return dynamicPage(pageProperties) {
        section(state.devName) {
            if (settings."z_blindType_${devId}" == "Screen") {
                input	"z_blindVH_${devId}", "enum", options:["Vertical","Horizontal"], title:"Vertical or Horizontal Screen", required:true, multiple:false, defaultValue:"Vertical", submitOnChange:true
            }
            if (settings."z_blindVH_${devId}" == "Horizontal") 
            	        href(name: "hrefWithImage1", title: "Horizontal Shade",
             				description: "tap to view Image",
             				required: false,
             				image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Screens%20Horizontal.PNG",
             				url: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Screens%20Horizontal.PNG")
            else 
            	        href(name: "hrefWithImage2", title: "Vertical Shade",
             				description: "tap to view Image",
             				required: false,
             				image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Screens%20Vertical.PNG",
             				url: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Screens%20Vertical.PNG")

            def heightUnit = "cm" 
            def height = 200 
            def sunDistance = 100                      	

            if (state.units != "metric") {
                heightUnit = "inch"; 
                height = 80; 
                sunDistance = 40
            }

            input	"z_blindSunDistance_${devId}", "number", title:"(S) Shadow on floor after how many ${heightUnit}", required:true, multiple:false, defaultValue: sunDistance
            input	"z_blindWindowHeight_${devId}", "number", title:"(WL) Height of window in ${heightUnit}", required:true, multiple:false, defaultValue: height
            input	"z_blindWindowOffset_${devId}", "number", title:"(B) Distance of window from the floor in ${heightUnit}", required:true, multiple:false, defaultValue: 0, range: "0..${height}"

            if (settings."z_blindVH_${devId}" == "Horizontal") {
                input	"z_blindScreenHeight_${devId}", "number", title:"(SL) Length of screen in ${heightUnit}", required:true, multiple:false, defaultValue: height
                input	"z_blindScreenAngle_${devId}", "number", title:"(a) Horizontal angle of screen 0-60", required:true, multiple:false, defaultValue: 15, range: "0..60"
            }
        }
    }
}

/*-----------------------------------------------------------------------*/
//	 Show State device info
/*-----------------------------------------------------------------------*/
def pageDisplayState(blindParams) {
	def devId = blindParams.ID    
    def pageProperties = [
            name:       "pageDisplayState",
            title:      "State information for ${state.devName}",
            nextPage:   "pageConfigureBlinds",
            uninstall:  false
        ]
        
    return dynamicPage(pageProperties) {
        section(state.devName) {
        	//paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Settings.png", "Settings"
            if (state.devices[devId].completionTime > 0) {
        		paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Settings.png", title:"Dynamic Shade settings",
                	"" +
                	"Type of shade is ${state.devices[devId].blindsType} it is fitted ${state.devices[devId].blindsVH}, it has been calibrated with a closetime of ${state.devices[devId].completionTime} seconds. " +
                    "The window is "                
            }
            else
            	paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Settings.png", title:"General Shade Settings",
                	""
		}
        section("Sun Protection") {
            paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sun.png", "Sun Protection"
        }
        section("Wind Protection") {
            paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/LotsOfWind.png", "End of Day operation"
        }
        section("Keep it cool") {
            paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Temperature.png", "End of Day operation"
        }
        section("Start and End operations") {
            paragraph image:"https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/smart-screens.src/Sunrise.png", "End of Day operation"
        }
   	}
}
/*-----------------------------------------------------------------------*/
// Start of calibration
/*-----------------------------------------------------------------------*/

def pageStartCal(params) {
    TRACE("pageStartCal() ${state.devices[params.id].blindName}")
    devShade(params.id).open()
	state.calibrationTime = null

    def pageProperties = [
            name:       "pageStartCal",
            title:      "Start of Calibration",
            uninstall:  false
        ]    
   
    return dynamicPage(pageProperties) {
    
    	section("Hit NEXT to start calibration") {
            href ( name: "pageStartCal", page :"pageStopCal", title:"Tap to start Shade calibration", description:"Shade must be open/close fully as a start", params:[id:params.id])
        
        }
    }

}

def pageStopCal(params) {
    TRACE("pageStopCal() ${state.devices[params.id].blindName}")
    devShade(params.id).close()
    state.calibrationTime = now() 
    
    def pageProperties = [
            name:       "pageStopCal",
            title:      "Stop of Calibration",
            uninstall:  false
        ]    
   
    return dynamicPage(pageProperties) {
    	section("Hit NEXT to complete calibration") {
           href ( name :"pageStopCal", page :"pageCompleteCal", title:"Tap when Shade is fully opened/closed", description:"WAIT for the shade to open/close fully", params:[id:params.id])
        }
    }
}

def pageCompleteCal(params) {
    TRACE("pageCompleteCal() ${state.devices[params.id].blindName}")
    def t = now() - state.calibrationTime
    state.calibrationTime = Math.round(t / 1000).toInteger()
	devShade(params.id).open()
    state.devices[params.id].completionTime = state.calibrationTime
    def pageProperties = [
            name:       "pageCompleteCal",
            title:      "Calibration Complete",
            uninstall:  false
        ]    
   
    return dynamicPage(pageProperties) {
    	section("Hit NEXT to return") {
            href (name :"pageCompleteCal", page :"pageSetupForecastIO", title:"Tap to return", description:"Closing Time = ${state.calibrationTime}")
        }
    }    
}

/*-----------------------------------------------------------------------*/
// Show Sun/Wind ForecastIO API last output Page 2018-06-22 5:20:44.006 AM CEST
/*-----------------------------------------------------------------------*/

def pageForecastIO() {
    TRACE("pageForecastIO()")
    def forecast = getForecast()
    def sc = sunCalc()

    def pageProperties = [
            name:       "pageForecastIO",
            title:      "Current Sun and Wind Info",
            nextPage:   "pageSetupForecastIO",
            refreshInterval: 10,
            uninstall:  false
        ]    
   
    return dynamicPage(pageProperties) {

        section("Wind") {
        	paragraph "Next hour forecasted windspeed can be used ${settings.z_EnableNextWindSpeed}"
        	paragraph "Speed ${state.windSpeed} from direction ${state.windBearing}" 
            settings.z_sensors.each {
            	if (it.currentValue("WindAngle") && it.currentValue("WindStrength")) paragraph "${it.displayName} speed ${it.currentValue("WindStrength")} from direction ${calcBearing(it.currentValue("WindAngle"))}"
                else paragraph "Invalid data from ${it}"
            }
		}
        
        section("Sun") {
            paragraph "cloud Cover ${state.cloudCover} Sun in direction ${state.sunBearing}"
		}
        
 		if (settings.z_EnableTemp) {       
            section("Temperature") {
            	def blindParams = [:]
                paragraph "Outside Temperature reported ${state.extTemp}"
                settings.z_blinds.each { blind ->
                    if (state.devices[blind.id].extTempAction) {
                    	if (state.devices[blind.id].intTempLogic) {
                    		paragraph "${state.devices[blind.id].blindName} ${state.devices[blind.id].extTempAction} above outside ${settings.z_defaultExtTemp}, ${state.devices[blind.id].intTempLogic} inside temp ${state.devices[blind.id].devTemp} is above ${state.devices[blind.id].intTemp}"
                  		}          
                        else {
                    		paragraph "${state.devices[blind.id].blindName} ${state.devices[blind.id].extTempAction} above outside ${settings.z_defaultExtTemp}"
                    	}
                    }
                }
            }
        }
        
        section("SunCalc") {
        	paragraph "Latitude ${state.lat}"
        	paragraph "Longitude ${state.lng}"
            paragraph "Suncoord ${state.c}"
            paragraph "Azimuth ${sc.azimuth}"
            paragraph "Altitude ${sc.altitude}"
		}
    }
}
//******************************************************************************************************************************************************************************
def installed() {
	initialize()
	TRACE("Installed with settings: ${settings}")
}

def updated() {
	unsubscribe()
	initialize()
	TRACE("Updated devices: ${state.devices}")
    //resetCurrentLevel()
}

def initialize() {
    state.sunBearing = ""
    state.windBearing = ""
    if (!state.night) state.night = false
    state.windSpeed = 0
    state.cloudCover = 100
    state.netatmo = false
    state.cycles = 0
    state.offSeason = offSeason()
    
	if (!state.country) getCountry() 
        
	subscribe(location, "sunset", stopSunpath,  [filterEvents:true])
    subscribe(location, "sunrise", startSunpath,  [filterEvents:true])

    def offset
    def sunriseString = location.currentValue("sunriseTime")
    def sunriseTime
    def timeBeforeSunset
    
    offset = 120 * 60 * 1000
    sunriseTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", sunriseString)
    timeBeforeSunset = new Date(sunriseTime.time - offset)
    
    schedule(timeBeforeSunset, notifyNewVersion)
  
    subscribe(z_sensors, "WindStrength", eventNetatmo)
    subscribe(z_blinds, "windowShade", eventBlinds)
    
    settings.z_blinds.each {
    	subscribeToCommand(it, "refresh", eventRefresh)
	    def copy = fillStateDevice(it.id)
        state.devices[it.id] = copy
        fillCurrentStateDevice(it.id)
        pause 2
    }
    
    settings.each { k, v ->
    	if (k.contains("z_blindsOpenSensor")) {
        	subscribe(v, "contact.Closed", eventDoorClosed) 
        	subscribe(v, "contact.Open", eventDoorOpen) 
        }
        if (k.contains("z_blindsThermostatMode")) {
        	subscribe(v, "thermostatMode", eventThermostatMode)
        }
    }
       
    runEvery10Minutes(checkForWind)
    
    if (settings.z_PauseSwitch) subscribe(z_PauseSwitch, "switch", pauseHandler)
    if (settings.z_CallReset) subscribe(z_CallReset, "switch", resetHandler)
}

private def pauseGroup(shadeDevid) {
	def rc = false
    settings.z_pauseSwitch.each { pauseDev ->
    	if (pauseDev.currentValue("switch") == "on") {
			"z_Pause_${pauseDev.id}".each { shade ->
            	if (rc == false && shade.id == shadeDevid) rc = true
            }
        }
    }
	return rc
}

private def getCountry() {
    def httpGetCountry = [
        uri: "https://maps.googleapis.com",
        path: "/maps/api/geocode/json",
        contentType: "application/json", 
        query: ["latlng" : "${location.latitude},${location.longitude}", "key" : "AIzaSyCvNfXMaFmrTlIwIqILm7reh_9P-Sx3x2I"]
    ]

	try {
        httpGet(httpGetCountry) { response ->
        	response.data.results.address_components.each {
            	if (it.types[0].contains("country")) state.country = it.short_name[0]
            }
    	}
    }
    catch (e)  {
        log.error "googleApis $e"
    }
    
    if (state.country) {
    	if (state.country.matches("GB|US|LR|MM")) {
        	state.units = "imperial"
        	state.unitWind = "mph"
        }
        else {
        	state.units = "metric"
            state.unitWind = "km/h"
       	}
    }
}
def eventBlinds(evt) {
	if (!evt.isStateChange()) return
    
    TRACE("[eventBlinds] ${evt.device} with id ${evt.device.id} changed to value ${evt.value}") 

    if (state.devices[evt.device.id]?.lastActionTimestamp) {
        if ((now() - state.devices[evt.device.id].lastActionTimestamp) > 120000) {
        	TRACE("[eventBlinds] ${evt.device} received external action")
    		sendEvent([name: "eventBlinds", value: "${evt.device} with id ${evt.device.id} changed to value ${evt.value}, lastaction EXTERNAL"])
			state.devices[evt.device.id].lastAction = "External"
        }
    }
}

def eventDoorClosed(evt) {
	TRACE("[eventDoorClosed] ${evt.device} has closed") 
 	
    if (state.night == false) checkForSun()
}

def eventDoorOpen(evt) {
	TRACE("[eventDoorOpen] ${evt.device} has opened") 
 	
    if (state.night == true) return
    def blindID

    settings.findAll {it.key.contains("z_blindsOpenSensor_")}.each {
    	if (it.value.toString() == evt.device.toString()) {
        	blindID = it.key.split("z_blindsOpenSensor_")[1]
        	TRACE("[eventDoorOpen] Door open reverse sun action ${state.devices[blindID].blindName}")
            operateBlind([requestor: "DoorOpen", device:devShade(blindID), action: state.devices[blindID].closeMaxAction, reverse:true])
         }
	}
}

def eventThermostatMode(evt) {  
    TRACE("[eventThermostatMode] ${evt.device} ${evt.value} mode event")
    def blindID
    
    settings.findAll {it.key.contains("z_blindsThermostatMode_")}.each {
    	if (it.value.toString() == evt.device.toString()) {
        	blindID = it.key.split("z_blindsThermostatMode_")[1]
            if (evt.value == "cool") {
                if (state.devices[blindID].blindsThermostatMode && state.devices[blindID].thermoStatmodeAction) {
                    TRACE("[eventThermostatMode] ${evt.device} ${evt.value} action ${state.devices[blindID].thermoStatmodeAction} on ${state.devices[blindID].blindName}")
                    operateBlind([requestor: "Airco", device:devShade(blindID), action: state.devices[blindID].thermoStatmodeAction, reverse:false])
                    state.devices[blindID].cool = true
                }
            }
            else state.devices[blindID].cool = false
        }
    }
}

/*-----------------------------------------------------------------------------------------*/
/*	This is an event handler that will be provided with wind events from NETATMO devices
/*-----------------------------------------------------------------------------------------*/
def eventNetatmo(evt) {
	TRACE("[eventNetatmo]")
	
    def dev = evt.getDevice()
    def windAngle 		= calcBearing(dev.latestValue("WindAngle"))
    def gustStrength 	= dev.latestValue("GustStrength")
    def gustAngle 		= calcBearing(dev.latestValue("GustAngle"))
    def windStrength 	= dev.latestValue("WindStrength")
    def units 			= dev.latestValue("units")

    if (evt.isStateChange()) {
        state.windBearing = windAngle
        state.windSpeed = windStrength
        state.netatmo = true
        checkForWind("NETATMO")
    }
}

def pauseHandler(evt) {

	if (evt.value.toUpperCase() == "ON") {
    	state.pause = true
		TRACE("[pauseHandler] ${evt.device} ${evt.value} state.pause -> ${state.pause}") 
    }
    if (evt.value.toUpperCase() == "OFF") {
    	state.pause = false
		TRACE("[pauseHandler] ${evt.device} ${evt.value} state.pause -> ${state.pause}") 
    }
}

def resetHandler(evt) {

	if (evt.value.toUpperCase() == "ON") {
    	notifyNewVersion()
		TRACE("[resetHandler] ${evt.device} ${evt.value} call reset") 
    }
}

/*-----------------------------------------------------------------------------------------*/
/*	This routine will get information relating to the weather at location and current time
/*-----------------------------------------------------------------------------------------*/
def getForecast() {

	def windBearing
    def windSpeed
    def cloudCover
    def returnList = [:]
    
    TRACE("[getForecast] ${settings.z_weatherAPI} for Lon:${location.longitude} Lat:${location.latitude}")
            
	if (settings.z_weatherAPI == "Darksky") {
    	def units = "auto"
		def httpGetParams = [
            uri: "https://api.darksky.net",
            path: "/forecast/${settings.pageSetupAPI}/${location.latitude},${location.longitude}",
            contentType: "application/json", 
            query: ["units" : units, "exclude" : "minutely,daily,flags"]
        ]
        try {
            httpGet(httpGetParams) { response ->
                returnList.put('windBearing' ,calcBearing(response.data.currently.windBearing))
                returnList.put('windSpeed', Math.round(response.data.currently.windSpeed.toDouble()))
                returnList.put('cloudCover', response.data.currently.cloudCover.toDouble() * 100)
                returnList.put('temperature', response.data.currently.temperature.toDouble())
                if (settings.z_EnableNextWindSpeed) {
                    returnList.put('nextWindBearing' ,calcBearing(response.data.hourly.data[1].windBearing))
                    returnList.put('nextWindSpeed', Math.round(response.data.hourly.data[1].windSpeed.toDouble()))
                }
                }
            } 
            catch (e) {
                log.error "DARKSKY something went wrong: $e"
				returnList = [:]
            }
	}
    
	if (settings.z_weatherAPI == "OpenWeatherMap") {

        def httpGetParams = "http://api.openweathermap.org/data/2.5/weather?lat=${location.latitude}&lon=${location.longitude}&APPID=${settings.pageSetupAPI}&units=${state.units}"
        def httpGetHourly = "http://api.openweathermap.org/data/2.5/forecast?lat=${location.latitude}&lon=${location.longitude}&APPID=${settings.pageSetupAPI}&units=${state.units}"
        try {
            httpGet(httpGetParams) { resp ->
                returnList.put('windBearing',calcBearing(resp.data.wind.deg))
                returnList.put('windSpeed', Math.round(resp.data.wind.speed.toDouble()))
                returnList.put('cloudCover', Math.round(resp.data.clouds.all.toDouble()))
                returnList.put('temperature', resp.data.main.temp)
            	}
            if (settings.z_EnableNextWindSpeed) {
	            httpGet(httpGetHourly) { resp ->
                returnList.put('nextWindSpeed', Math.round(resp.data.list[0].wind.speed.toDouble()))
                returnList.put('nextWindBearing', calcBearing(Math.round(resp.data.list[0].wind.deg.toInteger())))
            	}
            }
        } 
        catch (e) {
            log.error "OWM something went wrong: $e"
            returnList = [:]
        }
    }

	if (settings.z_weatherAPI.contains("WeatherUnderground")) {
		def httpGetParams = "http://api.wunderground.com/api/${settings.pageSetupAPI}/conditions/pws:0/q/${location.latitude},${location.longitude}.json"
        def httpGetHourly = "http://api.wunderground.com/api/${settings.pageSetupAPI}/hourly/pws:0/q/${location.latitude},${location.longitude}.json"

		if (settings.z_weatherAPI.contains("NoPWS") == false) {
            httpGetParams = "http://api.wunderground.com/api/${settings.pageSetupAPI}/conditions/pws:1/q/${location.latitude},${location.longitude}.json"
            httpGetHourly = "http://api.wunderground.com/api/${settings.pageSetupAPI}/hourly/pws:0/q/${location.latitude},${location.longitude}.json"
        	TRACE("[getForecast] Use PWS is true ${httpGetParams}")
            }
        else {
        	TRACE("[getForecast] Use PWS is false ${httpGetParams}")
            }
            
		try {
        	TRACE("[getForecast] Get current conditions")

            httpGet(httpGetParams) { resp ->
                returnList.put('windBearing',calcBearing(resp.data.current_observation.wind_degrees))
                returnList.put('windSpeed', resp.data.current_observation.wind_kph.toDouble())  //all others do m/s if metric, account for this.
                def CC = 100
                switch (resp.data.current_observation.weather) {
                case ["Clear"]:
                    CC = 0
                    break;
                case "Scattered Clouds":
                    CC = 30
                    break;
                case "Partly Cloudy":
                    CC = 50
                    break;
                case ["Mostly Cloudy", "Overcast"]:
                    CC = 80
                    break;
                default:
                    CC = 100
                    break
                	}
                returnList.put('cloudCover', CC.toDouble())
                
                if (location.temperatureScale == "C") returnList.put('temperature', resp.data.current_observation.temp_c.toDouble())
                else returnList.put('temperature', resp.data.current_observation.temp_f.toDouble())
            	}
            if (settings.z_EnableNextWindSpeed) {
                TRACE("[getForecast] Get hourly conditions")
                httpGet(httpGetHourly) { resp ->
                    if (resp.data.hourly_forecast.size() > 0) if (state.units == "metric") returnList.put('nextWindSpeed', resp.data.hourly_forecast[0].wspd.metric.toDouble()) else returnList.put('nextWindSpeed', resp.data.hourly_forecast[0].wspd.english.toDouble())
                    if (resp.data.hourly_forecast.size() > 0) returnList.put('nextWindBearing', calcBearing(resp.data.hourly_forecast[0].wdir.degrees))
                }
            }
        } 
        catch (e) {
                log.error "WU something went wrong: $e"
                log.error "WU ${httpGetParams}"
                if (settings.z_EnableNextWindSpeed) log.error "WU ${httpGetHourly}"
				log.error returnList
        } 
    }
    if (returnList != null) {
        state.windBearing = returnList.windBearing
        state.windSpeed = returnList.windSpeed
        
        if (settings.z_EnableNextWindSpeed && returnList.nextWindSpeed && returnList.nextWindSpeed > returnList.windSpeed) {
            TRACE("[getForecast] next hour wind info is used!")
            state.windBearing = returnList.nextWindBearing
            state.windSpeed = returnList.nextWindSpeed
        }

        if (state.units == "metric" && settings.z_weatherAPI.contains("WeatherUnderground") == false) {state.windSpeed = returnList.windSpeed = returnList.windSpeed * 3.6}
        state.cloudCover = returnList.cloudCover
        state.extTemp = returnList.temperature
    }
    
    state.sunBearing = getSunpath()
    returnList.put('sunBearing', state.sunBearing)
	TRACE("[getForecast] ${settings.z_weatherAPI} ${returnList}")
	return returnList
}

/*-----------------------------------------------------------------------------------------*/
/*	This routine will get information relating to the SUN´s position
/*-----------------------------------------------------------------------------------------*/
def getSunpath() {
    //TRACE("[getSunpath]")
    def sp = sunCalc()
    return calcBearing(sp.azimuth)  
}

/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get latest SUN related info on position
/*	and will check the blinds that provide sun protection if they need to be closed or opened
/*	Also if temperature control is enabled it will check this first
/*-----------------------------------------------------------------------------------------*/
def checkForSun(evt) {
    TRACE("[checkForSun]")

    settings.z_blinds.each {
        fillCurrentStateDevice(it.id)

        if (!state.devices[it.id].cool && state.sunBearing.matches(state.devices[it.id].blindsOrientation)) {
			if (actionTemperature(state.devices[it.id]) == true) {
                    if (state.devices[it.id].blindsType == "Screen") {                    
                        if((state.windSpeed.toInteger() < state.devices[it.id].windForceCloseMax && state.windBearing.matches(state.devices[it.id].blindsOrientation)) || state.windBearing.matches(state.devices[it.id].blindsOrientation) == false ) {
                            operateBlind([requestor: "Temperature from Sun", device:it, action: state.devices[it.id].extTempAction, reverse:false])
                            if (!state.devices[it.id].firstTempAction) state.devices[it.id].firstTempAction = true
                        }
                    }
                    if (state.devices[it.id].blindsType == "Shutter" || state.devices[it.id].blindsType == "InHouse Screen") {
                        operateBlind([requestor: "Temperature from Sun", device:it, action: state.devices[it.id].extTempAction, reverse:false])
                        if (!state.devices[it.id].firstTempAction) state.devices[it.id].firstTempAction = true
                    }
            }
            else {
                TRACE("[checkForSun] ${it}, Forecast is ${state.cloudCover.toInteger()}% cloud, definition on shade is ${state.devices[it.id].cloudCover}%")
                if(state.cloudCover.toInteger() <= state.devices[it.id].cloudCover) 
                {                      
                    if (state.devices[it.id].blindsType == "Screen") {                    
                    	TRACE("[checkForSun] ${state.devices[it.id].blindsType} ${it}, Forecasted ${state.windSpeed.toInteger()} < ${state.devices[it.id].windForceCloseMax}, wind orientation ${state.windBearing.matches(state.devices[it.id].blindsOrientation)}")
                        if((state.windSpeed.toInteger() < state.devices[it.id].windForceCloseMax && state.windBearing.matches(state.devices[it.id].blindsOrientation)) || state.windBearing.matches(state.devices[it.id].blindsOrientation) == false ) {
                            operateBlind([requestor: "Sun", device:it, action: state.devices[it.id].closeMaxAction, reverse:false])
                            if (!state.devices[it.id].firstSunAction) state.devices[it.id].firstSunAction = true
                        }
                    }
                    if (state.devices[it.id].blindsType == "Shutter" || state.devices[it.id].blindsType == "InHouse Screen") {
                        operateBlind([requestor: "Sun", device:it, action: state.devices[it.id].closeMaxAction, reverse:false])
                        if (!state.devices[it.id].firstSunAction) state.devices[it.id].firstSunAction = true
                    }
                }
        	}
        }
        // reverse action when Sun not on Window
        if (!state.devices[it.id].cool && !state.sunBearing.matches(state.devices[it.id].blindsOrientation) && state.devices[it.id].firstSunAction == true ) {
            if (state.devices[it.id].blindsType == "Screen") {                    
                if((state.windSpeed.toInteger() < state.devices[it.id].windForceCloseMax && state.windBearing.matches(blindParams.blindsOrientation)) || state.windBearing.matches(state.devices[it.id].blindsOrientation) == false ) {
                    operateBlind([requestor: "Sun", device:it, action: state.devices[it.id].closeMaxAction, reverse:true])
                }
            }
            if (state.devices[it.id].blindsType == "Shutter" || state.devices[it.id].blindsType == "InHouse Screen") {
                operateBlind([requestor: "Sun", device:it, action: state.devices[it.id].closeMaxAction, reverse:true])
            }
        }
    }
    return null
}

/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get on cloud coverage
/*	and will check the blinds that provide sun protection if they need to be closed or opened
/*	Also if temperature control is enabled it will check this first
/*-----------------------------------------------------------------------------------------*/
def checkForClouds() {
    TRACE("[checkForClouds] ${params}")

    settings.z_blinds.each {
        fillCurrentStateDevice(it.id)
        if (!state.devices[it.id].cool && state.sunBearing.matches(state.devices[it.id].blindsOrientation) && state.devices[it.id].firstSunAction == true) {
			if (state.cloudCover.toInteger() > state.devices[it.id].cloudCover) {
                operateBlind([requestor: "Clouds", device:it, action: state.devices[it.id].closeMaxAction, reverse:true]) 
				if (!state.devices[it.id].firstCloudAction) state.devices[it.id].firstCloudAction = true
            }
    	}
    }
	return null
}

/*-----------------------------------------------------------------------------------------*/
/*	This is a scheduled event that will get latest WIND related info on position
/*	and will check the blinds if they need to be closed
/* 
/*	This routine is also used as a base for the other checks (SUN and CLOUDS) but at a lesser frequency
/*-----------------------------------------------------------------------------------------*/
def checkForWind(evt) {
    state.cycles = state.cycles + 1

	def forecast = getForecast()
    if (!forecast) {  //retry
    	pause 30
    	forecast = getForecast()
    }
    if (state.netatmo) TRACE("[checkForWind] Netatmo data is used!")
    
    settings.z_blinds.each { dev ->
    	if (dev.typeName == "domoticzBlinds") {
            sendEvent(dev, [name:"windBearing", value:state.windBearing])
            sendEvent(dev, [name:"windSpeed", value:state.windSpeed])
            sendEvent(dev, [name:"cloudCover", value:state.cloudCover])
            sendEvent(dev, [name:"sunBearing", value:state.sunBearing])
        }
    }
        
    if (state.pause) return

    TRACE("[checkForWind]")
    
	settings.z_blinds.each {
        fillCurrentStateDevice(it.id)
        /*-----------------------------------------------------------------------------------------*/
        /* Look for Start of Day times if defined and start performing actions only after that time has passed
        /* This is just a convenient place as it gets done every 10 minutes.
        /* if an offset to sunrise exists it will prevail above sunriseTime
        /*-----------------------------------------------------------------------------------------*/
        if (state.devices[it.id].sodDone == false) {
			sodActions(state.devices[it.id])
        }
        if (state.devices[it.id].eodDone == false) {
			eodActions(state.devices[it.id])
        }        
		/*-----------------------------------------------------------------------------------------*/
        /*	WIND determine if we need to close (or OPEN if wind speed is above allowed max for blind)
        /*-----------------------------------------------------------------------------------------*/ 
        if (state.windBearing) {
            if(state.windBearing.matches(state.devices[it.id].blindsOrientation)) {   
                if(state.windSpeed.toInteger() > state.devices[it.id].windForceCloseMin && (state.devices[it.id].blindsType == "Shutter" || state.devices[it.id].blindsType == "InHouse Screen")) {
                    operateBlind([requestor: "Wind", device:it, action: state.devices[it.id].closeMinAction, reverse:false])
					if (!state.devices[it.id].firstWindAction) state.devices[it.id].firstWindAction = true
                }
                if(state.windSpeed.toInteger() > state.devices[it.id].windForceCloseMax && state.devices[it.id].blindsType == "Screen") {
                    //reverse the defined MaxAction
                    operateBlind([requestor: "Wind", device:it, action: state.devices[it.id].closeMaxAction, reverse:true])
					if (!state.devices[it.id].firstWindAction) state.devices[it.id].firstWindAction = true
                }
            }
        }
        else TRACE("[checkForWind] No windBearing in State ${getForecast()}")
    }

	def sp = sunCalc()

	if (state.night == false) {
    	if (sp.altitude > 0) {
            //if (state.cycles % 2 == 0) runIn(30, checkForSun)
            runIn(30, checkForSun)
            if (state.cycles % 9 == 0) runIn(60, checkForClouds)
        }
    } 	   
    return null
}

private def sodActions(blindParams) {
    def sunriseString = location.currentValue("sunriseTime")
    def offset
    def sunriseTime
    def sunriseMinutes
    def ID = blindParams.ID
    Date thisDate = new Date()
    def thisTime = thisDate.format("HH:mm", location.timeZone)
    def thisMinutes = thisTime.split(":")[0].toInteger()*60 + thisTime.split(":")[1].toInteger()
    
    if (blindParams?.sunriseOffset != null) { //
        def hour = blindParams.sunriseOffset.intdiv(60)            
        def minutes = blindParams.sunriseOffset % 60
        def xx = getSunriseAndSunset(sunriseOffset: "${String.format('%02d',hour)}:${String.format('%02d',minutes)}")
        sunriseTime = xx.sunrise.format("HH:mm", location.timeZone)
        sunriseMinutes = sunriseTime.split(":")[0].toInteger()*60 + sunriseTime.split(":")[1].toInteger()
    }
    else if (blindParams.sunriseTime != null) {
        sunriseTime = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", blindParams.sunriseTime).format("HH:mm", location.timeZone)
        sunriseMinutes = sunriseTime.split(":")[0].toInteger()*60 + sunriseTime.split(":")[1].toInteger()
    }
    else if (state.devices[blindParams.ID].sodDone == false) state.devices[blindParams.ID].sodDone = true

    if (sunriseMinutes && thisMinutes >= sunriseMinutes) {
        TRACE("[checkForWind] Perform actions, reset sodDone for ${blindParams.blindName}, check wind for Screen types")
        if	(blindParams.blindsType == "Screen") {
            if (state.windSpeed.toInteger() <= blindParams.windForceCloseMax && blindParams.sodAction) 
                operateBlind([requestor: "SOD", device:devShade(ID), action: blindParams.sodAction, reverse:false, t:thisMinutes, s:sunriseMinutes])
            } 
        else if (blindParams.sodAction) operateBlind([requestor: "SOD", device:devShade(ID), action: blindParams.sodAction, reverse:false, t:thisMinutes, s:sunriseMinutes])
        state.devices[blindParams.ID].sodDone = true
    }
    if (state.devices[blindParams.ID].sodDone) 	TRACE("[sodActions] has been set to True")

}

private def eodActions(blindParams) {
    def sunsetString = location.currentValue("sunsetTime")
    def offset
    def sunsetTime
    def sunsetMinutes
    def ID = blindParams.ID
    Date thisDate = new Date()
    def thisTime = thisDate.format("HH:mm", location.timeZone)
    def thisMinutes = thisTime.split(":")[0].toInteger()*60 + thisTime.split(":")[1].toInteger()
    
    if (blindParams?.sunsetOffset != null && blindParams.eodAction != null) { //
        def hour = blindParams.sunsetOffset.intdiv(60)            
        def minutes = blindParams.sunsetOffset % 60
        def xx = getSunriseAndSunset(sunsetOffset: "${String.format('%02d',hour)}:${String.format('%02d',minutes)}")
        sunsetTime = xx.sunset.format("HH:mm", location.timeZone)
        sunsetMinutes = sunsetTime.split(":")[0].toInteger()*60 + sunsetTime.split(":")[1].toInteger()
    }
    else if (state.devices[blindParams.ID].eodDone == false) state.devices[blindParams.ID].eodDone = true
    
    if (sunsetMinutes && thisMinutes >= sunsetMinutes) {
        TRACE("[checkForWind] Perform actions, reset eodDone for ${blindParams.blindName}, check wind for Screen types")
        if	(blindParams.blindsType == "Screen") {
            if (state.windSpeed.toInteger() <= blindParams.windForceCloseMax && blindParams.eodAction) 
                operateBlind([requestor: "EOD", device:devShade(ID), action: blindParams.eodAction, reverse:false, t:thisMinutes, s:sunsetMinutes])
            } 
        else if (blindParams.eodAction) operateBlind([requestor: "EOD", device:devShade(ID), action: blindParams.eodAction, reverse:false, t:thisMinutes, s:sunsetMinutes])
        state.devices[blindParams.ID].eodDone = true
    }
    if (state.devices[blindParams.ID].eodDone) 	TRACE("[eodActions] has been set to True")
}

def eventRefresh(evt) {  
    TRACE("[eventRefresh] ${evt.device} Source ${evt.source}")
    
    checkForWind()
    checkForSun()
    checkForClouds()
}

/*-----------------------------------------------------------------------------------------*/
/*	this will stop the scheduling of events called at SUNSET
/*-----------------------------------------------------------------------------------------*/
def stopSunpath(evt) {
	TRACE("[stopSunpath] Stop Scheduling")
    state.night = true
    pause 5
    unsubscribe(eventRefresh)    
	return null
}

/*-----------------------------------------------------------------------------------------*/
/*	this will start the scheduling of events called at SUNRISE
/*-----------------------------------------------------------------------------------------*/
def startSunpath(evt) {
	TRACE("[startSunpath] Start Scheduling")
    state.night = false
    state.offSeason = offSeason()
    state.cycles = 0
    pause 5
    
    z_blinds.each {
    	if (it.hasCommand("refresh")) subscribeToCommand(it, "refresh", eventRefresh)
        }
           
	return null
}

private def operateBlind(blind) {
    //protect
    def id = blind.device.id

    if (blind.action == null) {
    	TRACE("[operateBlind] no action defined ${blind}")
        return false
    }
    
    if (state.offSeason && blind.requestor.matches("Wind|SOD|EOD") == false) {
    	TRACE("[operateBlind] OffSeason has been set for ${state.devices[id].blindName}, no action")
        return false
    }

    //if (state.pause && blind.requestor.matches("Wind|XX") == false) {
    if (pauseGroup(id) == true && blind.requestor.matches("Wind|XX") == false) {
    	TRACE("[operateBlind] PAUSE has been set for ${state.devices[id].blindName}, no action")
        return false
    }

	if (state.devices[id].openContact != "Closed" && blind.requestor != "DoorOpen") {
    	TRACE("[operateBlind] door/window ${state.devices[id].openContact} for ${state.devices[id].blindName}, no action")
        return false
    }
    
	if (state.devices[id].sunriseTime != null && state.devices[id].sodDone == false && blind.requestor != "SOD") {
    	TRACE("[operateBlind] StartOfDay time not passed yet for ${state.devices[id].blindName}, no action")
        return false
    }
    
	if (state.devices[id].eodDone == true) {
    	TRACE("[operateBlind] EndofDay time passed for ${state.devices[id].blindName}, no action")
        return false
    }
    
	if (!state.devices[id].test) {        
        if (blind.reverse == true) {
        	if (blind.action == "Dynamic" || (state.devices[id].reverse != blind.reverse || state.devices[id].reverse && state.devices[id].lastAction != blind.action)) {
                TRACE("[operateBlind] : ${blind}")
                if (blind.action == "Down") blind.device.open()
                if (blind.action == "Up") blind.device.close()
                if (blind.action == "Preset") blind.device.open()
                if (blind.action == "Stop") blind.device.open()
                if (blind.action == "Down 25%") blind.device.open()
                if (blind.action == "Down 50%") blind.device.open()
                if (blind.action == "Down 75%") blind.device.open()
                sendEvent([name: "operateBlind", value: blind])
                if (blind.action == "Dynamic" && state.devices[id].lastAction != blind.action) {
                	state.devices[id].currentLevel = 0
                }
                state.devices[id].lastAction = blind.action
    			state.devices[id].reverse = blind.reverse
                state.devices[id].lastActionTimestamp = now()
                pause 20
            }
       }
        else {
        	if (blind.action == "Dynamic" || (state.devices[id].reverse != blind.reverse || !state.devices[id].reverse && state.devices[id].lastAction != blind.action)) {
                TRACE("[operateBlind] : ${blind}")
                if (blind.action == "Down") blind.device.close()
                if (blind.action == "Up") blind.device.open()
                if (blind.action == "Preset") blind.device.presetPosition()
                if (blind.action == "Stop") blind.device.stop()
                if (state.devices[id].completionTime == 0) {
                    if (blind.action == "Down 25%") blind.device.setLevel(25)
                    if (blind.action == "Down 50%") blind.device.setLevel(50)
                    if (blind.action == "Down 75%") blind.device.setLevel(75)
                }
                else {
                	if (blind.action == "Dynamic") 	actionPercentage([id : id, percentage:calculateLevel(state.devices[id]), dynamic:true])
                    if (blind.action == "Down 25%") actionPercentage([id : id, percentage:25, dynamic:false])
                    if (blind.action == "Down 50%") actionPercentage([id : id, percentage:50, dynamic:false])
                    if (blind.action == "Down 75%") actionPercentage([id : id, percentage:75, dynamic:false])
                }
                sendEvent([name: "operateBlind", value: blind])
                if (blind.action == "Dynamic" && state.devices[id].lastAction != blind.action) {
                	state.devices[id].currentLevel = 0
                }
                state.devices[id].lastAction = blind.action
    			state.devices[id].reverse = blind.reverse
                state.devices[id].lastActionTimestamp = now()
                pause 20
            }
        }    	
    }
	else sendEvent([name: "TEST operateBlind", value: blind])

	return true
}

private def actionPercentage(blind) {
	eTRACE("[actionPercentage] ${blind}")
	if ((state.devices[blind.id].currentLevel == 0 && blind.dynamic) || !blind.dynamic) { // first dynamic call or postition 25/50/75
    	eTRACE("[actionPercentage] ${blind} first call")
        devShade(blind.id).open()
        runIn(state.devices[blind.id].completionTime, startMoving, [overwrite: false, data: blind])  
        sendEvent(devShade(blind.id), [name:'windowShade', value:"opening" as String])
    }
	if (state.devices[blind.id].currentLevel > 0 && blind.dynamic) {
    	eTRACE("[actionPercentage] ${blind} dynamic call")
    	startMoving(blind) // dynamic call just move the shade further down 
    }
    state.devices[blind.id].lastActionTimestamp = now()
}

def startMoving(blind) {
	eTRACE("[startMoving] ${blind} currentLevel : ${state.devices[blind.id].currentLevel}")
	def Sec = Math.round(state.devices[blind.id].completionTime*blind.percentage.toInteger()/100) 
    
    if (state.devices[blind.id].currentLevel < 100) {
        if (Sec != 0) {
            eTRACE("[startMoving] ${state.devices[blind.id].blindName} ${Sec} seconds")
            if (Sec > 0) {
                devShade(blind.id).close()
                if (Sec > 2) Sec = Sec - 1i
                runIn(Sec, stopClosing, [overwrite: false, data: blind])  
                sendEvent(devShade(blind.id), [name:'windowShade', value:"closing" as String])
            }
            if (Sec < 0) {	// this can happen with dynamic call.
                devShade(blind.id).open()
                if (Sec < -2) Sec = Sec + 1i
                runIn(Math.abs(Sec), stopOpening, [overwrite: false, data: blind])  
                sendEvent(devShade(blind.id), [name:'windowShade', value:"opening" as String])
            }
            state.devices[blind.id].lastActionTimestamp = now()
        }
        else eTRACE("[startMoving] ${state.devices[blind.id].blindName} ${Sec} seconds, no Action needed")
    }
   	else eTRACE("[startMoving] ${state.devices[blind.id].blindName} 100%, no Action needed")
}

def stopClosing(blind) {
	eTRACE("[stopClosing] ${blind}")
    state.devices[blind.id].currentLevel = (state.devices[blind.id].currentLevel + blind.percentage).toInteger()
    if (state.devices[blind.id].currentLevel < 0)  state.devices[blind.id].currentLevel = 0 
    if (state.devices[blind.id].stopSupported) devShade(blind.id).presetPosition()
    else devShade(blind.id).close()
    pause 5
    sendEvent(devShade(blind.id), [name:'windowShade', value:"partially open" as String])
    state.devices[blind.id].lastActionTimestamp = now()
}

def stopOpening(blind) {
	eTRACE("[stopOpening] ${blind}")
    state.devices[blind.id].currentLevel = (state.devices[blind.id].currentLevel + blind.percentage).toInteger()
    if (state.devices[blind.id].currentLevel < 0)  state.devices[blind.id].currentLevel = 0 
    if (state.devices[blind.id].stopSupported) devShade(blind.id).presetPosition()
    else devShade(blind.id).open()   
    pause 5
    sendEvent(devShade(blind.id), [name:'windowShade', value:"partially open" as String])
}


private def calculateLevel(blind) {
	if (!blind.blindsWindowHeight) return
    
	def sp = sunCalc()
    sp.altitude = sp.altitude.toDouble()
    double radians = Math.toRadians(sp.altitude)
	double percentage 
    def openingNeeded = Math.round(((Math.tan(radians) * 100).toInteger()))
    eTRACE("[calculateLevel] oN ${openingNeeded} bS ${blind.sunDistance} Alt ${sp.altitude}")
    
    if (blind.blindsVH == "Vertical") {
		openingNeeded = openingNeeded - blind.blindsWindowOffset
		percentage = 1 - (openingNeeded / blind.blindsWindowHeight.toInteger())
        percentage = (Math.round(percentage * 10) * 10).toInteger()		//make it round on the nearest 10ssss
        eTRACE("[calculateLevel] V-Shade length needed is ${openingNeeded}, percentage is ${percentage.toInteger()}")
        if (percentage > 90) percentage = 100i
    }
    
    if (blind.blindsVH == "Horizontal") {
        double alpha = 90 - sp.altitude						// calc alpha angle
        double gamma = 90 - blind.blindScreenAngle			// calc gamma angle 
        double beta = 180 - alpha - gamma  					// calc beta angle
        int c = (blind.blindsWindowHeight+blind.blindsWindowOffset) - openingNeeded	// calc triangle side c
        alpha = Math.sin(Math.toRadians(alpha))				// sinus for angle alpha
        beta = Math.sin(Math.toRadians(beta))				// sinus for angle beta
        double a = c * alpha / beta      					// sinus rule for calculating side a
        percentage = a / blind.blindScreenHeight.toInteger()
        percentage = (Math.round(percentage * 10) * 10).toInteger()		//make it round on the nearest 10ssss
        if (percentage > 90) percentage = 100i
        eTRACE("[calculateLevel] H-Shade length needed is ${Math.round(a)}, percentage is ${percentage.toInteger()}")
    }
    
    if (blind.currentLevel < 0 || blind.currentLevel > 100) {
    	state.devices[blind.ID].currentLevel = 0  // RESET
        blind.currentLevel = 0
    }
    
    if (blind.currentLevel == 0) return percentage
    if (blind.currentLevel == 100) return 0i
    return percentage - blind.currentLevel
        
}
//-----------------------------------------------
//	Action based on Temperature
//-----------------------------------------------
private def actionTemperature(blindParams) {
	def rc = false
    if (settings.z_EnableTemp != true || blindParams.extTempAction == null) return false

    if (state.cloudCover && state.cloudCover.toInteger() > settings.z_defaultTempCloud.toInteger()) return false
    
    if (!blindParams.intTempLogic) {
    	if (state.extTemp.toInteger() > settings?.z_defaultExtTemp.toInteger()) {
        	rc = true
        }
    }
	else {  
    	if (blindParams.intTempSensor) {
            if (state.extTemp.toInteger() > settings?.z_defaultExtTemp.toInteger()) {
                rc = true
            }
            if (blindParams.intTempLogic == "OR") {    	
                if (blindParams.devTemp && blindParams.intTemp && blindParams.devTemp > blindParams.intTemp) rc = true
            } // AND
            else if (rc == true && blindParams.devTemp && blindParams.intTemp && blindParams.devTemp > blindParams.intTemp) rc = true
                 else rc = false
        }
    }    
	return rc
}

private def devShade(id) {return settings.z_blinds.find {it.id == id}}

private def fillCurrentStateDevice(findID) {
	if (!state.devices) return
	if (!state.devices[findID]) return
 	// get current shade state
    def blindDev = devShade(findID)
    state.devices[findID].blindName 	= blindDev.displayName
    state.devices[findID].currentValue	= blindDev?.currentValue("windowShade") ?: null
    // get internal temp state
    if (settings?."z_blindsTempSensor_${findID}"?.currentValue("temperature")) state.devices[findID].devTemp = settings."z_blindsTempSensor_${findID}".currentValue("temperature").toDouble().round(0).toInteger()
    else state.devices[findID].intTempLogic = null
    // get open / closed from contactsensor
    if (settings?."z_blindsOpenSensor_${findID}") state.devices[findID].openContact = settings?."z_blindsOpenSensor_${findID}".currentValue("contact") ?: "Closed"
}

private def fillStateDevice(findID) {
	if (!state.devices) state.devices = [:]
    if (!state.devices[findID]) state.devices[findID] = [:]  
	def copyState = [:] << state.devices[findID]

    if (!copyState.cool) 						copyState.cool = false 
	if (!copyState.completionTime) 				copyState.completionTime=  0
    if (!copyState.sodDone) 					copyState.sodDone = false
    if (!copyState.eodDone) 					copyState.eodDone = false
    if (!copyState.firstSunAction) 				copyState.firstSunAction = false
    if (!copyState.firstWindAction) 			copyState.firstWindAction = false
    if (!copyState.firstTempAction) 			copyState.firstTempAction = false
    if (!copyState.firstCloudAction) 			copyState.firstCloudAction = false
    
    copyState.blindsType 			= settings?."z_blindType_${findID}"  
    copyState.stopSupported			= settings?."z_blindStopSupported_${findID}" 
    
    if (settings?."z_blindsOrientation_${findID}" == null) copyState.blindsOrientation = "NA|NA"
    else copyState.blindsOrientation = settings?."z_blindsOrientation_${findID}".join('|').replaceAll('\"','')

	copyState.blindsVH 				= settings?."z_blindVH_${findID}"  
	copyState.blindsWindowHeight	= settings?."z_blindWindowHeight_${findID}"  
	copyState.blindsWindowOffset	= settings?."z_blindWindowOffset_${findID}" 
    if (!copyState.blindsWindowOffset) copyState.blindsWindowOffset = 0    
	copyState.blindScreenHeight		= settings?."z_blindScreenHeight_${findID}"  
	copyState.blindScreenAngle		= settings?."z_blindScreenAngle_${findID}"    
	copyState.sunDistance			= settings?."z_sunDistance_${findID}"
    
    if (settings?."z_sunDistance_${findID}" && state.units != "metric") copyState.sunDistance == settings."z_sunDistance_${findID}" * 2.5 / 100   // inch to meters (approx)
    if (!copyState.sunDistance) 	copyState.sunDistance = 1
    
    if (settings?."z_windForceCloseMax_${findID}") copyState.windForceCloseMax = settings."z_windForceCloseMax_${findID}".toInteger() 
    else  {
    	if (copyState.blindsType == "Screen" )copyState.windForceCloseMax = -1 else copyState.windForceCloseMax = 999
    }
    
    if (settings?."z_windForceCloseMin_${findID}") copyState.windForceCloseMin = settings."z_windForceCloseMin_${findID}".toInteger() else copyState.windForceCloseMin = 999
    
    if (settings?."z_cloudCover_${findID}") 	copyState.cloudCover = settings."z_cloudCover_${findID}".toInteger() else copyState.cloudCover = -1
    
    copyState.closeMinAction 		= settings?."z_closeMinAction_${findID}"    
    copyState.closeMaxAction 		= settings?."z_closeMaxAction_${findID}"        
	if (settings?."z_blindsOpenSensor_${findID}") copyState.blindsOpenSensor = true     
    copyState.openContact			= "Closed"
	copyState.sunsetOffset 			= settings?."z_sunsetOffset_${findID}"
    copyState.eodAction 			= settings?."z_eodAction_${findID}"
    if (settings?."z_eodAction_${findID}" && !settings?."z_sunsetOffset_${findID}") copyState.sunsetOffset = 0    
    copyState.sunriseOffset			= settings?."z_sunriseOffset_${findID}"
   	copyState.sunriseTime			= settings?."z_sunriseTime_${findID}"
    copyState.sodAction 			= settings?."z_sodAction_${findID}"    
    copyState.test	 				= settings?."z_blindsTest_${findID}" ?: false
	copyState.intTemp 				= settings?."z_intTemp_${findID}"
    copyState.extTempAction 		= settings?."z_extTempAction_${findID}"
    if (settings?."z_blindsTempSensor_${findID}") copyState.intTempSensor = true else copyState.intTempSensor = false
    if (settings.z_EnableTemp == true) 			copyState.actOnTemp = state.devices[findID]?.actOnTemp ?: false else state.devices[findID].actOnTemp = false
    copyState.devTemp				= null
    copyState.intTempLogic 			= settings?."z_intTempLogic_${findID}"
    if (settings?."z_blindsThermostatMode_${findID}")  copyState.blindsThermostatMode = true else copyState.blindsThermostatMode = false
	if (!state.devices[findID].intTemp || state.devices[findID].intTemp == "") state.devices[findID].intTemp = settings?.z_defaultIntTemp ?: 200 else state.devices[findID].intTemp = state.devices[findID].intTemp.toInteger()
    copyState.thermoStatmodeAction 	= settings?."z_thermoStatmodeAction_${findID}"
    copyState.ID					= findID
    
	return copyState

}

/*-----------------------------------------------------------------------------------------*/
/*	this routine will return the wind or sun direction
/*-----------------------------------------------------------------------------------------*/
private def calcBearing(degree) {
		
        switch (degree.toInteger()) {
        case 0..23:
            return "N"
            break;
        case 23..68:
            return "NE"
            break;
        case 68..113:
            return "E"
            break;
        case 113..158:
            return "SE"
            break;
        case 158..203:
            return "S"
            break;
        case 203..248:
            return "SW"
            break;
        case 248..293:
            return "W"
            break;
        case 293..338:
            return "NW"
            break;
        case 338..360:
            return "N"
            break;
		default :
        	return "not found"
        	break;
        } 
 
}
private def eTRACE(message) {
	log.error message
}

private def TRACE(message) {
	if(settings.z_TRACE) {log.trace message}
}

private def offSeason() {
    def pauseReturn = false
    def date = new Date()
    def M = date[Calendar.MONTH]+1
    def D = date[Calendar.DATE]
    def Y = date[Calendar.YEAR]
    def YS = Y
    def YE = Y
    def dS
    def dE
    def justNow = "${D}-${M}-${Y}"    
    def df = "dd-MM-yyyy"
    
    if (settings.z_inputMonthStart) {
        
        if (settings.z_inputMonthEnd.toInteger() < settings.z_inputMonthStart.toInteger() && M.toInteger() >= settings.z_inputMonthStart.toInteger()) YE = Y+1
        if (settings.z_inputMonthEnd.toInteger() < settings.z_inputMonthStart.toInteger() && M.toInteger() <= settings.z_inputMonthEnd.toInteger()) YS = Y-1
        if (settings.z_inputMonthEnd.toInteger() < settings.z_inputMonthStart.toInteger() && M.toInteger() > settings.z_inputMonthEnd.toInteger() && M.toInteger() < settings.z_inputMonthStart.toInteger()) YE = Y+1
		
        dS = "${settings.z_inputDayStart}-${settings.z_inputMonthStart}-${YS}"
        dE = "${settings.z_inputDayEnd}-${settings.z_inputMonthEnd}-${YE}"
        def dateTimeS = new Date().parse(df, dS)
        def dateTimeE = new Date().parse(df, dE)
        def dateTimeN = new Date().parse(df, justNow)
        if (dateTimeN >= dateTimeS && dateTimeN <= dateTimeE) {
        	log.trace "Off SEASON, set pause switch device ON"
            def dev = getChildDevice("SmartScreens Pause Switch")
            if (dev) dev.sendEvent(name: "switch", value: "on")
            pauseReturn = true
        }
    }
    
    return pauseReturn
}

/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Azimuth and Alitude for current Time
/*-----------------------------------------------------------------------------------------*/
def sunCalc() {

	def lng = location.longitude
   	def lat = location.latitude

    state.lat = lat
    state.lng = lng
    state.julian = toJulian()
    
    def lw  = rad() * -lng
    state.lw = lw
    
    def phi = rad() * lat
    state.phi = phi
    
    def d   = toDays()
    state.d = d

    def c  = sunCoords(d)
    state.c = c
    
    def H  = siderealTime(d, lw) - c.ra
    state.H = H
     
    def az = azimuth(H, phi, c.dec)
    az = (az*180/Math.PI)+180
    def al = altitude(H, phi, c.dec)
    al = al*180/Math.PI
    
    return [
        azimuth: az,
        altitude: al
    ]
}
/*-----------------------------------------------------------------------------------------*/
/*	Return the Julian date 
/*-----------------------------------------------------------------------------------------*/
def toJulian() { 
    def date = new Date()
    date = date.getTime() / dayMs() - 0.5 + J1970() // ms time/ms in a day = days - 0.5 + number of days 1970.... 
    return date   
}
/*-----------------------------------------------------------------------------------------*/
/*	Return the number of days since J2000
/*-----------------------------------------------------------------------------------------*/
def toDays(){ return toJulian() - J2000()}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun RA
/*-----------------------------------------------------------------------------------------*/
def rightAscension(l, b) { 
	return Math.atan2(Math.sin(l) * Math.cos(e()) - Math.tan(b) * Math.sin(e()), Math.cos(l)) 
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Declination
/*-----------------------------------------------------------------------------------------*/
def declination(l, b)    { return Math.asin(Math.sin(b) * Math.cos(e()) + Math.cos(b) * Math.sin(e()) * Math.sin(l)) } 
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Azimuth
/*-----------------------------------------------------------------------------------------*/
def azimuth(H, phi, dec)  { return Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(phi) - Math.tan(dec) * Math.cos(phi)) }
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Altitude
/*-----------------------------------------------------------------------------------------*/
def altitude(H, phi, dec) { return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(H)) }
/*-----------------------------------------------------------------------------------------*/
/*	compute sidereal time (One sidereal day corresponds to the time taken for the Earth to rotate once with respect to the stars and lasts approximately 23 h 56 min.
/*-----------------------------------------------------------------------------------------*/
def siderealTime(d, lw) { return rad() * (280.16 + 360.9856235 * d) - lw }
/*-----------------------------------------------------------------------------------------*/
/*	Compute Sun Mean Anomaly
/*-----------------------------------------------------------------------------------------*/
def solarMeanAnomaly(d) { return rad() * (357.5291 + 0.98560028 * d) }
/*-----------------------------------------------------------------------------------------*/
/*	Compute Sun Ecliptic Longitude
/*-----------------------------------------------------------------------------------------*/
def eclipticLongitude(M) {

	def C = rad() * (1.9148 * Math.sin(M) + 0.02 * Math.sin(2 * M) + 0.0003 * Math.sin(3 * M))
	def P = rad() * 102.9372 // perihelion of the Earth

    return M + C + P + Math.PI 
}
/*-----------------------------------------------------------------------------------------*/
/*	Return Sun Coordinates
/*-----------------------------------------------------------------------------------------*/
def sunCoords(d) {

    def M = solarMeanAnomaly(d)
    def L = eclipticLongitude(M)

	return [dec: declination(L, 0), ra: rightAscension(L, 0)]
}
/*-----------------------------------------------------------------------------------------*/
/*	Some auxilliary routines for readabulity in the code
/*-----------------------------------------------------------------------------------------*/
def dayMs() { return 1000 * 60 * 60 * 24 }
def J1970() { return 2440588}
def J2000() { return 2451545}
def rad() { return  Math.PI / 180}
def e() { return  rad() * 23.4397}

private def getHubID(){
    TRACE("[getHubID]")
    def hubID
    def hubs = location.hubs.findAll{ it.type == physicalgraph.device.HubType.PHYSICAL } 
    if (hubs.size() == 1) hubID = hubs[0].id 
    return hubID
}

/*-----------------------------------------------------------------------------------------*/
/*	Version Control
/*-----------------------------------------------------------------------------------------*/
def getWebData(params, desc, text=true) {
	try {
		httpGet(params) { resp ->
			if(resp.data) {
				if(text) { return resp?.data?.text.toString() } 
                else { return resp?.data }
			}
		}
	}
	catch (ex) {
		if(ex instanceof groovyx.net.http.HttpResponseException) {log.error "${desc} file not found"} 
        else { log.error "[getWebData] (params: $params, desc: $desc, text: $text) Exception:", ex}
		
        return "[getWebData] ${label} info not found"
	}
}

def notifyNewVersion() {

	if (appVerInfo().split()[1] != runningVersion()) {
    	sendNotificationEvent("Hue Sensor App has a newer version, ${appVerInfo().split()[1]}, please visit IDE to update app/devices")
    }
    
    state.devices.each {
        state.devices[it.key].sodDone = false
        state.devices[it.key].eodDone = false
        state.devices[it.key].firstSunAction = false
        state.devices[it.key].firstWindAction = false
        state.devices[it.key].firstTempAction = false
        state.devices[it.key].firstCloudAction = false
        state.devices[it.key].cool = false
        if (state.devices[it.key].completionTime && state.devices[it.key].completionTime > 0) state.devices[it.key].currentLevel = 0
        pause 2
    }
}
def resetCurrentLevel() {

	state.devices.each {
        if (state.devices[it.key].completionTime && state.devices[it.key].completionTime > 0) state.devices[it.key].currentLevel = 0
        pause 2
    }
}
private def appVerInfo()		{ return getWebData([uri: "https://raw.githubusercontent.com/verbem/SmartThingsPublic/master/smartapps/verbem/SmartScreensData", contentType: "text/plain; charset=UTF-8"], "changelog") }