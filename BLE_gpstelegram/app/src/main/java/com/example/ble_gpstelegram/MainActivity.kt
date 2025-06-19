package com.example.ble_gpstelegram

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    private lateinit var speedText: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    // private val SCAN_PERIOD: Long = 10000
    private val SCAN_PERIOD: Long = 5000
    private var gatt: BluetoothGatt? = null
    private var speedExceededNotified = false
    private var lastValidSpeed = 0f

    //  Accelerometer (impact)
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val shakeThreshold = 25f
    private var lastShakeTime: Long = 0
    private val shakeCooldown = 5000L

    // BLE Configuration
    private val deviceName = "ESP32_LED_Server"
    private val serviceUUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val characteristicUUID = UUID.fromString("abcdefab-1234-5678-1234-abcdefabcdef")

    // Telegram Configuration
    private val telegramBotToken = "8126940006:AAEZTQEJHHsgZUJdz9nmn4x6xju5Na9YkYM"
    private val telegramChatId = "8030955888"
    private val telegramApiUrl = "https://api.telegram.org/bot$telegramBotToken/sendMessage"
    private val okHttpClient = OkHttpClient()

    companion object {
        private const val REQUEST_BLE_SCAN_PERMISSION = 1001
        private const val REQUEST_BLE_CONNECT_PERMISSION = 1002
        private const val MIN_GPS_SPEED = 0.3f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        speedText = findViewById(R.id.speedText)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        checkPermissions()
        scanLeDevice()
        startLocationUpdates()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.also {
            sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

    }
    // ==================== Accel ====================

    private val accelListener = object : SensorEventListener {
        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            Log.d("ACCEL", "Aceleración: $acceleration")

            if (acceleration > shakeThreshold) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShakeTime > shakeCooldown) {
                    lastShakeTime = currentTime
                    triggerImpactAlert()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun triggerImpactAlert() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                sendTelegramAlert(it)
            }
        }
    }



    // ==================== GPS ====================
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 1.0f
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (location.hasSpeed()) {
                        val speedMps = location.speed
                        val filteredSpeed = filterGpsSpeed(speedMps)
                        val speedKmh = filteredSpeed * 3.6f

                        updateUI(speedKmh)
                        sendDataOverBLE("%.1f".format(speedKmh))
                        // checkSpeedAlert(speedKmh, location)
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun filterGpsSpeed(rawSpeed: Float): Float {
        return when {
            rawSpeed < 0 -> 0f
            rawSpeed < MIN_GPS_SPEED -> 0f
            else -> {
                lastValidSpeed = rawSpeed
                rawSpeed
            }
        }
    }

    private fun updateUI(speedKmh: Float) {
        runOnUiThread {
            speedText.text = "Velocidad: %.1f km/h".format(speedKmh)
        }
    }
    /*
        private fun checkSpeedAlert(speedKmh: Float, location: Location) {
            if (speedKmh > 1.0f) {
                if (!speedExceededNotified) {
                    speedExceededNotified = true
                    sendTelegramAlert("%.1f".format(speedKmh), location)
                }
            } else {
                speedExceededNotified = false
            }
        }
    */
    // ==================== Telegram ====================
    private fun sendTelegramAlert(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        val googleMapsLink = "https://www.google.com/maps?q=$lat,$lon"
        val accuracy = "%.1f".format(location.accuracy) + "m"
        val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        val message = """
        🚨 *ALERTA DE IMPACTO DETECTADO* 🚨
        
        📍 *Ubicación*:
        - Latitud: `$lat`
        - Longitud: `$lon`
        - Precisión: `$accuracy`
        - [📌 Ver en Google Maps]($googleMapsLink)
        
        ⏰ *Hora*: ${timeFormat.format(Date())}
    """.trimIndent()

        val mediaType = "application/json".toMediaType()
        val requestBody = """
        {
            "chat_id": "$telegramChatId",
            "text": ${JSONObject.quote(message)},
            "parse_mode": "Markdown",
            "disable_web_page_preview": false
        }
    """.trimIndent().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(telegramApiUrl)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Telegram", "Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("Telegram", "Error: ${response.code} - ${response.body?.string()}")
                }
            }
        })
    }



    // ==================== BLE ====================
    private fun checkPermissions() {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val missing = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        }
    }

    private fun scanLeDevice() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("BLE_SCAN", "BLUETOOTH_SCAN permission not granted. Requesting...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                REQUEST_BLE_SCAN_PERMISSION
            )
            return
        }

        if (bleScanner == null) {
            Log.e("BLE_SCAN", "BluetoothLeScanner not initialized. Attempting to reinitialize.")

            bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            bleScanner = bluetoothAdapter?.bluetoothLeScanner
            if (bleScanner == null) {
                Log.e("BLE_SCAN", "Failed to reinitialize BluetoothLeScanner. Cannot scan.")
                return
            }
        }

        // If already connected, don't start a new scan.
        if (this@MainActivity.gatt != null) {
            Log.i("BLE_SCAN", "Already connected or gatt instance exists. Skipping new scan.")
            return;
        }


        if (!scanning) {
            Log.i("BLE_SCAN", "Starting BLE scan for device name: $deviceName")
            // Stops scanning after a pre-defined scan period.
            // Remove any pending stop scan callbacks to prevent premature stop if scanLeDevice is called again.
            handler.removeCallbacksAndMessages(null) // Be careful if handler is used for other things.
            // Or use a specific Runnable object for stopping:
            // handler.removeCallbacks(stopScanRunnable)

            handler.postDelayed({
                if (scanning) { // Check if still scanning
                    Log.i("BLE_SCAN", "Scan period elapsed. Stopping scan.")
                    scanning = false
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bleScanner?.stopScan(leScanCallback)
                    } else {
                        Log.e("BLE_SCAN", "Cannot stop scan, BLUETOOTH_SCAN permission missing.")
                    }
                    if (this@MainActivity.gatt == null) {
                        Log.w("BLE_SCAN", "Device not found/connected after scan period.")
                        // Optionally, try scanning again after a longer delay or update UI
                        // handler.postDelayed({ scanLeDevice() }, 10000) // retry scan after 10s
                    }
                }
            }, SCAN_PERIOD /*, stopScanRunnable */)

            scanning = true
            val scanFilters: MutableList<ScanFilter> = ArrayList()
            val scanFilter = ScanFilter.Builder()
                .setDeviceName(deviceName)
                .build()
            scanFilters.add(scanFilter)

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use low latency for faster discovery
                .build()

            bleScanner?.startScan(scanFilters, scanSettings, leScanCallback)
        } else {
            Log.i("BLE_SCAN", "Scan already in progress.")
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            if (result.device.name == deviceName) {
                Log.i("BLE_SCAN_RESULT", "Target device found: ${result.device.name} (${result.device.address})")

                if (scanning) { // If we are actively scanning
                    scanning = false // We found our device, no need to keep the general scanning flag true
                    // The SCAN_PERIOD timeout will also try to stop it, but better to stop earlier.
                    bleScanner?.stopScan(this) // Stop further scan callbacks
                    Log.i("BLE_SCAN_RESULT", "Scan stopped as target device was found.")
                }

                // Check if we are already connected or attempting to connect.
                if (this@MainActivity.gatt == null) {
                    Log.i("BLE_CONNECT", "Attempting to connect to ${result.device.address}")
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        // false for autoConnect parameter: establish a direct connection or throw an error if the device isn't available
                        result.device.connectGatt(this@MainActivity, false, gattCallback)
                    } else {
                        Log.e("BLE_CONNECT", "BLUETOOTH_CONNECT permission not granted when device found.")
                        // Request permission if necessary, or guide user.
                    }
                } else {
                    Log.i("BLE_CONNECT", "Already have a GATT instance or connected to ${this@MainActivity.gatt?.device?.address}. Ignoring new scan result for ${result.device.address}")
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Log.d("BLE_SCAN_RESULT", "Batch scan results received: ${results?.size ?: 0}")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BLE_SCAN_RESULT", "BLE Scan Failed. Error Code: $errorCode")
            scanning = false // Reset scanning flag
            // Implement retry logic or user notification as needed
            // handler.postDelayed({ scanLeDevice() }, 5000) // Retry scan after 5 seconds
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address // Good for logging

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE_GATT", "Connected to GATT server at $deviceAddress")
                this@MainActivity.gatt = gatt // Store the active GATT instance
                // Ensure permission before discovering services
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.i("BLE_GATT", "Attempting to discover services...")
                    val success = gatt.discoverServices()
                    if (!success) {
                        Log.e("BLE_GATT", "discoverServices() failed to start for $deviceAddress")
                    }
                } else {
                    Log.e("BLE_GATT", "BLUETOOTH_CONNECT permission not granted for discoverServices on $deviceAddress")
                    // Handle missing permission, perhaps by closing GATT and notifying the user or re-requesting.
                }

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLE_GATT", "Disconnected from GATT server at $deviceAddress. Status: $status")
                // Clean up the old connection
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    gatt.close()
                }
                this@MainActivity.gatt = null // Nullify the GATT instance

                // --- RECONNECTION STRATEGY ---
                Log.d("BLE_GATT", "ESP32 disconnected. Attempting to rescan and reconnect...")
                // Ensure scanning is done on the main thread if it updates UI or interacts with certain system services
                runOnUiThread {
                    handler.postDelayed({
                        if (this@MainActivity.gatt == null) { // Only scan if not already reconnected by another means
                            Log.i("BLE_RECONNECT", "Starting scan to find ESP32 again.")
                            scanLeDevice()
                        }
                    }, 500) // 3-second delay before rescanning
                }
            } else {
                Log.w("BLE_GATT", "Connection state changed to $newState for $deviceAddress with status $status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE_GATT", "Services discovered for ${gatt.device.address}.")

                val service = gatt.getService(serviceUUID)
                if (service == null) {
                    Log.e("BLE_GATT", "Service $serviceUUID not found on ${gatt.device.address}.")
                } else {
                    val characteristic = service.getCharacteristic(characteristicUUID)
                    if (characteristic == null) {
                        Log.e("BLE_GATT", "Characteristic $characteristicUUID not found in service ${service.uuid} on ${gatt.device.address}.")
                    } else {
                        Log.i("BLE_GATT", "Service and Characteristic found on ${gatt.device.address}. Ready for communication.")
                    }
                }
            } else {
                Log.w("BLE_GATT", "onServicesDiscovered received error status $status for ${gatt.device.address}")
            }
        }

        @Deprecated("Used natively in Android 12 and lower")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE_WRITE", "Characteristic ${characteristic?.uuid} written successfully. Value: ${characteristic?.value?.toString(Charsets.UTF_8)}")
            } else {
                Log.e("BLE_WRITE", "Characteristic ${characteristic?.uuid} write failed. Status: $status")
            }
        }
        // If targeting Android 13+ and using specific write types, might need to implement
        // onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int, offset: Int, value: ByteArray)
    }
    private fun sendDataOverBLE(data: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BLE", "Permiso BLUETOOTH_CONNECT no concedido. No se puede enviar.")
            return
        }

        val service = gatt?.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)
        characteristic?.let {
            it.value = data.toByteArray()
            gatt?.writeCharacteristic(it)
            Log.d("BLE", "Velocidad enviada: $data km/h")
        } ?: Log.e("BLE", "Servicio o característica no encontrados.")
    }
}