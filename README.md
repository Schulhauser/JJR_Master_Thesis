
This repository contains the source code developed for the Master's Thesis project. It includes three main components:

- **BLE_gpstelegram/**  
  This folder contains the Android smartphone application. It is responsible for collecting sensor data (GPS, accelerometer) and transmitting it via BLE to the ESP32. It also includes Telegram alert integration.

- **GPS_2/**  
  This folder contains the ESP32 code for the **wired version** of the system. It communicates directly with a GPS module and displays data on the Head-Up Display (HUD).

- **Opt_1/**  
  This folder contains the ESP32 code for the **wireless version** using BLE. In this configuration, the ESP32 receives GPS and sensor data from the smartphone via BLE and displays it on the HUD.
