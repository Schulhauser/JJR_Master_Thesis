// cpu freqz.: 80 MHz

//#include <PowerFeather.h>
#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <U8g2lib.h>
#include <SPI.h>
#include "esp_pm.h"
#include "soc/rtc.h" 
#include <WiFi.h>
#include "esp_wifi.h"

#define OLED_DC 10
#define OLED_CS 6
#define OLED_RST 9

U8G2_SSD1309_128X64_NONAME2_1_4W_HW_SPI u8g2(U8G2_R0, OLED_CS, OLED_DC, OLED_RST);

// BLE setup
BLEServer* pServer = nullptr;
BLECharacteristic* pCharacteristic = nullptr;
BLEAdvertising* pAdvertising = nullptr;
bool deviceConnected = false;
bool hasSpeedData = false;
String speedValue = "";

// Callbacks for BLE connection/disconnection
class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        deviceConnected = true;
        hasSpeedData = false;
    }
    
    void onDisconnect(BLEServer* pServer) override {
        deviceConnected = false;
        hasSpeedData = false;
        delay(100);
        pAdvertising->start();
    }
};

class MyCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
       // String value = pCharacteristic->getValue();
        String value = String(pCharacteristic->getValue().c_str());

        
        if (value.length() > 0) {
            speedValue = value;
            hasSpeedData = true;
        }
    }
};

void disableWiFi() {
    WiFi.disconnect(true);      
    WiFi.mode(WIFI_OFF);        
    esp_wifi_stop();             
    esp_wifi_deinit();           
}

void setup() {
    u8g2.begin();
    u8g2.enableUTF8Print();
    //pf_initPowerSystem();
    
    // Freq. check
    setCpuFrequencyMhz(80);  // 40, 80, 160, o 240 
    Serial.begin(115200);
    Serial.print("Frecuenciy: ");
    Serial.print(getCpuFrequencyMhz());
    Serial.println(" MHz");

    // BLE initialization
    BLEDevice::init("ESP32_LED_Server");
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    BLEService* pService = pServer->createService("12345678-1234-1234-1234-1234567890ab");
    pCharacteristic = pService->createCharacteristic(
        "abcdefab-1234-5678-1234-abcdefabcdef",
        BLECharacteristic::PROPERTY_WRITE
    );
    pCharacteristic->setCallbacks(new MyCallbacks());
    pService->start();
    pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(pService->getUUID());
    pAdvertising->start();

    disableWiFi(); 

}

void displayCenteredText(const String& text, uint8_t y, const uint8_t* font = u8g2_font_ncenB24_tr) {
    u8g2.setFont(font);
    int strWidth = u8g2.getUTF8Width(text.c_str());
    int xPos = (128 - strWidth) / 2;
    u8g2.drawUTF8(xPos, y, text.c_str());
}

void loop() {
    u8g2.firstPage();
    do {
        if (!deviceConnected) {
            displayCenteredText("Disc.", 40, u8g2_font_ncenB24_tr);
        } 
        else if (!hasSpeedData) {
            displayCenteredText("Conn.", 40, u8g2_font_ncenB24_tr);
        } 
        else {

            u8g2.setFont(u8g2_font_ncenB24_tr);
            
            // Center numerical value
            int speedWidth = u8g2.getUTF8Width(speedValue.c_str());
            int startX = (128 - speedWidth) / 2;
            
            
            u8g2.drawUTF8(startX, 40, speedValue.c_str());
            
            // Write "km/h" small next to number
            u8g2.setFont(u8g2_font_ncenB08_tr);
            u8g2.drawUTF8(startX + speedWidth + 5, 40 - 10, "km/h"); // 5px separation
        }
    } while (u8g2.nextPage());
    
    delay(100);
}
/*
bool pf_initPowerSystem() {
    if (PowerFeather::Board.init() != PowerFeather::Result::Ok) {
        return false;
    }
    if (PowerFeather::Board.enable3V3(true) != PowerFeather::Result::Ok) {
        return false;
    }
    if (PowerFeather::Board.enableBatteryCharging(false) != PowerFeather::Result::Ok) {
        // Silent fail for charging disable
    }
    delay(1000); // NECESSARY FOR 3V3 RAIL
    return true;
}
*/