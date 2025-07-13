#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <SoftwareSerial.h>
#include <TinyGPS++.h>
#include <EEPROM.h>
#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>
#include <WiFiUdp.h>
#include <ESP8266WebServer.h>
#include <ESP8266HTTPUpdateServer.h>  // Add this library
#include <DNSServer.h>
#include <Esp.h>
#include <ArduinoJson.h>

// Function declarations
float getStableFuelReading();
void saveFuelFillDataToEEPROM();
void loadFuelFillDataFromEEPROM();
void resetFuelFillData();

// Add server declaration at the top
ESP8266WebServer server(80);
ESP8266HTTPUpdateServer httpUpdater;  // Add this line

// Constants
const int FUEL_READING_SAMPLES = 20;  // Increased number of samples for better smoothing
const float FUEL_CHANGE_THRESHOLD = 0.05;  // Minimum change in liters to consider valid

// Distance and speed constants
const float MIN_VALID_SPEED = 1.0;  // Minimum speed in km/h to consider valid movement (reduced noise)
const float MAX_DISTANCE_THRESHOLD = 0.1;  // Maximum distance between points (100 meters)
const float SPEED_TO_DISTANCE_FACTOR = 0.000277778;  // 1 km/h = 0.000277778 km/s

// LCD
LiquidCrystal_I2C lcd(0x27, 16, 2);

// Fuel
const int fuelPin = A0;
const float maxFuelLiters = 5.3; // Updated max fuel capacity for Activa
const float fuelMinVoltage = 0.1; // Updated minimum fuel voltage (full tank)
const float fuelMaxVoltage = 3.3; // Updated maximum fuel voltage (empty tank)
float lastFuelLiters = 0.0;
float lastFuelReadingTime = 0;
float instantFuelEconomy = 0.0;  // Add instant fuel economy variable
const float FUEL_ECONOMY_CALC_INTERVAL = 1000; // Calculate every second

// Speed from GPS
float currentSpeed = 0.0;  // Current speed from GPS in km/h
const unsigned long SPEED_UPDATE_INTERVAL = 300;  // Update speed every 300ms to match app and LCD

// GPS
TinyGPSPlus gps;
SoftwareSerial gpsSerial(D7, D8); // RX, TX

// Button
const int buttonPin = D3;  // Change this to your desired pin
const int flashButtonPin = 0;  // Flash button on NodeMCU (GPIO0)
int buttonState = HIGH;
int lastButtonState = HIGH;
int flashButtonState = HIGH;
int lastFlashButtonState = HIGH;
unsigned long lastDebounceTime = 0;
unsigned long lastFlashDebounceTime = 0;
unsigned long debounceDelay = 50;
unsigned long buttonPressStartTime = 0;
const unsigned long longPressDuration = 1000; // 1 second for long press

// Screen
int currentScreen = 0;
const int totalScreens = 5;  // Updated to 5 screens (Speed, Odometer, Trip1, Trip2, Fuel Fill)
bool screenChanged = true;  // Flag to track if screen needs to be redrawn
unsigned long lastScreenSwitch = 0;
const unsigned long SCREEN_SWITCH_INTERVAL = 8000; // Base interval for non-speed screens
const unsigned long SPEED_SCREEN_INTERVAL = 30000;  // Speed screen shows for 30 seconds
const unsigned long OTHER_SCREEN_INTERVAL = 5000;   // Other screens show for 5 seconds

// Odometer
double totalDistanceKm = 0.0;
double lastLat = 0.0, lastLng = 0.0;

// Trip 1
double trip1DistanceKm = 0.0;
bool trip1Started = false;
float trip1FuelAverage = 0.0;  // km/L
float trip1FuelUsed = 0.0;     // Liters used during trip
float lastTrip1FuelLiters = 0.0;

// Trip 2
double trip2DistanceKm = 0.0;
bool trip2Started = false;
float trip2FuelAverage = 0.0;  // km/L
float trip2FuelUsed = 0.0;     // Liters used during trip
float lastTrip2FuelLiters = 0.0;

// Timers
unsigned long lastUpdate = 0;

// Structure to store trip data
struct TripData {
  double distance;
  float fuelUsed;
  float fuelAverage;
  float instantEconomy;
  bool isStarted;
  uint8_t checksum;
} __attribute__((packed));  // Ensure no padding between fields

// EEPROM
const int EEPROM_SIZE = 512;  // Size of EEPROM in bytes
const int ODOMETER_ADDR = 0;  // Address to store odometer value
const int CHECKSUM_ADDR = sizeof(double);  // Address to store checksum
const int TRIP1_ADDR = CHECKSUM_ADDR + sizeof(uint8_t);  // Address for Trip 1 data
const int TRIP2_ADDR = TRIP1_ADDR + sizeof(TripData);  // Address for Trip 2 data
const int INSTANT_ECONOMY_ADDR = TRIP2_ADDR + sizeof(TripData);  // Address for instant economy
const int LAST_FUEL_ADDR = INSTANT_ECONOMY_ADDR + sizeof(float);  // Address for last fuel reading
const int FUEL_FILL_AVG_ADDR = LAST_FUEL_ADDR + sizeof(float);  // Address for fuel fill average
const int FUEL_FILL_DIST_ADDR = FUEL_FILL_AVG_ADDR + sizeof(float);  // Address for fuel fill distance
const int LAST_FUEL_FILL_ADDR = FUEL_FILL_DIST_ADDR + sizeof(float);  // Address for last fuel fill amount
unsigned long lastEEPROMWrite = 0;
double lastSavedKm = 0.0;
const unsigned long EEPROM_SAVE_INTERVAL = 5000;        // Save EEPROM every 5 seconds
const double DISTANCE_CHANGE_THRESHOLD = 0.001;  // Reduced threshold to save more frequently

// Add these variables with other variables
float fuelPercentage = 0.0;      // Current fuel percentage
float lastDisplayedFuelLiters = 0.0;  // Last displayed fuel value
unsigned long lastFuelReading = 0;  // Last time fuel was read
float fuelReadings[FUEL_READING_SAMPLES];  // Array to store fuel readings for moving average
int fuelReadingIndex = 0;  // Index for fuel readings array

// Fuel fill tracking variables
float lastFuelFillLiters = 0.0;
float fuelFillDistance = 0.0;
float fuelFillAverage = 0.0;
bool fuelFillStarted = false;
const float FUEL_FILL_THRESHOLD = 1;  // Threshold in liters to detect fuel fill (reduced for better detection)

// Add these constants near the top with other constants
const unsigned long FUEL_READING_INTERVAL = 500;         // Read fuel every 0.5 seconds

// WiFi credentials - replace with your network details
const char* ssid = "Activa_Dashboard";  // Name of the WiFi network created by ESP8266
const char* password = "12345678";      // Password for the WiFi network

// Add global IP address variable
IPAddress myIP;

// Add these constants near the top with other constants
const unsigned long LCD_UPDATE_INTERVAL = 500;           // Update LCD every 0.5 seconds

// Move boot messages to PROGMEM to save RAM
const char bootMessages[] PROGMEM = {
  "Ready to Ride!\0"
  "Fuel Up & Go!\0"
  "Let's Hit Road!\0"
  "Adventure Awaits!\0"
  "Time to Explore!\0"
  "Ride Smart!\0"
  "Stay Safe!\0"
  "Enjoy the Ride!\0"
  "Keep Rolling!\0"
  "Road Ready!\0"
  "Let's Cruise!\0"
  "Ride On!\0"
  "Stay Alert!\0"
  "Safe Journey!\0"
  "Happy Riding!\0"
  "Go Explore!\0"
  "Ride Safe!\0"
  "Time to Roll!\0"
  "Let's Go!\0"
  "Drive Smart!\0"
};

const int NUM_BOOT_MESSAGES = 20;

// Function to get boot message from PROGMEM
String getBootMessage(int index) {
  char buffer[16];
  int offset = 0;
  for (int i = 0; i < index; i++) {
    while (pgm_read_byte(&bootMessages[offset]) != '\0') offset++;
    offset++;
  }
  int i = 0;
  while (i < 15 && (buffer[i] = pgm_read_byte(&bootMessages[offset + i])) != '\0') i++;
  buffer[i] = '\0';
  return String(buffer);
}

// Function to calculate checksum for a block of data
uint8_t calculateChecksum(void* data, size_t size) {
  uint8_t checksum = 0;
  uint8_t* bytes = (uint8_t*)data;
  for (size_t i = 0; i < size; i++) {
    checksum ^= bytes[i];
  }
  return checksum;
}

// Function to save trip data to EEPROM
void saveTripToEEPROM(int tripAddr, double distance, float fuelUsed, float fuelAverage, float instantEconomy, bool isStarted) {
  TripData tripData = {distance, fuelUsed, fuelAverage, instantEconomy, isStarted, 0};
  tripData.checksum = calculateChecksum(&tripData, sizeof(TripData) - sizeof(uint8_t));
  EEPROM.put(tripAddr, tripData);
  EEPROM.commit();
}

// Function to load trip data from EEPROM
bool loadTripFromEEPROM(int tripAddr, double& distance, float& fuelUsed, float& fuelAverage, float& instantEconomy, bool& isStarted) {
  TripData tripData;
  memset(&tripData, 0, sizeof(TripData));
  EEPROM.get(tripAddr, tripData);
  
  uint8_t calculatedChecksum = calculateChecksum(&tripData, sizeof(TripData) - sizeof(uint8_t));
  
  if (isnan(tripData.distance) || isnan(tripData.fuelUsed) || isnan(tripData.fuelAverage) || 
      isnan(tripData.instantEconomy)) {
    return false;
  }
  
  if (tripData.checksum == calculatedChecksum && 
      tripData.distance >= 0.0 && 
      tripData.fuelUsed >= 0.0 && 
      tripData.fuelAverage >= 0.0) {
    distance = tripData.distance;
    fuelUsed = tripData.fuelUsed;
    fuelAverage = tripData.fuelAverage;
    instantEconomy = tripData.instantEconomy;
    isStarted = tripData.isStarted;
    return true;
  }
  
  return false;
}

// Function to calculate checksum for odometer value
uint8_t calculateChecksum(double value) {
  uint8_t checksum = 0;
  uint8_t* bytes = (uint8_t*)&value;
  for (size_t i = 0; i < sizeof(double); i++) {
    checksum ^= bytes[i];
  }
  return checksum;
}

// Function to save odometer to EEPROM
void saveOdometerToEEPROM() {
  uint8_t checksum = calculateChecksum(totalDistanceKm);
  EEPROM.put(ODOMETER_ADDR, totalDistanceKm);
  EEPROM.write(CHECKSUM_ADDR, checksum);
  EEPROM.commit();
  lastSavedKm = totalDistanceKm;
  lastEEPROMWrite = millis();
}

// Function to load odometer from EEPROM
bool loadOdometerFromEEPROM() {
  double loadedKm;
  EEPROM.get(ODOMETER_ADDR, loadedKm);
  uint8_t storedChecksum = EEPROM.read(CHECKSUM_ADDR);
  uint8_t calculatedChecksum = calculateChecksum(loadedKm);
  
  if (storedChecksum == calculatedChecksum && 
      !isnan(loadedKm) && 
      loadedKm >= 0.0 && 
      loadedKm < 999999.99) {
    totalDistanceKm = loadedKm;
    lastSavedKm = loadedKm;
    return true;
  }
  
  return false;
}

// EEPROM address for resetCount
const int RESET_COUNT_ADDR = LAST_FUEL_FILL_ADDR + sizeof(float) + sizeof(bool); // Pick a safe unused address
int resetCount = 0;

// Add these variables near other reset/trip related variables
const int TRIP1_RESET_COUNT_ADDR = RESET_COUNT_ADDR + sizeof(int);  // Address for Trip 1 reset count
const int TRIP2_RESET_COUNT_ADDR = TRIP1_RESET_COUNT_ADDR + sizeof(int);  // Address for Trip 2 reset count
int trip1ResetCount = 0;
int trip2ResetCount = 0;

// Function to save all data to EEPROM
void saveAllDataToEEPROM() {
    // Save odometer data
    saveOdometerToEEPROM();
    
    // Save trip data
    saveTripToEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started);
    saveTripToEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started);
    
    // Save fuel fill data
    saveFuelFillDataToEEPROM();
    
    // Save instant economy and last fuel reading
    EEPROM.put(INSTANT_ECONOMY_ADDR, instantFuelEconomy);
    EEPROM.put(LAST_FUEL_ADDR, lastFuelLiters);
    // Save resetCount
    EEPROM.put(RESET_COUNT_ADDR, resetCount);
    
    // Commit changes
    EEPROM.commit();
    lastEEPROMWrite = millis();
    lastSavedKm = totalDistanceKm;
}

// Function to load all data from EEPROM
void loadAllDataFromEEPROM() {
  if (!loadOdometerFromEEPROM()) {
    totalDistanceKm = 0.0;
    saveOdometerToEEPROM();
  }
  
  if (!loadTripFromEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started)) {
    trip1DistanceKm = 0.0;
    trip1FuelUsed = 0.0;
    trip1FuelAverage = 0.0;
    trip1Started = false;
    saveTripToEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started);
  }
  
  if (!loadTripFromEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started)) {
    trip2DistanceKm = 0.0;
    trip2FuelUsed = 0.0;
    trip2FuelAverage = 0.0;
    trip2Started = false;
    saveTripToEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started);
  }
  
  EEPROM.get(INSTANT_ECONOMY_ADDR, instantFuelEconomy);
  if (isnan(instantFuelEconomy)) {
    instantFuelEconomy = 0.0;
  }
  
  EEPROM.get(LAST_FUEL_ADDR, lastFuelLiters);
  if (isnan(lastFuelLiters)) {
    lastFuelLiters = 0.0;
  }

  // Load fuel fill data
  loadFuelFillDataFromEEPROM();
  
  // Load resetCount
  EEPROM.get(RESET_COUNT_ADDR, resetCount);
  if (resetCount < 0) resetCount = 0;
}

// Update resetTrip function to match odometer reset style
void resetTrip(int tripNum) {
  if (tripNum == 1) {
    trip1DistanceKm = 0.0;
    trip1FuelUsed = 0.0;
    trip1FuelAverage = 0.0;
    trip1Started = false;
    saveTripToEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started);
    // Increment resetCount and save
    resetCount++;
    EEPROM.put(RESET_COUNT_ADDR, resetCount);
    EEPROM.commit();
  } else if (tripNum == 2) {
    trip2DistanceKm = 0.0;
    trip2FuelUsed = 0.0;
    trip2FuelAverage = 0.0;
    trip2Started = false;
    saveTripToEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started);
    // Increment resetCount and save
    resetCount++;
    EEPROM.put(RESET_COUNT_ADDR, resetCount);
    EEPROM.commit();
  }
}

void switchScreen() {
  currentScreen = (currentScreen + 1) % totalScreens;
  screenChanged = true;
  lastScreenSwitch = millis();
  
  // If switching to speed screen, ensure it stays longer
  if (currentScreen == 0) {
    lastScreenSwitch = millis();  // Reset timer for speed screen
  }
}

// Add this function to handle fuel fill detection
void checkFuelFill() {
    float currentFuel = getStableFuelReading();
    float fuelChange = currentFuel - lastFuelLiters;
    
    // If fuel level increases significantly, consider it a fill
    if (fuelChange > FUEL_FILL_THRESHOLD) {
        lastFuelFillLiters = currentFuel;
        fuelFillDistance = 0.0;
        fuelFillAverage = 0.0;
        fuelFillStarted = true;
        saveFuelFillDataToEEPROM();
        
    }
    
    // Calculate fuel used since last fill and update average
    if (fuelFillStarted) {
        float fuelUsed = lastFuelFillLiters - currentFuel;
        
        // Update average if we've used some fuel and have distance
        if (fuelUsed > 0 && fuelFillDistance > 0) {
            fuelFillAverage = fuelFillDistance / fuelUsed;
        }
        
        // Save periodically when data changes
        static unsigned long lastFuelFillSave = 0;
        if (millis() - lastFuelFillSave >= 5000) {  // Save every 5 seconds
            saveFuelFillDataToEEPROM();
            lastFuelFillSave = millis();
        }
    }
    
    // Update last fuel reading
    lastFuelLiters = currentFuel;
}

// Modify the updateDisplay function to include the new screen
void updateDisplay(float speedKmph, float fuelPercentage, float fuelLiters, 
                  double totalDistanceKm, double trip1DistanceKm, float trip1FuelAverage,
                  double trip2DistanceKm, float trip2FuelAverage) {
  static unsigned long lastLcdUpdate = 0;
  unsigned long currentMillis = millis();
  
  // Only update LCD if enough time has passed
  if (currentMillis - lastLcdUpdate >= LCD_UPDATE_INTERVAL) {
    lastLcdUpdate = currentMillis;
    
    if (screenChanged) {
      lcd.clear();
      screenChanged = false;
    }
    
    lcd.setCursor(0, 0);

    if (currentScreen == 0) {
      // Screen 0: Speed + Fuel - Update more frequently
      lcd.print("SPEED:");
      lcd.setCursor(7, 0);
      char speedStr[8];
      dtostrf(speedKmph, 6, 1, speedStr);
      lcd.print(speedStr);
      
      lcd.setCursor(0, 1);
      lcd.print("F:");
      lcd.print((int)fuelPercentage);
      lcd.print("% ");
      lcd.print(fuelLiters, 1);
      lcd.print("L");
      
    } else if (currentScreen == 1) {
      // Screen 1: Odometer + GPS Time
      lcd.print("ODO:");
      lcd.setCursor(5, 0);
      lcd.print(totalDistanceKm, 2);
      lcd.print(" km");
      
      lcd.setCursor(0, 1);
      // Display GPS time if available
      if (gps.time.isValid() && gps.date.isValid()) {
        // Convert UTC to IST (UTC + 5:30)
        int utcHour = gps.time.hour();
        int utcMinute = gps.time.minute();
        
        // Add 5 hours 30 minutes for IST
        int istMinute = utcMinute + 30;
        int istHour = utcHour + 5;
        
        // Handle minute overflow
        if (istMinute >= 60) {
          istMinute -= 60;
          istHour++;
        }
        
        // Handle hour overflow
        if (istHour >= 24) {
          istHour -= 24;
        }
        
        // Convert to 12-hour format
        bool isPM = istHour >= 12;
        int displayHour = istHour;
        if (displayHour == 0) displayHour = 12;  // Midnight = 12 AM
        else if (displayHour > 12) displayHour -= 12;  // PM hours
        
        // Format time as "TIME: HH:MM AM/PM"
        lcd.print("TIME:");
        lcd.setCursor(5, 1);
        char timeStr[8];
        sprintf(timeStr, "%02d:%02d%s", 
                displayHour, istMinute, isPM ? "PM" : "AM");
        lcd.print(timeStr);
      } else {
        lcd.print("TIME: GPS WAIT");
      }
      
    } else if (currentScreen == 2) {
      // Screen 2: Trip 1
      lcd.print("TRIP1:");
      lcd.setCursor(7, 0);
      lcd.print(trip1DistanceKm, 2);
      lcd.print(" km");
      
      lcd.setCursor(0, 1);
      lcd.print("AVG:");
      lcd.setCursor(5, 1);
      lcd.print(trip1FuelAverage, 1);
      lcd.print(" km/L");
      
    } else if (currentScreen == 3) {
      // Screen 3: Trip 2
      lcd.print("TRIP2:");
      lcd.setCursor(7, 0);
      lcd.print(trip2DistanceKm, 2);
      lcd.print(" km");
      
      lcd.setCursor(0, 1);
      lcd.print("AVG:");
      lcd.setCursor(5, 1);
      lcd.print(trip2FuelAverage, 1);
      lcd.print(" km/L");
      
    } else if (currentScreen == 4) {
      // Screen 4: Fuel Fill Data
      lcd.print("FILL:");
      lcd.setCursor(6, 0);
      lcd.print(fuelFillDistance, 1);
      lcd.print(" km");
      
      lcd.setCursor(0, 1);
      lcd.print("AVG:");
      lcd.setCursor(5, 1);
      lcd.print(fuelFillAverage, 1);
      lcd.print(" km/L");
    }
  }
}

// Add new GPS data variables
unsigned long lastGpsUpdate = 0;
const unsigned long GPS_UPDATE_INTERVAL = 1000;  // Update GPS data every second

// Removed handleGpsData function - app no longer sends GPS data

// Update calculateSpeedAndDistance function with improved distance calculation
void calculateSpeedAndDistance() {
  // Check if GPS data is stale (no updates for more than 2 seconds)
  if (millis() - lastGpsUpdate > 2000) {
    currentSpeed = 0.0;  // Set speed to 0 if no recent GPS updates
  }

  // Update distances based on current speed
  if (currentSpeed >= MIN_VALID_SPEED) {
    // Calculate distance based on speed and time
    float distanceIncrement = currentSpeed * SPEED_TO_DISTANCE_FACTOR * (SPEED_UPDATE_INTERVAL / 1000.0);
    
    // Only add distance if it's reasonable
    if (distanceIncrement <= MAX_DISTANCE_THRESHOLD) {
    // Update distances
    totalDistanceKm += distanceIncrement;
      
      // Update Trip 1 if started
    if (trip1Started) {
      trip1DistanceKm += distanceIncrement;
      if (trip1FuelUsed > 0) {
        trip1FuelAverage = trip1DistanceKm / trip1FuelUsed;
      }
    }
      
      // Update Trip 2 if started
    if (trip2Started) {
      trip2DistanceKm += distanceIncrement;
      if (trip2FuelUsed > 0) {
        trip2FuelAverage = trip2DistanceKm / trip2FuelUsed;
        }
      }
      
      // Update fuel fill distance if tracking
      if (fuelFillStarted) {
        fuelFillDistance += distanceIncrement;
      }
    }
  }
}

// Add these before setup()
const byte DNS_PORT = 53;
IPAddress apIP(192, 168, 4, 1);
DNSServer dnsServer;

// Add this function to handle captive portal
void handleCaptivePortal() {
  server.sendHeader("Location", "http://192.168.4.1/gps-sender", true);
  server.send(302, "text/plain", "");
}

// Add common navigation HTML
const char NAV_HTML[] PROGMEM = R"rawliteral(
<div class="nav-menu">
    <a href="#" onclick="showPage('gps')" id="nav-gps">GPS Sender</a>
    <a href="#" onclick="showPage('dashboard')" id="nav-dashboard">Dashboard</a>
    <a href="#" onclick="showPage('update')" id="nav-update">Firmware Update</a>
</div>
)rawliteral";

// Update GPS sender HTML
const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
    <title>Activa Dashboard - Firmware Update</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="Content-Security-Policy" content="upgrade-insecure-requests">
    <style>
        body { 
            font-family: Arial; 
            margin: 20px;
            background-color: #f5f5f5;
        }
        .container { 
            max-width: 600px; 
            margin: 0 auto;
            background-color: white;
            padding: 20px;
            border-radius: 10px;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        }
        .status { 
            margin: 20px 0; 
            padding: 10px; 
            border-radius: 5px; 
        }
        .success { 
            background-color: #dff0d8; 
            color: #3c763d; 
        }
        .error { 
            background-color: #f2dede; 
            color: #a94442; 
        }
        .data-display {
            margin: 20px 0;
            padding: 15px;
            background: #f8f9fa;
            border-radius: 5px;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        }
        .data-row {
            margin: 10px 0;
            display: flex;
            justify-content: space-between;
            align-items: center;
            min-height: 30px;
        }
        .label { 
            font-weight: bold; 
            color: #666;
            flex: 1;
        }
        .value { 
            color: #333; 
            font-size: 1.1em;
            min-width: 80px;
            text-align: right;
            font-family: monospace;
            padding-left: 10px;
        }
        .unit {
            color: #666;
            margin-left: 5px;
            min-width: 40px;
            text-align: left;
        }
        button {
            padding: 12px 24px;
            margin: 5px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            font-size: 16px;
            font-weight: bold;
            text-transform: uppercase;
            transition: all 0.3s ease;
            box-shadow: 0 2px 5px rgba(0,0,0,0.2);
            min-width: 150px;
        }
        button:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(0,0,0,0.3);
        }
        button.update-btn {
            background-color: #28a745;
            color: white;
            width: 100%;
            margin-top: 20px;
        }
        button.update-btn:hover {
            background-color: #218838;
        }
        .file-drop-zone {
            border: 2px dashed #ccc;
            border-radius: 5px;
            padding: 20px;
            text-align: center;
            margin: 20px 0;
            transition: all 0.3s ease;
        }
        .file-drop-zone:hover {
            border-color: #28a745;
        }
        .file-input-label {
            cursor: pointer;
            color: #666;
        }
        .hidden {
            display: none;
        }
        .progress-bar {
            width: 100%;
            height: 20px;
            background-color: #f0f0f0;
            border-radius: 10px;
            overflow: hidden;
            margin: 10px 0;
        }
        .progress-bar-fill {
            height: 100%;
            background-color: #28a745;
            width: 0%;
            transition: width 0.3s ease;
        }
        input[type="text"],
        input[type="password"] {
            width: 100%;
            padding: 10px;
            margin: 5px 0;
            border: 1px solid #ddd;
            border-radius: 5px;
            box-sizing: border-box;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>Firmware Update</h1>
        <div id="update-status" class="status"></div>
        
        <div class="data-display">
            <div class="data-row">
                <span class="label">Current Version:</span>
                <span id="current-version" class="value">1.0.0</span>
            </div>
        </div>

        <div class="data-display">
            <div class="data-row">
                <span class="label">Update Credentials</span>
            </div>
            <input type="text" id="username" placeholder="Username" value="admin" class="input-field">
            <input type="password" id="password" placeholder="Password" value="admin" class="input-field">
        </div>

        <form id="uploadForm" enctype="multipart/form-data">
            <div class="data-display file-drop-zone" id="dropZone">
                <div class="file-input-label" id="fileInputLabel">
                    <span>Click to select firmware file or drag and drop here</span>
                </div>
                <input type="file" id="fileInput" name="update" accept=".bin" class="hidden">
                <div id="fileName" class="value"></div>
            </div>

            <div class="data-display">
                <div class="data-row">
                    <span class="label">Update Progress</span>
                </div>
                <div class="progress-bar">
                    <div id="progressBar" class="progress-bar-fill"></div>
                </div>
                <div id="progressText" class="value">0%</div>
            </div>

            <button type="submit" class="update-btn">Update Firmware</button>
        </form>
    </div>

    <script>
        const form = document.getElementById('uploadForm');
        const fileInput = document.getElementById('fileInput');
        const fileInputLabel = document.getElementById('fileInputLabel');
        const fileName = document.getElementById('fileName');
        const dropZone = document.getElementById('dropZone');

        fileInputLabel.addEventListener('click', () => {
            fileInput.click();
        });

        fileInput.addEventListener('change', (e) => {
            if (e.target.files.length) {
                fileName.textContent = e.target.files[0].name;
            }
        });

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.style.borderColor = '#4CAF50';
        });

        dropZone.addEventListener('dragleave', (e) => {
            e.preventDefault();
            dropZone.style.borderColor = '#ccc';
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.style.borderColor = '#ccc';
            const files = e.dataTransfer.files;
            if (files.length) {
                fileInput.files = files;
                fileName.textContent = files[0].name;
            }
        });

        form.onsubmit = async (e) => {
            e.preventDefault();
            
            const formData = new FormData(form);
            const file = formData.get('update');
            const username = document.getElementById('username').value;
            const password = document.getElementById('password').value;
            
            if (!file || !file.name) {
                document.getElementById('update-status').innerHTML = 'Please select a firmware file';
                document.getElementById('update-status').className = 'status error';
                return;
            }

            try {
                document.getElementById('update-status').innerHTML = 'Uploading firmware...';
                document.getElementById('update-status').className = 'status';
                document.getElementById('progressBar').style.width = '0%';
                document.getElementById('progressText').textContent = '0%';
                
                const response = await fetch('/update', {
                    method: 'POST',
                    headers: {
                        'Authorization': 'Basic ' + btoa(username + ':' + password)
                    },
                    body: formData
                });

                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }

                const result = await response.text();
                document.getElementById('update-status').innerHTML = 'Update successful! Device will restart...';
                document.getElementById('update-status').className = 'status success';
                document.getElementById('progressBar').style.width = '100%';
                document.getElementById('progressText').textContent = '100%';
                
                setTimeout(() => {
                    window.location.href = '/';
                }, 5000);
                
            } catch (error) {
                document.getElementById('update-status').innerHTML = 'Update failed: ' + error.message;
                document.getElementById('update-status').className = 'status error';
                document.getElementById('progressBar').style.width = '0%';
                document.getElementById('progressText').textContent = '0%';
            }
        };
    </script>
</body>
</html>
)rawliteral";

// Add dashboard data endpoint handler
void handleDashboardData() {
    String json = "{";
    json += "\"speed\":" + String(currentSpeed) + ",";
    json += "\"fuelPercentage\":" + String((lastDisplayedFuelLiters / maxFuelLiters) * 100.0) + ",";
    json += "\"fuelLiters\":" + String(lastDisplayedFuelLiters) + ",";
    json += "\"instantEconomy\":" + String(instantFuelEconomy) + ",";
    json += "\"totalDistance\":" + String(totalDistanceKm) + ",";
    json += "\"trip1Distance\":" + String(trip1DistanceKm) + ",";
    json += "\"trip1Fuel\":" + String(trip1FuelUsed) + ",";
    json += "\"trip1Average\":" + String(trip1FuelAverage) + ",";
    json += "\"trip1Started\":" + String(trip1Started ? "true" : "false") + ",";
    json += "\"trip2Distance\":" + String(trip2DistanceKm) + ",";
    json += "\"trip2Fuel\":" + String(trip2FuelUsed) + ",";
    json += "\"trip2Average\":" + String(trip2FuelAverage) + ",";
    json += "\"trip2Started\":" + String(trip2Started ? "true" : "false") + ",";
    json += "\"fuelFillAverage\":" + String(fuelFillAverage) + ",";
    json += "\"fuelFillDistance\":" + String(fuelFillDistance) + ",";
    json += "\"lastFuelFill\":" + String(lastFuelFillLiters) + ",";
    json += "\"fuelFillStarted\":" + String(fuelFillStarted ? "true" : "false") + ",";
    json += "\"fuelUsedSinceFill\":" + String(lastFuelFillLiters - lastDisplayedFuelLiters) + ",";
    json += "\"resetCount\":" + String(resetCount);
    json += "}";
    server.send(200, "application/json", json);
}

// Add trip reset endpoint handlers
void handleResetTrip() {
    String tripNum = server.pathArg(0);
    if (tripNum == "1" || tripNum == "2") {
        resetTrip(tripNum.toInt());
        server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Trip " + tripNum + " reset successfully\"}");
    } else {
        server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Invalid trip number\"}");
    }
}

void handleResetAllTrips() {
  
    resetTrip(1);
    resetTrip(2);
  
    
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"All trips reset successfully\"}");
}

// Add this function to check update status
void checkUpdateStatus() {
    if (Update.hasError()) {
        // Silent error handling
    }
}

// Add getStableFuelReading function
float getStableFuelReading() {
    // Read raw value
    int rawFuel = analogRead(fuelPin);
    
    // Convert to voltage
    float voltage = rawFuel * (3.3 / 1023.0);
    voltage = constrain(voltage, fuelMinVoltage, fuelMaxVoltage);
    
    // Calculate percentage
    float fuelPercentage = 100.0 - ((voltage - fuelMinVoltage) / (fuelMaxVoltage - fuelMinVoltage)) * 100.0;
    fuelPercentage = constrain(fuelPercentage, 0, 100);
    
    // Convert to liters
    float fuelLiters = (fuelPercentage / 100.0) * maxFuelLiters;
    
    // Update moving average
    fuelReadings[fuelReadingIndex] = fuelLiters;
    fuelReadingIndex = (fuelReadingIndex + 1) % FUEL_READING_SAMPLES;
    
    // Calculate average
    float sum = 0;
    for (int i = 0; i < FUEL_READING_SAMPLES; i++) {
        sum += fuelReadings[i];
    }
    float averageFuel = sum / FUEL_READING_SAMPLES;
    
    return averageFuel;
}

// Add OTA update handler
void handleUpdate() {
    server.send(200, "text/html", INDEX_HTML);
}

void handleUpdatePost() {
    HTTPUpload& upload = server.upload();
    
    if (upload.status == UPLOAD_FILE_START) {
        if (!Update.begin(upload.totalSize)) {
            server.send(500, "text/plain", "Update begin failed");
            return;
        }
    } else if (upload.status == UPLOAD_FILE_WRITE) {
        if (Update.write(upload.buf, upload.currentSize) != upload.currentSize) {
            server.send(500, "text/plain", "Update write failed");
            return;
        }
    } else if (upload.status == UPLOAD_FILE_END) {
        if (Update.end(true)) {
            server.send(200, "text/plain", "Update complete. Restarting...");
            delay(1000);
            ESP.restart();
        } else {
            server.send(500, "text/plain", "Update failed");
        }
    }
}

// Add this function near other reset handlers
void handleResetOdometer() {
    totalDistanceKm = 0.0;
    lastSavedKm = 0.0;
    saveOdometerToEEPROM();
    
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Odometer reset successfully\"}");
}

// Add this function to save fuel fill data to EEPROM
void saveFuelFillDataToEEPROM() {
    EEPROM.put(FUEL_FILL_AVG_ADDR, fuelFillAverage);
    EEPROM.put(FUEL_FILL_DIST_ADDR, fuelFillDistance);
    EEPROM.put(LAST_FUEL_FILL_ADDR, lastFuelFillLiters);
    
    // Save fuelFillStarted flag at the end of fuel fill addresses
    int fuelFillStartedAddr = LAST_FUEL_FILL_ADDR + sizeof(float);
    EEPROM.put(fuelFillStartedAddr, fuelFillStarted);
    
    EEPROM.commit();
}

// Add this function to load fuel fill data from EEPROM
void loadFuelFillDataFromEEPROM() {
    EEPROM.get(FUEL_FILL_AVG_ADDR, fuelFillAverage);
    EEPROM.get(FUEL_FILL_DIST_ADDR, fuelFillDistance);
    EEPROM.get(LAST_FUEL_FILL_ADDR, lastFuelFillLiters);
    
    // Load fuelFillStarted flag
    int fuelFillStartedAddr = LAST_FUEL_FILL_ADDR + sizeof(float);
    EEPROM.get(fuelFillStartedAddr, fuelFillStarted);

    // Validate loaded data
    if (isnan(fuelFillAverage) || fuelFillAverage < 0) {
        fuelFillAverage = 0.0;
    }
    if (isnan(fuelFillDistance) || fuelFillDistance < 0) {
        fuelFillDistance = 0.0;
    }
    if (isnan(lastFuelFillLiters) || lastFuelFillLiters < 0) {
        lastFuelFillLiters = 0.0;
    }
    
    // Validate fuelFillStarted flag (in case of corrupted data)
    // If we have valid fuel fill data but no flag, assume tracking was active
    if (fuelFillDistance > 0.0 && lastFuelFillLiters > 0.0) {
        fuelFillStarted = true;
    }
}

// Add a reset function for fuel fill data
void resetFuelFillData() {
    fuelFillAverage = 0.0;
    fuelFillDistance = 0.0;
    
    // Set current fuel level as the new baseline and start tracking immediately
    float currentFuel = getStableFuelReading();
    lastFuelFillLiters = currentFuel;
    fuelFillStarted = true;  // Start tracking immediately
    
    saveFuelFillDataToEEPROM();
    
  
}

void readGPSModule() {
  while (gpsSerial.available() > 0) {
    gps.encode(gpsSerial.read());
  }
  // If we have a valid speed from GPS, update currentSpeed
  if (gps.location.isValid() && gps.speed.isValid()) {
    currentSpeed = gps.speed.kmph();
    lastLat = gps.location.lat();
    lastLng = gps.location.lng();
    lastGpsUpdate = millis();
  } else {
    // GPS not valid - no speed data available
    currentSpeed = 0.0;
  }
}

void setup() {
  Serial.begin(115200);
  
  // Enable watchdog timer
  ESP.wdtEnable(WDTO_8S);  // 8 second timeout
  
  // Initialize EEPROM
  EEPROM.begin(EEPROM_SIZE);

  // Load saved data
  loadAllDataFromEEPROM();
  
  // Initialize LCD
  lcd.init();
  lcd.backlight();
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Activa Dashboard");
  lcd.setCursor(0, 1);
  lcd.print("Starting...");
  
  // Initialize GPS
  gpsSerial.begin(9600);

  // Initialize WiFi with reduced TX power
  WiFi.mode(WIFI_AP);
  WiFi.setOutputPower(15); // Reduce power to 15dBm for stability
  WiFi.softAP(ssid, password, 1, false, 4); // Channel 1, not hidden, max 4 clients
  myIP = WiFi.softAPIP();
  
  // Initialize MDNS
  if (MDNS.begin("activa")) {
  
  }
  
  // Initialize DNS server
  dnsServer.start(53, "*", myIP);
  
  // Initialize HTTP update server
  httpUpdater.setup(&server);
  
  // Add root endpoint
  server.on("/", HTTP_GET, []() {
    server.send(200, "text/html", INDEX_HTML);
  });

  // Add status endpoint
  server.on("/status", [&]() {
    String status = "{\"connected_clients\": " + String(WiFi.softAPgetStationNum()) + "}";
    server.send(200, "application/json", status);
  });

  // Add dashboard endpoints
  server.on("/dashboard", HTTP_GET, []() {
    server.send(200, "text/html", INDEX_HTML);
  });
  
  server.on("/dashboard-data", HTTP_GET, []() {
    static unsigned long lastDashboardRequest = 0;
    if (millis() - lastDashboardRequest < 500) { // Rate limit to 2 requests per second
      server.send(429, "application/json", "{\"error\":\"Too many requests\"}");
      return;
    }
    lastDashboardRequest = millis();
    handleDashboardData();
  });
  
  server.on("/reset-trip/1", HTTP_POST, []() {
    resetTrip(1);
    String json = "{";
    json += "\"status\":\"success\",";
    json += "\"message\":\"Trip 1 reset successfully\",";
    json += "\"trip1Distance\":0.0,";
    json += "\"trip1Fuel\":0.0,";
    json += "\"trip1Average\":0.0,";
    json += "\"trip1Started\":false";
    json += "}";
    server.send(200, "application/json", json);
  });
  server.on("/reset-fuel-fill", HTTP_POST, []() {
    resetFuelFillData();
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Fuel fill data reset successfully\"}");
  });

  // GPS worker endpoint removed - using physical GPS module only

  // Add trip toggle endpoint
  server.on("/toggle-trip/1", HTTP_POST, []() {
    trip1Started = !trip1Started;
    saveTripToEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started);
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Trip 1 " + String(trip1Started ? "started" : "stopped") + "\"}");
  });

  // Add trip 2 toggle endpoint
  server.on("/toggle-trip/2", HTTP_POST, []() {
    trip2Started = !trip2Started;
    saveTripToEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started);
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Trip 2 " + String(trip2Started ? "started" : "stopped") + "\"}");
  });

  // Add odometer reset endpoint
  server.on("/reset-odometer", HTTP_POST, []() {
    totalDistanceKm = 0.0;
    lastSavedKm = 0.0;
    saveOdometerToEEPROM();
    
    String json = "{";
    json += "\"status\":\"success\",";
    json += "\"message\":\"Odometer reset successfully\",";
    json += "\"totalDistance\":0.0";
    json += "}";
    server.send(200, "application/json", json);
  });

  // Add new endpoint handlers for starting trips
  server.on("/start-trip/1", HTTP_POST, []() {
    if (!trip1Started) {
      trip1Started = true;
      saveTripToEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started);
      server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Trip 1 started\"}");
    } else {
      server.send(200, "application/json", "{\"status\":\"info\",\"message\":\"Trip 1 already started\"}");
    }
  });

  // Add start-trip/2 endpoint handler
  server.on("/start-trip/2", HTTP_POST, []() {
    if (!trip2Started) {
      trip2Started = true;
      saveTripToEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started);
      server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Trip 2 started\"}");
    } else {
      server.send(200, "application/json", "{\"status\":\"info\",\"message\":\"Trip 2 already started\"}");
    }
  });

  server.on("/reset-trip/2", HTTP_POST, []() {
    resetTrip(2);
    String json = "{";
    json += "\"status\":\"success\",";
    json += "\"message\":\"Trip 2 reset successfully\",";
    json += "\"trip2Distance\":0.0,";
    json += "\"trip2Fuel\":0.0,";
    json += "\"trip2Average\":0.0,";
    json += "\"trip2Started\":false";
    json += "}";
    server.send(200, "application/json", json);
  });

  // Add test endpoint for fuel fill simulation
  server.on("/test-fuel-fill", HTTP_POST, []() {
    // Get current fuel level and set as new baseline
    float currentFuel = getStableFuelReading();
    
    // Reset fuel fill data and start tracking from current level
    lastFuelFillLiters = currentFuel;
    fuelFillDistance = 0.0;
    fuelFillAverage = 0.0;
    fuelFillStarted = true;
    saveFuelFillDataToEEPROM();
    
    String message = "Fuel fill test triggered - tracking started from " + String(currentFuel, 1) + "L";
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"" + message + "\"}");
  });

  // Add the /sync endpoint
  server.on("/sync", HTTP_POST, []() {
    String body = server.arg("plain");
    DynamicJsonDocument doc(512);
    DeserializationError error = deserializeJson(doc, body);
    if (error) {
        server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Invalid JSON\"}");
        return;
    }
    double appOdo = doc["odo"] | 0.0;
    double appTrip1 = doc["trip1"] | 0.0;
    double appTrip2 = doc["trip2"] | 0.0;
    int appTrip1ResetCount = doc["trip1ResetCount"] | 0;
    int appTrip2ResetCount = doc["trip2ResetCount"] | 0;

    // Always keep the maximum value for each field
    totalDistanceKm = max(totalDistanceKm, appOdo);
    
    // Handle Trip 1 reset and sync
    if (appTrip1ResetCount > trip1ResetCount) {
        trip1DistanceKm = 0.0;
        trip1ResetCount = appTrip1ResetCount;
    } else {
    trip1DistanceKm = max(trip1DistanceKm, appTrip1);
    }
    
    // Handle Trip 2 reset and sync
    if (appTrip2ResetCount > trip2ResetCount) {
        trip2DistanceKm = 0.0;
        trip2ResetCount = appTrip2ResetCount;
    } else {
    trip2DistanceKm = max(trip2DistanceKm, appTrip2);
    }

    // Save all data immediately
    saveAllDataToEEPROM();
    EEPROM.put(TRIP1_RESET_COUNT_ADDR, trip1ResetCount);
    EEPROM.put(TRIP2_RESET_COUNT_ADDR, trip2ResetCount);
    EEPROM.commit();

    // Send back the synchronized values
    String response = "{\"status\":\"ok\",\"odo\":" + String(totalDistanceKm, 2) + 
                     ",\"trip1\":" + String(trip1DistanceKm, 2) + 
                     ",\"trip2\":" + String(trip2DistanceKm, 2) + 
                     ",\"trip1ResetCount\":" + String(trip1ResetCount) + 
                     ",\"trip2ResetCount\":" + String(trip2ResetCount) + "}";
    server.send(200, "application/json", response);
  });

  server.begin();
}

void loop() {
  // Feed watchdog timer
  ESP.wdtFeed();
  
  // Check free heap and restart if too low
  if (ESP.getFreeHeap() < 4000) { // 4KB minimum
    ESP.restart();
    return;
  }

  dnsServer.processNextRequest();
  MDNS.update();
  server.handleClient();
  
  // Update speed and distance calculations more frequently
  static unsigned long lastSpeedUpdate = 0;
  unsigned long currentMillis = millis();
  
  if (currentMillis - lastSpeedUpdate >= SPEED_UPDATE_INTERVAL) {
    lastSpeedUpdate = currentMillis;
    calculateSpeedAndDistance();
  }

  // Handle main button
  int reading = digitalRead(buttonPin);
  if (reading != lastButtonState) {
    lastDebounceTime = millis();
    if (reading == LOW) {
      buttonPressStartTime = millis();
    }
  }
  
  if ((millis() - lastDebounceTime) > debounceDelay) {
    if (reading != buttonState) {
      buttonState = reading;
      if (buttonState == HIGH) {
        if (millis() - buttonPressStartTime < longPressDuration) {
          switchScreen();
        } else {
          if (currentScreen == 2) {
            resetTrip(1);
            screenChanged = true;
          } else if (currentScreen == 3) {
            resetTrip(2);
            screenChanged = true;
          } else if (currentScreen == 4) {
            resetFuelFillData();
            screenChanged = true;
          }
        }
      }
    }
  }
  
  lastButtonState = reading;

  // Handle flash button
  int flashReading = digitalRead(flashButtonPin);
  if (flashReading != lastFlashButtonState) {
    lastFlashDebounceTime = millis();
  }
  
  if ((millis() - lastFlashDebounceTime) > debounceDelay) {
    if (flashReading != flashButtonState) {
      flashButtonState = flashReading;
      if (flashButtonState == HIGH) {  // Button released
        // Reset both trips and fuel fill data when flash button is pressed
        resetTrip(1);
        resetTrip(2);
        resetFuelFillData();
        screenChanged = true;
        
        // Show reset message on LCD
        lcd.clear();
        lcd.setCursor(0, 0);
        lcd.print("All Data Reset!");
        delay(1000);
        screenChanged = true;  // Force screen redraw
      }
    }
  }
  
  lastFlashButtonState = flashReading;

  // Automatic screen switching with different intervals
  unsigned long currentInterval = (currentScreen == 0) ? SPEED_SCREEN_INTERVAL : OTHER_SCREEN_INTERVAL;
  if (millis() - lastScreenSwitch >= currentInterval) {
    switchScreen();
  }

  unsigned long now = millis();
  if (now - lastUpdate >= 1000) {
    lastUpdate = now;

    // Fuel reading and instant economy calculation
    if (millis() - lastFuelReading >= FUEL_READING_INTERVAL) {
      lastFuelReading = millis();
      float currentFuelLiters = getStableFuelReading();
      
      // Update displayed fuel value
      lastDisplayedFuelLiters = currentFuelLiters;
      
      // Calculate fuel consumption rate (liters per hour)
      float fuelConsumptionRate = 0.0;
      if (currentSpeed >= MIN_VALID_SPEED) {  // Only calculate when moving at valid speed
        // Calculate fuel consumption based on speed and fuel level change
        float fuelChange = lastFuelLiters - currentFuelLiters;
        
        // Only consider significant fuel changes
        if (abs(fuelChange) >= FUEL_CHANGE_THRESHOLD) {
          // Convert to liters per hour
          fuelConsumptionRate = (fuelChange * 3600.0) / FUEL_READING_INTERVAL;
          
          // Calculate instant fuel economy (km/L)
          if (fuelConsumptionRate > 0) {
            instantFuelEconomy = currentSpeed / fuelConsumptionRate;
          }
          
          // Update trip fuel used only for significant changes
          if (trip1Started) {
            trip1FuelUsed += fuelChange;
            if (trip1FuelUsed > 0) {
              trip1FuelAverage = trip1DistanceKm / trip1FuelUsed;
            }
          }
          if (trip2Started) {
            trip2FuelUsed += fuelChange;
            if (trip2FuelUsed > 0) {
              trip2FuelAverage = trip2DistanceKm / trip2FuelUsed;
            }
          }
        }
      }
      lastFuelLiters = currentFuelLiters;
    }

    // Update the EEPROM save logic in the main loop
    if (millis() - lastEEPROMWrite >= EEPROM_SAVE_INTERVAL) {
        // Save if any of these conditions are met:
        // 1. Distance has changed
        // 2. Any trip is active
        // 3. Fuel fill tracking is active
        if (abs(totalDistanceKm - lastSavedKm) >= DISTANCE_CHANGE_THRESHOLD ||
            trip1Started || trip2Started || fuelFillStarted) {
            saveAllDataToEEPROM();
        }
    }

    // Add immediate save after significant events
    if (trip1Started || trip2Started) {
        static unsigned long lastTripSave = 0;
        if (millis() - lastTripSave >= 1000) {  // Save trip data every second when active
            saveAllDataToEEPROM();
            lastTripSave = millis();
        }
    }
  }

  // Check for update errors
  checkUpdateStatus();

  // Add periodic data printing
  static unsigned long lastDataPrint = 0;
  if (millis() - lastDataPrint >= 5000) { // Print every 5 seconds
    lastDataPrint = millis();
    
    // GPS Time and Date Status (IST 12-hour format)
    if (gps.time.isValid() && gps.date.isValid()) {
      // Convert UTC to IST (UTC + 5:30)
      int utcHour = gps.time.hour();
      int utcMinute = gps.time.minute();
      int utcSecond = gps.time.second();
      int utcDay = gps.date.day();
      int utcMonth = gps.date.month();
      int utcYear = gps.date.year();
      
      // Add 5 hours 30 minutes for IST
      int istMinute = utcMinute + 30;
      int istHour = utcHour + 5;
      int istDay = utcDay;
      int istMonth = utcMonth;
      int istYear = utcYear;
      
      // Handle minute overflow
      if (istMinute >= 60) {
        istMinute -= 60;
        istHour++;
      }
      
      // Handle hour overflow
      if (istHour >= 24) {
        istHour -= 24;
        istDay++;
        // Handle day overflow (simplified)
        if ((istMonth == 1 || istMonth == 3 || istMonth == 5 || istMonth == 7 || 
             istMonth == 8 || istMonth == 10 || istMonth == 12) && istDay > 31) {
          istDay = 1;
          istMonth++;
        } else if ((istMonth == 4 || istMonth == 6 || istMonth == 9 || istMonth == 11) && istDay > 30) {
          istDay = 1;
          istMonth++;
        } else if (istMonth == 2 && istDay > 28) {
          istDay = 1;
          istMonth++;
        }
        
        // Handle month overflow
        if (istMonth > 12) {
          istMonth = 1;
          istYear++;
        }
      }
      
      // Convert to 12-hour format
      bool isPM = istHour >= 12;
      int displayHour = istHour;
      if (displayHour == 0) displayHour = 12;  // Midnight = 12 AM
      else if (displayHour > 12) displayHour -= 12;  // PM hours
      
      char timeStr[25];
      sprintf(timeStr, "%2d:%02d:%02d %s IST", 
              displayHour, istMinute, utcSecond, isPM ? "PM" : "AM");
      Serial.print(timeStr);
      char dateStr[15];
      sprintf(dateStr, " %02d/%02d/%04d", 
              istDay, istMonth, istYear);
      Serial.println(dateStr);
    } else {
      Serial.println("WAITING FOR GPS TIME SYNC");
    }
  }

  // Read GPS data from module
  readGPSModule();

  // Add this near the top of the loop
  static unsigned long lastFuelCheck = 0;
  if (millis() - lastFuelCheck >= 1000) {  // Check every second
      lastFuelCheck = millis();
      checkFuelFill();
  }

  // Update display with current values
  static unsigned long lastDisplayUpdate = 0;
  if (millis() - lastDisplayUpdate >= 200) {  // Update display every 200ms
    lastDisplayUpdate = millis();
    float fuelPercentage = (lastDisplayedFuelLiters / maxFuelLiters) * 100.0;
    updateDisplay(currentSpeed, fuelPercentage, lastDisplayedFuelLiters, 
                  totalDistanceKm, trip1DistanceKm, trip1FuelAverage,
                  trip2DistanceKm, trip2FuelAverage);
  }
}