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

// Navigation related declarations
struct NavigationData {
  char direction;  // 'U'=Up, 'D'=Down, 'L'=Left, 'R'=Right, 'T'=U-turn, 'A'=Roundabout
  float distance;  // Distance in meters to next turn
  char streetName[32];  // Current street name (increased size)
  char instruction[128]; // Navigation instruction
  int currentStep;     // Current navigation step
  int totalSteps;      // Total navigation steps
  float bearing;       // Current bearing
  bool isNavigating;   // Navigation status
  float speed;         // Speed in km/h from navigation
};

NavigationData currentNavigation;
bool hasNavigation = false;
unsigned long lastNavUpdate = 0;
const unsigned long NAV_SCREEN_DURATION = 30000; // 30 seconds
unsigned long navScreenStartTime = 0;

// Custom characters for navigation arrows
byte arrowUp[8] = {
  B00100,
  B01110,
  B11111,
  B00100,
  B00100,
  B00100,
  B00100,
  B00000
};

byte arrowDown[8] = {
  B00100,
  B00100,
  B00100,
  B00100,
  B11111,
  B01110,
  B00100,
  B00000
};

byte arrowLeft[8] = {
  B00000,
  B00100,
  B01100,
  B11111,
  B01100,
  B00100,
  B00000,
  B00000
};

byte arrowRight[8] = {
  B00000,
  B00100,
  B00110,
  B11111,
  B00110,
  B00100,
  B00000,
  B00000
};

byte arrowUturn[8] = {
  B00010,
  B00100,
  B01000,
  B11111,
  B01000,
  B01000,
  B01110,
  B00000
};

byte arrowRoundabout[8] = {
  B00100,
  B01010,
  B10001,
  B10001,
  B10001,
  B01010,
  B00100,
  B00000
};

// Helper function to get direction name
String getDirectionName(char direction) {
  switch(direction) {
    case 'U': return "Up";
    case 'D': return "Down";
    case 'L': return "Left";
    case 'R': return "Right";
    case 'T': return "U-turn";
    case 'A': return "Roundabout";
    default: return "Unknown";
  }
}

// Power consumption monitoring
const float ESP_VOLTAGE = 3.3;  // ESP8266 operating voltage
float currentConsumption = 0.0;  // Current consumption in mA
float powerConsumption = 0.0;    // Power consumption in mW
unsigned long lastPowerUpdate = 0;
const unsigned long POWER_UPDATE_INTERVAL = 1000;  // Update power consumption every second

// Function declarations
float getStableFuelReading();
void saveFuelFillDataToEEPROM();
void loadFuelFillDataFromEEPROM();
void resetFuelFillData();
void saveResetCountToEEPROM();
void saveTotalDistanceToEEPROM();

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
const unsigned long SPEED_UPDATE_INTERVAL = 100;  // Update speed every 100ms for smoother updates

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
const int BASE_SCREENS = 4;  // Base number of screens (Speed, Odo, Trip1, Trip2)
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

// EEPROM Addresses
const int TRIP1_ADDR = 0;
const int TRIP2_ADDR = 32;
const int FUEL_FILL_ADDR = 64;
const int RESET_COUNT_ADDR = 96;
const int TOTAL_DISTANCE_ADDR = 100;  // Add this line for total distance storage
const int MAX_EEPROM_ADDR = 512;  // ESP8266 has 512 bytes of EEPROM

// Add these variables with other variables
float fuelPercentage = 0.0;      // Current fuel percentage
float lastDisplayedFuelLiters = 0.0;  // Last displayed fuel value
unsigned long lastFuelReading = 0;  // Last time fuel was read
float fuelReadings[FUEL_READING_SAMPLES];  // Array to store fuel readings for moving average
int fuelReadingIndex = 0;  // Index for fuel readings array

// Add global variables for auto-restart and LCD restart interval
unsigned long lastLCDRestartTime = 0;
bool autoRestartEnabled = true;  // Flag to enable/disable auto-restart - set to true by default
const unsigned long LCD_RESTART_INTERVAL = 120000;  // 2 minutes in milliseconds

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
const unsigned long LCD_UPDATE_INTERVAL = 200;           // Update LCD every 0.2 seconds for smoother display

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
  EEPROM.put(TRIP1_ADDR, totalDistanceKm);
  EEPROM.write(TRIP1_ADDR + sizeof(double), checksum);
  EEPROM.commit();
}

// Function to load odometer from EEPROM
bool loadOdometerFromEEPROM() {
  double loadedKm;
  EEPROM.get(TRIP1_ADDR, loadedKm);
  uint8_t storedChecksum = EEPROM.read(TRIP1_ADDR + sizeof(double));
  uint8_t calculatedChecksum = calculateChecksum(loadedKm);
  
  if (storedChecksum == calculatedChecksum && 
      !isnan(loadedKm) && 
      loadedKm >= 0.0 && 
      loadedKm < 999999.99) {
    totalDistanceKm = loadedKm;
    return true;
  }
  
  return false;
}

// EEPROM address for resetCount
int resetCount = 0;

// Add these variables near other reset/trip related variables
const int TRIP1_RESET_COUNT_ADDR = RESET_COUNT_ADDR + sizeof(int);  // Address for Trip 1 reset count
const int TRIP2_RESET_COUNT_ADDR = TRIP1_RESET_COUNT_ADDR + sizeof(int);  // Address for Trip 2 reset count
int trip1ResetCount = 0;
int trip2ResetCount = 0;

// Update resetTrip function with debug output
void resetTrip(int tripNum) {
    Serial.println("\n=== Resetting Trip " + String(tripNum) + " ===");
    
    if (tripNum == 1) {
        trip1DistanceKm = 0.0;
        trip1FuelUsed = 0.0;
        trip1FuelAverage = 0.0;
        trip1Started = true;  // Always keep trip started
        saveTripToEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started);
        Serial.println("Trip 1 reset to zero");
    } else if (tripNum == 2) {
        trip2DistanceKm = 0.0;
        trip2FuelUsed = 0.0;
        trip2FuelAverage = 0.0;
        trip2Started = true;  // Always keep trip started
        saveTripToEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started);
        Serial.println("Trip 2 reset to zero");
    }
    
    resetCount++;
    saveResetCountToEEPROM();
    Serial.println("Reset count: " + String(resetCount));
    Serial.println("===========================");
}

// Function to get total number of screens
int getTotalScreens() {
  return hasNavigation ? (BASE_SCREENS + 1) : BASE_SCREENS;
}

// Function to handle navigation data from app
void handleNavigationData() {
  if (server.hasArg("plain")) {
    String json = server.arg("plain");
    DynamicJsonDocument doc(1024); // Increased buffer size
    DeserializationError error = deserializeJson(doc, json);
    
    if (!error) {
      // Update navigation data
      hasNavigation = true;
      currentNavigation.isNavigating = true;
      
      // Get maneuver type and convert to direction
      const char* maneuver = doc["maneuver"].as<const char*>();
      if (strcmp(maneuver, "turn-right") == 0) currentNavigation.direction = 'R';
      else if (strcmp(maneuver, "turn-left") == 0) currentNavigation.direction = 'L';
      else if (strcmp(maneuver, "uturn-right") == 0 || strcmp(maneuver, "uturn-left") == 0) currentNavigation.direction = 'T';
      else if (strcmp(maneuver, "roundabout-right") == 0 || strcmp(maneuver, "roundabout-left") == 0) currentNavigation.direction = 'A';
      else currentNavigation.direction = 'U'; // Straight/default
      
      // Get distance from the next_instruction
      const char* distanceStr = doc["distance_to_next"].as<const char*>();
      float distance = 0;
      if (strstr(distanceStr, "km") != NULL) {
        distance = atof(distanceStr) * 1000; // Convert km to meters
      } else {
        distance = atof(distanceStr); // Already in meters
      }
      currentNavigation.distance = distance;
      
      // Get next instruction and street name
      strncpy(currentNavigation.instruction, doc["next_instruction"].as<const char*>(), 127);
      currentNavigation.instruction[127] = '\0'; // Ensure null termination
      
      // Clean HTML tags from instruction text
      String cleanedInstruction = cleanHtmlTags(String(currentNavigation.instruction));
      strncpy(currentNavigation.instruction, cleanedInstruction.c_str(), 127);
      currentNavigation.instruction[127] = '\0'; // Ensure null termination
      
      // Extract street name from instruction (simplified)
      const char* onto = strstr(currentNavigation.instruction, "onto");
      if (onto != NULL) {
        strncpy(currentNavigation.streetName, onto + 5, 31); // Skip "onto "
      } else {
        strncpy(currentNavigation.streetName, "Unknown Street", 31);
      }
      currentNavigation.streetName[31] = '\0'; // Ensure null termination
      
      // Get step information
      currentNavigation.currentStep = doc["current_step"].as<int>();
      currentNavigation.totalSteps = doc["total_steps"].as<int>();
      currentNavigation.bearing = doc["bearing"].as<float>();
      
      // Force switch to navigation screen and reset its timer
      currentScreen = 4; // Navigation screen
      navScreenStartTime = millis();
      screenChanged = true;
      
      // Print navigation update to serial monitor
      Serial.println("\n=== Navigation Update ===");
      Serial.print("Direction: ");
      Serial.print(getDirectionName(currentNavigation.direction));
      Serial.print(" (");
      Serial.print(currentNavigation.direction);
      Serial.println(")");
      Serial.print("Distance: ");
      if (currentNavigation.distance >= 1000) {
        Serial.print(currentNavigation.distance / 1000.0, 1);
        Serial.println(" km");
      } else {
        Serial.print((int)currentNavigation.distance);
        Serial.println(" m");
      }
      Serial.print("Street: ");
      Serial.println(currentNavigation.streetName);
      Serial.print("Step: ");
      Serial.print(currentNavigation.currentStep + 1);
      Serial.print("/");
      Serial.println(currentNavigation.totalSteps);
      Serial.println("Navigation screen activated");
      Serial.println("========================");
      
      server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Navigation updated\"}");
    } else {
      Serial.println("Error: Invalid navigation JSON received");
      server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Invalid JSON\"}");
    }
  } else {
    Serial.println("Error: No navigation data received");
    server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"No data received\"}");
  }
}

// Update switchScreen function to log navigation screen changes
void switchScreen() {
  int prevScreen = currentScreen;
  int totalScreens = getTotalScreens();
  currentScreen = (currentScreen + 1) % totalScreens;
  
  // Skip navigation screen if it's been showing for more than 30 seconds
  if (currentScreen == 4 && hasNavigation) {
    unsigned long navScreenDuration = millis() - navScreenStartTime;
    if (navScreenDuration >= NAV_SCREEN_DURATION) {
      currentScreen = 0;  // Go back to speed screen
      Serial.println("\n=== Navigation Timeout ===");
      Serial.println("Navigation screen timed out after 30 seconds");
      Serial.println("Switching back to speed screen");
      Serial.println("========================");
    }
  }
  
  screenChanged = true;
  lastScreenSwitch = millis();
  
  // Update navigation screen start time if switching to it
  if (currentScreen == 4) {
    navScreenStartTime = millis();
    Serial.println("\n=== Navigation Screen ===");
    Serial.print("Direction: ");
    Serial.print(getDirectionName(currentNavigation.direction));
    Serial.print(" (");
    Serial.print(currentNavigation.direction);
    Serial.println(")");
    Serial.print("Distance: ");
    if (currentNavigation.distance >= 1000) {
      Serial.print(currentNavigation.distance / 1000.0, 1);
      Serial.println(" km");
    } else {
      Serial.print((int)currentNavigation.distance);
      Serial.println(" m");
    }
    Serial.print("Street: ");
    Serial.println(currentNavigation.streetName);
    Serial.println("Screen will timeout in 30 seconds");
    Serial.println("========================");
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

// LCD Backlight Control
bool isNightMode = false;
unsigned long lastBacklightUpdate = 0;
const unsigned long BACKLIGHT_UPDATE_INTERVAL = 60000;  // Check every minute
const int DAY_BACKLIGHT = 0;       // Backlight off during day
const int NIGHT_BACKLIGHT = 1;     // Backlight on during night

// Function to control LCD backlight based on time
void updateLCDBacklight() {
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
    
    // Determine if it's night time (between 6 PM and 6 AM)
    bool shouldBeNightMode = (istHour >= 18 || istHour < 6);
    
    // Only update if the mode needs to change
    if (shouldBeNightMode != isNightMode) {
      isNightMode = shouldBeNightMode;
      if (isNightMode) {
        lcd.backlight();
        Serial.println("Switching to night mode - Backlight on");
      } else {
        lcd.noBacklight();
        Serial.println("Switching to day mode - Backlight off");
      }
    }
  }
}

// Update the updateDisplay function to include power consumption screen
void updateDisplay(float speedKmph, float powerConsumption, float currentConsumption, 
                  double totalDistanceKm, double trip1DistanceKm, float trip1FuelAverage,
                  double trip2DistanceKm, float trip2FuelAverage) {
  static unsigned long lastLcdUpdate = 0;
  unsigned long currentMillis = millis();
  
  // Check if navigation screen should timeout
  if (currentScreen == 4 && hasNavigation) {
    unsigned long navScreenDuration = currentMillis - navScreenStartTime;
    if (navScreenDuration >= NAV_SCREEN_DURATION) {
      Serial.println("\n=== Navigation Timeout ===");
      Serial.println("Navigation screen timed out after 30 seconds");
      Serial.println("Switching back to speed screen");
      Serial.println("========================");
      
      currentScreen = 0;  // Switch back to speed screen
      screenChanged = true;
    }
  }
  
  if (currentMillis - lastLcdUpdate >= LCD_UPDATE_INTERVAL) {
    lastLcdUpdate = currentMillis;
    
    if (screenChanged) {
      lcd.clear();
      screenChanged = false;
    }
    
    lcd.setCursor(0, 0);

    if (currentScreen == 0) {
      // Screen 0: Speed + Fuel Readings
      lcd.print("SPEED:");
      lcd.setCursor(7, 0);
      char speedStr[8];
      dtostrf(speedKmph, 6, 1, speedStr);
      lcd.print(speedStr);
      
      lcd.setCursor(0, 1);
      lcd.print("FUEL:");
      lcd.print(lastDisplayedFuelLiters, 1);
      lcd.print(" L");
      
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
      // Screen 4: Navigation
      if (hasNavigation) {
        // First line: Direction arrow and distance
        switch(currentNavigation.direction) {
          case 'U': lcd.write(byte(0)); break; // Up arrow
          case 'D': lcd.write(byte(1)); break; // Down arrow
          case 'L': lcd.write(byte(2)); break; // Left arrow
          case 'R': lcd.write(byte(3)); break; // Right arrow
          case 'T': lcd.write(byte(4)); break; // U-turn arrow
          case 'A': lcd.write(byte(5)); break; // Roundabout
          default: lcd.print(" "); break;
        }
        
        lcd.print(" ");
        if (currentNavigation.distance >= 1000) {
          lcd.print(currentNavigation.distance / 1000.0, 1);
          lcd.print(" km");
        } else {
          lcd.print((int)currentNavigation.distance);
          lcd.print(" m");
        }
        
        // Second line: Street name (scrolling if needed)
        lcd.setCursor(0, 1);
        static int scrollPos = 0;
        static unsigned long lastScroll = 0;
        int nameLen = strlen(currentNavigation.streetName);
        
        if (nameLen > 16) {
          // Scroll long street names
          if (currentMillis - lastScroll >= 500) { // Scroll every 500ms
            lastScroll = currentMillis;
            scrollPos = (scrollPos + 1) % nameLen;
          }
          
          for (int i = 0; i < 16; i++) {
            int pos = (scrollPos + i) % nameLen;
            lcd.print(currentNavigation.streetName[pos]);
          }
        } else {
          // Center short street names
          int padding = (16 - nameLen) / 2;
          for (int i = 0; i < padding; i++) lcd.print(" ");
          lcd.print(currentNavigation.streetName);
        }
      } else {
        // No navigation active
        lcd.print("Navigation");
        lcd.setCursor(0, 1);
        lcd.print("Not Active");
      }
    }
  }
}

// Add new GPS data variables
unsigned long lastGpsUpdate = 0;
const unsigned long GPS_UPDATE_INTERVAL = 1000;  // Update GPS data every second

// Removed handleGpsData function - app no longer sends GPS data

// Add test speed configuration
const bool TEST_MODE = false;  // Set to true to enable test speed
const float TEST_SPEED = 0.0;  // Test speed in km/h

// Update calculateSpeedAndDistance function to ensure speed is always available
void calculateSpeedAndDistance() {
    static unsigned long lastSpeedUpdate = 0;
    const unsigned long SPEED_UPDATE_INTERVAL = 1000;  // Update speed every second
    
    if (millis() - lastSpeedUpdate >= SPEED_UPDATE_INTERVAL) {
        lastSpeedUpdate = millis();
        
        // Prioritize navigation speed over GPS speed
        if (hasNavigation && currentNavigation.speed > 0) {
            currentSpeed = currentNavigation.speed;
            Serial.println("Using Navigation Speed: " + String(currentSpeed) + " km/h");
        } else if (gps.location.isValid() && gps.speed.isValid()) {
            currentSpeed = gps.speed.kmph();
            Serial.println("Using GPS Speed: " + String(currentSpeed) + " km/h");
        } else {
            currentSpeed = 0.0;  // Fallback to 0 if no valid speed data
            Serial.println("No valid speed data available");
        }
        
        // Only update distances if speed is above minimum threshold
        if (currentSpeed >= MIN_VALID_SPEED) {
            // Calculate distance increment based on current speed
            float distanceIncrement = (currentSpeed * SPEED_UPDATE_INTERVAL) / 3600000.0;  // Convert km/h to km for the interval
            
            // Update total distance
            if (distanceIncrement <= MAX_DISTANCE_THRESHOLD) {
                totalDistanceKm += distanceIncrement;
                saveTotalDistanceToEEPROM();
                
                // Update trip distances if they are active
                if (trip1Started) {
                    trip1DistanceKm += distanceIncrement;
                    if (trip1DistanceKm > 0 && trip1FuelUsed > 0) {
                        trip1FuelAverage = trip1DistanceKm / trip1FuelUsed;
                    }
                    saveTripToEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started);
                }
                
                if (trip2Started) {
                    trip2DistanceKm += distanceIncrement;
                    if (trip2DistanceKm > 0 && trip2FuelUsed > 0) {
                        trip2FuelAverage = trip2DistanceKm / trip2FuelUsed;
                    }
                    saveTripToEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started);
                }
                
                // Update fuel fill distance if tracking is active
                if (fuelFillStarted) {
                    fuelFillDistance += distanceIncrement;
                    saveFuelFillDataToEEPROM();
                }
            }
            
            // Print detailed distance information
            Serial.println("\n=== Distance Details ===");
            Serial.print("Speed Source: ");
            if (hasNavigation && currentNavigation.speed > 0) {
                Serial.println("Navigation");
            } else if (gps.location.isValid() && gps.speed.isValid()) {
                Serial.println("GPS");
            } else {
                Serial.println("None");
            }
            Serial.print("Current Speed: ");
            Serial.print(currentSpeed);
            Serial.println(" km/h");
            Serial.print("Distance Increment: ");
            Serial.print(distanceIncrement * 1000); // Convert to meters for display
            Serial.println(" m");
            
            Serial.println("\n--- Odometer ---");
            Serial.print("Total Distance: ");
            Serial.print(totalDistanceKm);
            Serial.println(" km");
            
            Serial.println("\n--- Trip 1 ---");
            Serial.print("Distance: ");
            Serial.print(trip1DistanceKm);
            Serial.println(" km");
            Serial.println("Status: Running");
            
            Serial.println("\n--- Trip 2 ---");
            Serial.print("Distance: ");
            Serial.print(trip2DistanceKm);
            Serial.println(" km");
            Serial.println("Status: Running");
            Serial.println("===================");
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

// Function to handle trip reset
void handleResetTrip() {
    if (server.hasArg("trip")) {
        int tripNum = server.arg("trip").toInt();
        Serial.print("Resetting trip ");
        Serial.println(tripNum);
        
        resetTrip(tripNum);
        
        String json = "{";
        json += "\"status\":\"success\",";
        json += "\"message\":\"Trip " + String(tripNum) + " reset successfully\",";
        json += "\"tripDistance\":0.0,";
        json += "\"tripFuel\":0.0,";
        json += "\"tripAverage\":0.0";
        json += "}";
    
    server.send(200, "application/json", json);
                } else {
        server.send(400, "application/json", "{\"status\":\"error\",\"message\":\"Trip number not specified\"}");
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
    saveOdometerToEEPROM();
    
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Odometer reset successfully\"}");
}

// Add this function to save fuel fill data to EEPROM
void saveFuelFillDataToEEPROM() {
    EEPROM.put(FUEL_FILL_ADDR, fuelFillAverage);
    EEPROM.put(FUEL_FILL_ADDR + sizeof(float), fuelFillDistance);
    EEPROM.put(FUEL_FILL_ADDR + 2 * sizeof(float), lastFuelFillLiters);
    
    // Save fuelFillStarted flag at the end of fuel fill addresses
    int fuelFillStartedAddr = FUEL_FILL_ADDR + 3 * sizeof(float);
    EEPROM.put(fuelFillStartedAddr, fuelFillStarted);
    
    EEPROM.commit();
}

// Add this function to load fuel fill data from EEPROM
void loadFuelFillDataFromEEPROM() {
    EEPROM.get(FUEL_FILL_ADDR, fuelFillAverage);
    EEPROM.get(FUEL_FILL_ADDR + sizeof(float), fuelFillDistance);
    EEPROM.get(FUEL_FILL_ADDR + 2 * sizeof(float), lastFuelFillLiters);
    
    // Load fuelFillStarted flag
    int fuelFillStartedAddr = FUEL_FILL_ADDR + 3 * sizeof(float);
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
  // Only use GPS speed if navigation speed is not available
  if (!hasNavigation || currentNavigation.speed <= 0) {
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
  // If navigation is active and has valid speed, keep using navigation speed
}

// Function to calculate power consumption
void calculatePowerConsumption() {
  // Read analog value from current sensor (assuming it's connected to A0)
  int rawValue = analogRead(A0);
  
  // Convert raw value to current (mA)
  // Assuming the current sensor gives 0-1023 for 0-100mA range
  currentConsumption = (rawValue * 100.0) / 1023.0;
  
  // Calculate power consumption (mW)
  powerConsumption = currentConsumption * ESP_VOLTAGE;
  
  // Print detailed power consumption data
  // Serial.println("\n=== Power Consumption Details ===");
  // Serial.print("Raw ADC Value: ");
  // Serial.println(rawValue);
  // Serial.print("Current Consumption: ");
  // Serial.print(currentConsumption);
  // Serial.println(" mA");
  // Serial.print("Power Consumption: ");
  // Serial.print(powerConsumption);
  // Serial.println(" mW");
  // Serial.print("Voltage: ");
  // Serial.print(ESP_VOLTAGE);
  // Serial.println(" V");
  // Serial.print("LCD Backlight: ");
  // Serial.println(isNightMode ? "ON (Night Mode)" : "OFF (Day Mode)");
  // Serial.println("==============================\n");
}

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
    EEPROM.put(TRIP1_ADDR + sizeof(TripData), instantFuelEconomy);
    EEPROM.put(TRIP1_ADDR + sizeof(TripData) + sizeof(float), lastFuelLiters);
    // Save resetCount
    EEPROM.put(RESET_COUNT_ADDR, resetCount);
    
    // Commit changes
    EEPROM.commit();
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
    trip1Started = true;  // Always start trip 1
    saveTripToEEPROM(TRIP1_ADDR, trip1DistanceKm, trip1FuelUsed, trip1FuelAverage, instantFuelEconomy, trip1Started);
  } else {
    trip1Started = true;  // Ensure trip 1 is always started
  }
  
  if (!loadTripFromEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started)) {
    trip2DistanceKm = 0.0;
    trip2FuelUsed = 0.0;
    trip2FuelAverage = 0.0;
    trip2Started = true;  // Always start trip 2
    saveTripToEEPROM(TRIP2_ADDR, trip2DistanceKm, trip2FuelUsed, trip2FuelAverage, instantFuelEconomy, trip2Started);
  } else {
    trip2Started = true;  // Ensure trip 2 is always started
  }
  
  EEPROM.get(TRIP1_ADDR + sizeof(TripData), instantFuelEconomy);
  if (isnan(instantFuelEconomy)) {
    instantFuelEconomy = 0.0;
  }
  
  EEPROM.get(TRIP1_ADDR + sizeof(TripData) + sizeof(float), lastFuelLiters);
  if (isnan(lastFuelLiters)) {
    lastFuelLiters = 0.0;
  }

  // Load fuel fill data
  loadFuelFillDataFromEEPROM();
  
  // Load resetCount
  EEPROM.get(RESET_COUNT_ADDR, resetCount);
  if (resetCount < 0) resetCount = 0;
}

// Add these function declarations after the other EEPROM-related functions

void saveResetCountToEEPROM() {
    EEPROM.put(RESET_COUNT_ADDR, resetCount);
    EEPROM.commit();
    Serial.println("Reset count saved to EEPROM: " + String(resetCount));
}

void saveTotalDistanceToEEPROM() {
    EEPROM.put(TOTAL_DISTANCE_ADDR, totalDistanceKm);
    EEPROM.commit();
    Serial.println("Total distance saved to EEPROM: " + String(totalDistanceKm));
}

// Add these with other global variables
unsigned long lastEEPROMWrite = 0;
double lastSavedKm = 0.0;
const unsigned long EEPROM_SAVE_INTERVAL = 5000;        // Save EEPROM every 5 seconds
const double DISTANCE_CHANGE_THRESHOLD = 0.001;  // Threshold for saving distance changes

// Add LCD backlight control function
void setLcdBacklight(int intensity) {
    if (intensity < 0) intensity = 0;
    if (intensity > 255) intensity = 255;
    lcd.setBacklight(intensity);
    Serial.print("LCD Backlight status: ");
    Serial.println(intensity);  // Log the current intensity
}

// Add web server endpoint for backlight control
void handleLcdBacklight() {
    if (server.hasArg("intensity")) {
        String intensityStr = server.arg("intensity");
        int intensity = intensityStr.toInt();
        setLcdBacklight(intensity);
        Serial.println("LCD Backlight set to intensity: " + String(intensity) + " (Status updated)");
        server.send(200, "text/plain", "Backlight set");
    } else if (server.hasArg("state")) {
        String state = server.arg("state");
        if (state == "on") {
            setLcdBacklight(255);
            Serial.println("LCD Backlight status: ON (Intensity: 255)");
            server.send(200, "text/plain", "Backlight on");
        } else if (state == "off") {
            setLcdBacklight(0);
            Serial.println("LCD Backlight status: OFF (Intensity: 0)");
            server.send(200, "text/plain", "Backlight off");
        } else {
            server.send(400, "text/plain", "Invalid state");
        }
    } else {
        server.send(400, "text/plain", "Missing parameters");
    }
}

// Rename the enum to avoid conflict
// enum DeviceWiFiState { INITIAL_ON, OFF_PERIOD, ON_UPDATE };
// DeviceWiFiState currentState = INITIAL_ON;  // Start in initial on state
// unsigned long stateStartTime = 0;
// const unsigned long INITIAL_ON_DURATION = 2 * 60 * 1000;  // 2 minutes in milliseconds
// const unsigned long ON_UPDATE_DURATION = 1 * 60 * 1000;  // 1 minute in milliseconds
// const unsigned long OFF_PERIOD_DURATION = 1 * 60 * 1000;  // 1 minute in milliseconds

// Add new function for LCD restart
void restartLCD() {
  lcd.init();  // Reinitialize LCD
  lcd.clear();  // Clear the display
  lcd.backlight();  // Turn on backlight
  screenChanged = true;  // Flag to redraw the screen
  Serial.println("LCD is restarting");
  autoRestartEnabled = true;  // Enable auto-restart
}

// Add navigation variables
String currentNavInstruction = "";
const unsigned long NAV_DISPLAY_INTERVAL = 3000;  // Show nav instructions for 3 seconds

void setup() {
  Serial.begin(115200);
  
  // Enable watchdog timer
  ESP.wdtEnable(WDTO_8S);  // 8 second timeout
  
  // Initialize LCD
  lcd.init();
  lcd.backlight();
  
  // Create custom characters for navigation
  lcd.createChar(0, arrowUp);
  lcd.createChar(1, arrowDown);
  lcd.createChar(2, arrowLeft);
  lcd.createChar(3, arrowRight);
  lcd.createChar(4, arrowUturn);
  lcd.createChar(5, arrowRoundabout);
  
  // Initialize EEPROM
  EEPROM.begin(MAX_EEPROM_ADDR);

  // Initialize trips as started
  trip1Started = true;
  trip2Started = true;
  
  // Ensure auto restart is enabled by default
  autoRestartEnabled = true;
  
  // Load saved data
  loadAllDataFromEEPROM();
  
  // Force trips to be started even after loading from EEPROM
  trip1Started = true;
  trip2Started = true;
  
  // Initialize GPS
  gpsSerial.begin(9600);

  // Initialize WiFi with reduced TX power
  WiFi.mode(WIFI_AP);
  WiFi.setOutputPower(15);  // Increase to 15 for better range
  WiFi.softAP(ssid, password, 1, false, 4);
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

  // Add LCD restart endpoint
  server.on("/lcd/restart", HTTP_POST, []() {
    Serial.println("LCD restart triggered from app");
    restartLCD();
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"LCD restarted\"}");
  });

  // Add GET endpoint for LCD restart (for compatibility with MainActivity)
  server.on("/restart-lcd", HTTP_GET, []() {
    Serial.println("LCD restart triggered from app (GET)");
    restartLCD();
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"LCD restarted\"}");
  });

  // Add LCD auto-restart control endpoint
  server.on("/lcd/auto-restart", HTTP_POST, []() {
    if (server.hasArg("enabled")) {
      autoRestartEnabled = server.arg("enabled") == "true";
      if (autoRestartEnabled) {
        lastLCDRestartTime = millis(); // Reset timer when enabling
      }
      server.send(200, "application/json", 
        "{\"status\":\"success\",\"message\":\"Auto-restart " + 
        String(autoRestartEnabled ? "enabled" : "disabled") + "\"}");
    } else {
      server.send(400, "application/json", 
        "{\"status\":\"error\",\"message\":\"Missing enabled parameter\"}");
    }
  });

  // Add dashboard data endpoint
  server.on("/dashboard-data", HTTP_GET, []() {
    // Debug output before creating JSON
    Serial.println("\n=== DASHBOARD DATA SENT TO APP ===");
    Serial.println("--- Speed & Distance Data ---");
    Serial.print("Current Speed: ");
    Serial.print(currentSpeed);
    Serial.println(" km/h");
    Serial.print("Total Distance (Odometer): ");
    Serial.print(totalDistanceKm);
    Serial.println(" km");
    
    Serial.println("\n--- Trip 1 Data ---");
    Serial.print("Trip 1 Distance: ");
    Serial.print(trip1DistanceKm);
    Serial.println(" km");
    Serial.print("Trip 1 Fuel Used: ");
    Serial.print(trip1FuelUsed);
    Serial.println(" L");
    Serial.print("Trip 1 Average: ");
    Serial.print(trip1FuelAverage);
    Serial.println(" km/L");
    Serial.print("Trip 1 Started: ");
    Serial.println(trip1Started ? "Yes" : "No");
    
    Serial.println("\n--- Trip 2 Data ---");
    Serial.print("Trip 2 Distance: ");
    Serial.print(trip2DistanceKm);
    Serial.println(" km");
    Serial.print("Trip 2 Fuel Used: ");
    Serial.print(trip2FuelUsed);
    Serial.println(" L");
    Serial.print("Trip 2 Average: ");
    Serial.print(trip2FuelAverage);
    Serial.println(" km/L");
    Serial.print("Trip 2 Started: ");
    Serial.println(trip2Started ? "Yes" : "No");
    
    Serial.println("\n--- Fuel Data ---");
    Serial.print("Fuel Percentage: ");
    Serial.print((lastDisplayedFuelLiters / maxFuelLiters) * 100.0);
    Serial.println(" %");
    Serial.print("Fuel Liters: ");
    Serial.print(lastDisplayedFuelLiters);
    Serial.println(" L");
    Serial.print("Instant Economy: ");
    Serial.print(instantFuelEconomy);
    Serial.println(" km/L");
    
    Serial.println("\n--- Fuel Fill Data ---");
    Serial.print("Fuel Fill Average: ");
    Serial.print(fuelFillAverage);
    Serial.println(" km/L");
    Serial.print("Fuel Fill Distance: ");
    Serial.print(fuelFillDistance);
    Serial.println(" km");
    Serial.print("Last Fuel Fill: ");
    Serial.print(lastFuelFillLiters);
    Serial.println(" L");
    Serial.print("Fuel Fill Started: ");
    Serial.println(fuelFillStarted ? "Yes" : "No");
    Serial.print("Fuel Used Since Fill: ");
    Serial.print(lastFuelFillLiters - lastDisplayedFuelLiters);
    Serial.println(" L");
    
    Serial.println("\n--- System Data ---");
    Serial.print("Reset Count: ");
    Serial.println(resetCount);
    Serial.print("LCD Auto Restart: ");
    Serial.println(autoRestartEnabled ? "Enabled" : "Disabled");
    
    Serial.println("\n--- Navigation Data ---");
    Serial.print("Has Navigation: ");
    Serial.println(hasNavigation ? "Yes" : "No");
    if (hasNavigation) {
      Serial.print("Navigation Direction: ");
      Serial.println(getDirectionName(currentNavigation.direction));
      Serial.print("Navigation Distance: ");
      Serial.print(currentNavigation.distance);
      Serial.println(" m");
      Serial.print("Navigation Street: ");
      Serial.println(currentNavigation.streetName);
      Serial.print("Navigation Instruction: ");
      Serial.println(currentNavigation.instruction);
      Serial.print("Navigation Step: ");
      Serial.print(currentNavigation.currentStep + 1);
      Serial.print("/");
      Serial.println(currentNavigation.totalSteps);
      Serial.print("Navigation Speed: ");
      Serial.print(currentNavigation.speed);
      Serial.println(" km/h");
    }
    
    Serial.println("================================\n");
    
    // Create JSON response with increased buffer for more data
    StaticJsonDocument<1024> doc;
    
    // Ensure speed is properly formatted and not zero
    float speedToSend = (currentSpeed > 0) ? currentSpeed : 0;
    doc["speed"] = speedToSend;
    doc["fuelPercentage"] = (lastDisplayedFuelLiters / maxFuelLiters) * 100.0;
    doc["fuelLiters"] = lastDisplayedFuelLiters;
    doc["instantEconomy"] = instantFuelEconomy;
    doc["odometer"] = totalDistanceKm;  // Changed from totalDistance to odometer
    doc["trip1"] = trip1DistanceKm;     // Changed from trip1Distance to trip1
    doc["trip1Fuel"] = trip1FuelUsed;
    doc["trip1Average"] = trip1FuelAverage;
    doc["trip1Started"] = trip1Started;
    doc["trip2"] = trip2DistanceKm;     // Changed from trip2Distance to trip2
    doc["trip2Fuel"] = trip2FuelUsed;
    doc["trip2Average"] = trip2FuelAverage;
    doc["trip2Started"] = trip2Started;
    doc["fuelFillAverage"] = fuelFillAverage;
    doc["fuelFillDistance"] = fuelFillDistance;
    doc["lastFuelFill"] = lastFuelFillLiters;
    doc["fuelFillStarted"] = fuelFillStarted;
    doc["fuelUsedSinceFill"] = lastFuelFillLiters - lastDisplayedFuelLiters;
    doc["resetCount"] = resetCount;
    doc["has_navigation"] = hasNavigation;
    doc["lcd_auto_restart"] = autoRestartEnabled;
    
    if (hasNavigation) {
      doc["nav_direction"] = String(currentNavigation.direction);
      doc["nav_distance"] = currentNavigation.distance;
      doc["nav_street"] = currentNavigation.streetName;
      doc["nav_instruction"] = currentNavigation.instruction;
      doc["nav_step"] = currentNavigation.currentStep;
      doc["nav_total_steps"] = currentNavigation.totalSteps;
      doc["nav_speed"] = currentNavigation.speed;
    }
    
    String jsonString;
    serializeJson(doc, jsonString);
    
    // Debug output after creating JSON
    Serial.println("Dashboard data JSON:");
    Serial.println(jsonString);
    
    server.send(200, "application/json", jsonString);
  });

  // Add status endpoint
  server.on("/status", [&]() {
    String status = "{\"connected_clients\": " + String(WiFi.softAPgetStationNum()) + "}";
    server.send(200, "application/json", status);
  });

  // Add navigation endpoints
  server.on("/navigation", HTTP_POST, []() {
    if (!server.hasArg("plain")) {
        server.send(400, "text/plain", "No data received");
        return;
    }

    String jsonData = server.arg("plain");
    Serial.println("\n=== Navigation Data Received ===");
    Serial.println(jsonData);
    Serial.println("============================");

    StaticJsonDocument<1024> doc;
    DeserializationError error = deserializeJson(doc, jsonData);

    if (error) {
        Serial.print("JSON parsing failed: ");
        Serial.println(error.c_str());
        server.send(400, "text/plain", "Invalid JSON data");
        return;
    }

    // Update navigation data
    hasNavigation = true;
    currentNavigation.isNavigating = true;
    currentNavigation.currentStep = doc["current_step"] | 0;
    currentNavigation.totalSteps = doc["total_steps"] | 0;
    
    // Get speed from navigation
    currentNavigation.speed = doc["speed"] | 0.0;
    // Update current speed if navigation is active and speed is valid
    if (currentNavigation.speed > 0) {
        currentSpeed = currentNavigation.speed;
        Serial.println("Navigation speed received: " + String(currentNavigation.speed) + " km/h");
        Serial.println("Current speed updated to: " + String(currentSpeed) + " km/h");
    } else {
        Serial.println("No valid navigation speed received");
    }
    
    // Get the maneuver type and convert to direction
    const char* maneuver = doc["maneuver"] | "straight";
    if (strcmp(maneuver, "turn-right") == 0) {
        currentNavigation.direction = 'R';
    } else if (strcmp(maneuver, "turn-left") == 0) {
        currentNavigation.direction = 'L';
    } else if (strcmp(maneuver, "uturn-right") == 0 || strcmp(maneuver, "uturn-left") == 0) {
        currentNavigation.direction = 'T';
    } else if (strcmp(maneuver, "roundabout-right") == 0 || strcmp(maneuver, "roundabout-left") == 0) {
        currentNavigation.direction = 'A';
    } else if (strcmp(maneuver, "straight") == 0) {
        currentNavigation.direction = 'U';
    } else {
        currentNavigation.direction = 'U'; // Default to straight/up
    }

    // Get distance to next turn
    const char* distanceStr = doc["distance_to_next"] | "0 m";
    float distance = 0;
    if (strstr(distanceStr, "km") != NULL) {
        distance = atof(distanceStr) * 1000; // Convert km to m
    } else {
        distance = atof(distanceStr); // Already in meters
    }
    currentNavigation.distance = distance;

    // Get street name and instruction
    const char* streetName = doc["next_instruction"] | "";
    strncpy(currentNavigation.streetName, streetName, sizeof(currentNavigation.streetName) - 1);
    currentNavigation.streetName[sizeof(currentNavigation.streetName) - 1] = '\0';

    // Clean HTML tags from instruction text
    String cleanedInstruction = cleanHtmlTags(String(currentNavigation.streetName));
    strncpy(currentNavigation.streetName, cleanedInstruction.c_str(), sizeof(currentNavigation.streetName) - 1);
    currentNavigation.streetName[sizeof(currentNavigation.streetName) - 1] = '\0';

    // Update navigation display time
    navScreenStartTime = millis();
    
    // Switch to navigation screen
    currentScreen = 4;
    screenChanged = true;

    // Debug output
    Serial.println("\n=== Navigation Updated ===");
    Serial.print("Direction: ");
    Serial.println(getDirectionName(currentNavigation.direction));
    Serial.print("Distance: ");
    Serial.print(currentNavigation.distance);
    Serial.println(" meters");
    Serial.print("Street: ");
    Serial.println(currentNavigation.streetName);
    Serial.print("Step: ");
    Serial.print(currentNavigation.currentStep + 1);
    Serial.print(" of ");
    Serial.println(currentNavigation.totalSteps);
    Serial.print("Speed: ");
    Serial.print(currentNavigation.speed);
    Serial.println(" km/h");
    Serial.println("========================");

    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Navigation updated\"}");
  });
  
  server.on("/navigation/clear", HTTP_POST, []() {
    Serial.println("\n=== Navigation Cleared ===");
    Serial.println("Navigation system deactivated");
    if (currentScreen == 4) {
      Serial.println("Switching back to speed screen");
    }
    Serial.println("========================");
    
    hasNavigation = false;
    currentNavigation.isNavigating = false;
    memset(&currentNavigation, 0, sizeof(NavigationData));
    
    // If currently on navigation screen, switch to speed screen
    if (currentScreen == 4) {
      currentScreen = 0;
      screenChanged = true;
    }
    
    server.send(200, "application/json", "{\"status\":\"success\",\"message\":\"Navigation cleared\"}");
  });

  // ... rest of the existing setup code ...

  server.begin();
}

void loop() {
  // Feed watchdog timer
  ESP.wdtFeed();
  
  // Log connected clients every 5 seconds
  static unsigned long lastClientCheck = 0;
  if (millis() - lastClientCheck >= 5000) {
    lastClientCheck = millis();
    int numClients = WiFi.softAPgetStationNum();
    if (numClients > 0) {
        Serial.println("ESP is connected to Activa Dashboard app");
    } else {
        Serial.println("ESP is not connected to Activa Dashboard app");
    }
  }
  
  // Check free heap and restart if too low
  if (ESP.getFreeHeap() < 4000) { // 4KB minimum
    ESP.restart();
    return;
  }

  // Update power consumption
  if (millis() - lastPowerUpdate >= POWER_UPDATE_INTERVAL) {
    lastPowerUpdate = millis();
    calculatePowerConsumption();
  }

  // Update LCD backlight based on time
  if (millis() - lastBacklightUpdate >= BACKLIGHT_UPDATE_INTERVAL) {
    lastBacklightUpdate = millis();
    updateLCDBacklight();
  }

  dnsServer.processNextRequest();
  MDNS.update();

  // Remove state transition logic
  // if (currentState == INITIAL_ON && (currentTime - stateStartTime >= INITIAL_ON_DURATION)) {
  //   currentState = OFF_PERIOD;
  //   stateStartTime = currentTime;
  //   WiFi.mode(WIFI_OFF);  // Turn off WiFi
  //   server.close();  // Stop the server to save power
  //   Serial.println("WiFi turned OFF for 1 minute");
  // } else if (currentState == OFF_PERIOD && (currentTime - stateStartTime >= OFF_PERIOD_DURATION)) {
  //   currentState = ON_UPDATE;
  //   stateStartTime = currentTime;
  //   WiFi.mode(WIFI_AP);  // Turn on WiFi
  //   WiFi.softAP(ssid, password, 1, false, 4);
  //   server.begin();  // Restart server for data updates
  //   Serial.println("WiFi turned ON for 1 minute to update data");
  // } else if (currentState == ON_UPDATE && (currentTime - stateStartTime >= ON_UPDATE_DURATION)) {
  //   currentState = OFF_PERIOD;
  //   stateStartTime = currentTime;
  //   WiFi.mode(WIFI_OFF);  // Turn off WiFi
  //   server.close();
  //   Serial.println("WiFi turned OFF for 1 minute");
  // }

  server.handleClient();
  
  // Update speed and distance calculations more frequently
  static unsigned long lastSpeedUpdate = 0;
  if (millis() - lastSpeedUpdate >= SPEED_UPDATE_INTERVAL) {
    lastSpeedUpdate = millis();
    Serial.println("\n=== PERFORMING SPEED & DISTANCE CALCULATION ===");
    Serial.print("Current Time: ");
    Serial.println(millis());
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
    if (now - lastFuelReading >= FUEL_READING_INTERVAL) {
      lastFuelReading = now;
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
    if (now - lastEEPROMWrite >= EEPROM_SAVE_INTERVAL) {
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
        if (now - lastTripSave >= 1000) {  // Save trip data every second when active
            saveAllDataToEEPROM();
            lastTripSave = now;
        }
    }
  }

  // Check for update errors
  checkUpdateStatus();

  // Add periodic data printing
  static unsigned long lastDataPrint = 0;
  if (now - lastDataPrint >= 5000) { // Print every 5 seconds
    lastDataPrint = now;
    
    // Print current speed status
    Serial.println("\n=== CURRENT STATUS ===");
    Serial.print("Current Speed: ");
    Serial.print(currentSpeed);
    Serial.println(" km/h");
    Serial.print("Speed Source: ");
    if (hasNavigation && currentNavigation.speed > 0) {
      Serial.println("Navigation");
    } else if (gps.location.isValid() && gps.speed.isValid()) {
      Serial.println("GPS");
    } else {
      Serial.println("None");
    }
    Serial.print("Trip 1 Started: ");
    Serial.println(trip1Started ? "Yes" : "No");
    Serial.print("Trip 2 Started: ");
    Serial.println(trip2Started ? "Yes" : "No");
    Serial.print("Auto Restart: ");
    Serial.println(autoRestartEnabled ? "Enabled" : "Disabled");
    Serial.println("===================");
    
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
  if (now - lastFuelCheck >= 1000) {  // Check every second
      lastFuelCheck = now;
      checkFuelFill();
  }

  // Update display with current values
  static unsigned long lastDisplayUpdate = 0;
  if (now - lastDisplayUpdate >= 200) {  // Update display every 200ms
    lastDisplayUpdate = now;
    updateDisplay(currentSpeed, powerConsumption, currentConsumption, 
                  totalDistanceKm, trip1DistanceKm, trip1FuelAverage,
                  trip2DistanceKm, trip2FuelAverage);
  }

  // Check for auto-restart every 5 minutes if enabled
  if (autoRestartEnabled && (now - lastLCDRestartTime >= LCD_RESTART_INTERVAL)) {
    restartLCD();
    lastLCDRestartTime = now;  // Reset timer
  }
}

// Stub functions for undefined ones
void handleRoot() {
  server.send(200, "text/plain", "Root page");
}

void handleGpsSender() {
  server.send(200, "text/plain", "GPS Sender page");
}

void handleDashboard() {
  server.send(200, "text/plain", "Dashboard page");
}

void handleData() {
  server.send(200, "text/plain", "Data page");
}

// Helper function to clean HTML tags from text
String cleanHtmlTags(String text) {
  String cleaned = text;
  // Remove common HTML tags
  cleaned.replace("<b>", "");
  cleaned.replace("</b>", "");
  cleaned.replace("<i>", "");
  cleaned.replace("</i>", "");
  cleaned.replace("<strong>", "");
  cleaned.replace("</strong>", "");
  cleaned.replace("<em>", "");
  cleaned.replace("</em>", "");
  cleaned.replace("&nbsp;", " ");
  cleaned.replace("&amp;", "&");
  cleaned.replace("&lt;", "<");
  cleaned.replace("&gt;", ">");
  return cleaned;
}

// Function to get direction name