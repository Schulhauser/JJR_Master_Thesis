#include <HardwareSerial.h>
#include <TinyGPS++.h>
#include <U8g2lib.h>
#include <SPI.h>

#include "esp_bt.h"          // Necesario para controlar Bluetooth
#include "esp_bt_main.h"     // Necesario para la pila Bluetooth
#include "soc/rtc.h" 
#include <WiFi.h>
#include "esp_wifi.h"

// GPS Configuration
#define GPS_RX_PIN 42  // Adjust these pins according to your connection
#define GPS_TX_PIN 44
HardwareSerial gpsSerial(1);  // Using Serial1 for GPS

// TinyGPS++ object
TinyGPSPlus gps;

// OLED Configuration
#define OLED_DC 10
#define OLED_CS 6
#define OLED_RST 9
U8G2_SSD1309_128X64_NONAME2_1_4W_HW_SPI u8g2(U8G2_R0, OLED_CS, OLED_DC, OLED_RST);

// Control variables
unsigned long lastDataTime = 0;
const unsigned long gpsTimeout = 5000;  // 5 seconds without data

void disableWireless() {
    // Apagar WiFi
    WiFi.disconnect(true);       // Desconecta y elimina credenciales
    WiFi.mode(WIFI_OFF);         // Apaga el WiFi
    esp_wifi_stop();             // Detiene la pila WiFi
    esp_wifi_deinit();           // Libera recursos de WiFi

    // Apagar Bluetooth
    esp_bluedroid_disable();     // Desactiva Bluedroid (parte de la pila BT)
    esp_bluedroid_deinit();      // Libera recursos de Bluedroid
    esp_bt_controller_disable(); // Desactiva el controlador BT
    esp_bt_controller_deinit();  // Libera recursos del controlador BT
}


void setup() {
  // Initialize serial communication with computer
  Serial.begin(115200);
  
 // disableWireless();

  // Freq. check
  setCpuFrequencyMhz(40);  // 40, 80, 160, o 240 
  Serial.print("Frecuenciy: ");
  Serial.print(getCpuFrequencyMhz());
  Serial.println(" MHz");

  // Initialize GPS communication
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);
  
  // Initialize OLED display
  u8g2.begin();
  u8g2.setFontPosTop();
  
  // Show initial message on OLED
  displayOLED("Search.");
}

void loop() {
  // Read GPS data
  while (gpsSerial.available() > 0) {
    char c = gpsSerial.read();
    if (gps.encode(c)) {
      lastDataTime = millis();
      processGPSData();
      
      displayGPSData();
    }
  }

  // Check for timeout
  if (millis() - lastDataTime > gpsTimeout) {
    displayOLED("Search.");  // Show searching message
    }

    delay(500);  // Small delay for stability
  }

  void processGPSData() {
  if (gps.speed.isValid()) {
    // If we have valid speed data, display it without decimals
    int speedKmh = (int)gps.speed.kmph();  // Convert to integer to remove decimals
    
    char displayStr[20];
    sprintf(displayStr, "%d km/h", speedKmh);
    
    displayOLED(displayStr);
    } else {
    // If we have GPS connection but no speed yet
    displayOLED("Search.");
  }
  }

  void displayOLED(const char* message) {
  u8g2.firstPage();   
  do {
    u8g2.clearBuffer();
    
    // Set large font for the main message
    //u8g2.setFont(u8g2_font_t0_17b_tr);
    u8g2.setFont(u8g2_font_ncenB24_tr);

    u8g2.drawStr(0, 20, message);  // Centered vertically

    u8g2.sendBuffer();
  } while (u8g2.nextPage());
  }


  void displayGPSData() {
  Serial.print("Fecha: ");
  if (gps.date.isValid()) {
    Serial.print(gps.date.day());
    Serial.print("/");
    Serial.print(gps.date.month());
    Serial.print("/");
    Serial.print(gps.date.year());
    } else {
    Serial.print("INVALIDA");
  }

  Serial.print("  Hora: ");
  if (gps.time.isValid()) {
    if (gps.time.hour() < 10) Serial.print("0");
    Serial.print(gps.time.hour());
    Serial.print(":");
    if (gps.time.minute() < 10) Serial.print("0");
    Serial.print(gps.time.minute());
    Serial.print(":");
    if (gps.time.second() < 10) Serial.print("0");
    Serial.print(gps.time.second());
    } else {
    Serial.print("INVALIDA");
  }

  Serial.print("  Satélites: ");
  if (gps.satellites.isValid()) {
    Serial.print(gps.satellites.value());
  } else {
    Serial.print("INVALIDO");
  }

  Serial.print("  Velocidad: ");
  if (gps.speed.isValid()) {
    Serial.print(gps.speed.kmph(), 1);  // km/h 1 decimal
    Serial.print(" km/h");
  } else {
    Serial.print("0.0 km/h (sin dato)");
  }

  Serial.print("  Posición: ");
  if (gps.location.isValid()) {
    Serial.print(gps.location.lat(), 6);
    Serial.print(", ");
    Serial.print(gps.location.lng(), 6);
  } else {
    Serial.print("INVALIDA");
  }
  
  Serial.print("  Frecuenciy: ");
  Serial.print(getCpuFrequencyMhz());
  Serial.print(" MHz");

  Serial.println();
}  