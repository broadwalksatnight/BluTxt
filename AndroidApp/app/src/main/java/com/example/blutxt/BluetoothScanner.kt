package com.example.blutxt

import android.Manifest
import androidx.compose.ui.graphics.vector.ImageVector
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Chat



// ----------------------------------------------------------------------------------
// 1. UUID & CONSTANTS
// ----------------------------------------------------------------------------------

object BluetoothConstants {

    // NOTE: CBUUID(string: "2222") in iOS maps to the standard base UUID with 2222 inserted.
    // The full 128-bit UUID is required for Android
    val BLUTXT_SERVICE_UUID: UUID = UUID.fromString("00002222-0000-1000-8000-00805f9b34fb")

    val TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("F9D1737F-65F8-5FE9-8025-0AD67E260AAF") // Notify (Peripheral -> Central)
    val RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("F9D1737F-65F8-5FE9-8025-0AD67E260AAD") // Write (Central -> Peripheral)
    val TERMINATE_CHARACTERISTIC_UUID: UUID = UUID.fromString("F9D1737F-65F8-5FE9-8025-0AD67E260ABA") // Notify

    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

// ----------------------------------------------------------------------------------
// 2. THEME DEFINITION
// ----------------------------------------------------------------------------------

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF64FFDA),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2E2E2E)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2196F3),
    secondary = Color(0xFF00BFA5),
    background = Color.White,
    surface = Color(0xFFF0F0F0),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE0E0E0)
)

@Composable
fun BluTxtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

// ----------------------------------------------------------------------------------
// 3. DATA CLASSES
// ----------------------------------------------------------------------------------

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val icon: ImageVector
)

data class ChatMessage(
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ConnectionState {
    IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, ERROR
}

// ----------------------------------------------------------------------------------
// 4. Device Identification Logic (BLE Advertisement Based)
// ----------------------------------------------------------------------------------



/**
 * Determines the device type using BLE advertisement information:
 *  - Uses the local name (if present)
 *  - Checks known service UUIDs
 *  - Optionally checks manufacturer data
 */
fun getDeviceNameSafe(context: Context, result: ScanResult): String {
    return try {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            result.device.name?.lowercase() ?: "Unknown"
        } else {
            "Permission Denied"
        }
    } catch (e: SecurityException) {
        "Permission Denied"
    }
}

fun getDeviceTypeSafe(context: Context, result: ScanResult): String {
    val name = getDeviceNameSafe(context, result)
    val uuids = try {
        result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }

    return when {
        name.contains("iphone") || name.contains("ipad") -> "Apple Device"
        name.contains("samsung") || name.contains("galaxy") -> "Samsung Device"
        name.contains("mac") || name.contains("surface") || name.contains("windows") -> "Computer Device"
        uuids.any { it.contains("2222", ignoreCase = true) } -> "BluTxt Device"
        else -> "Unknown"
    }
}

fun getDeviceIconSafe(context: Context, result: ScanResult): ImageVector {
    return when (getDeviceTypeSafe(context, result)) {
        "Apple Device" -> Icons.Filled.PhoneIphone
        "Samsung Device" -> Icons.Filled.PhoneAndroid
        "Computer Device" -> Icons.Filled.Laptop
        "BluTxt Device" -> Icons.Filled.Chat
        else -> Icons.Filled.Bluetooth
    }
}



// ----------------------------------------------------------------------------------
// 5. BleManager (Communication Logic)
// ----------------------------------------------------------------------------------

class BleManager(private val context: Context) {


    private val bluetoothManager: BluetoothManager? by lazy {
        ContextCompat.getSystemService(context, BluetoothManager::class.java)
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private val seenDevices = mutableSetOf<String>()


    var bluetoothGatt: BluetoothGatt? = null
    var rxCharacteristic: BluetoothGattCharacteristic? = null

    // 💥 ASYNCHRONOUS SEQUENCE TRACKER (NEW) 💥
    private var pendingNotificationCharacteristic: BluetoothGattCharacteristic? = null

    // 💥 MTU/REASSEMBLY STATE 💥
    // Holds partial data chunks, mirroring the iOS 'incomingDataBuffer' which is string-based.
    private var messageBuffer: String = ""
    private val MESSAGE_DELIMITER = "\n"


    var onDeviceFound: ((BleDevice) -> Unit)? = null
    var onConnectionStateChange: ((ConnectionState, String) -> Unit)? = null
    var onMessageReceived: ((ChatMessage) -> Unit)? = null

    private var isScanActive = false

    private val gattCallback = object : BluetoothGattCallback() {
        @Suppress("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = try { gatt.device.address } catch (e: SecurityException) { "Unknown" }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("BleManager", "Connected to GATT server at $deviceAddress. Requesting MTU...")
                    onConnectionStateChange?.invoke(ConnectionState.CONNECTING, "Connected. Negotiating MTU...")

                    // 💥 STEP 1: Request a high MTU to allow larger packets and reduce chunks 💥
                    // We will proceed to discoverServices() in onMtuChanged
                    if (!gatt.requestMtu(512)) {
                        // If MTU request fails at the API level, proceed immediately to discovery
                        Log.e("BleManager", "requestMtu failed immediately. Proceeding with discovery.")
                        try { gatt.discoverServices() } catch (e: SecurityException) { /* handled below */ }
                    }

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("BleManager", "Disconnected from GATT server at $deviceAddress.")

                    // 💡 CRITICAL FIX 1: Only call closeGatt() if it hasn't been closed by the TERMINATE characteristic
                    // If bluetoothGatt is null, it means onCharacteristicChanged already ran closeGatt().
                    if (bluetoothGatt != null) {
                        closeGatt()
                    }
                    onConnectionStateChange?.invoke(ConnectionState.DISCONNECTED, "Disconnected.")
                }
            } else {
                // This is the failure block (Status 19 hits here)

                // 💥 CRITICAL FIX 2: If the GATT object is null, the TERMINATE characteristic already handled it cleanly.
                if (bluetoothGatt == null) {
                    Log.w("BleManager", "Ignoring connection error status $status, GATT object is already cleared by characteristic.")
                    return // Stop execution, we've already cleanly disconnected
                }

                Log.e("BleManager", "Connection failed with status $status.")
                try { gatt.disconnect() } catch (e: SecurityException) { Log.e("BleManager", "Disconnect failed: ${e.message}") }
                closeGatt()
                onConnectionStateChange?.invoke(ConnectionState.ERROR, "Connection failed (Status $status).")
            }
        }

        // 💥 STEP 2: Handle MTU result and start service discovery 💥
        @Suppress("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BleManager", "MTU successfully negotiated to: $mtu. Starting service discovery.")
            } else {
                Log.w("BleManager", "MTU negotiation failed. Current MTU is $mtu. Starting service discovery.")
            }

            // Start discovery regardless of MTU success/failure
            try {
                gatt.discoverServices()
            } catch (e: SecurityException) {
                Log.e("BleManager", "SecurityException during discoverServices: ${e.message}")
                onConnectionStateChange?.invoke(ConnectionState.ERROR, "Permissions error during discovery.")
                closeGatt()
            }
        }

        @Suppress("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BluetoothConstants.BLUTXT_SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(BluetoothConstants.RX_CHARACTERISTIC_UUID)
                    val txCharacteristic = service.getCharacteristic(BluetoothConstants.TX_CHARACTERISTIC_UUID)
                    val terminateCharacteristic = service.getCharacteristic(BluetoothConstants.TERMINATE_CHARACTERISTIC_UUID)

                    if (rxCharacteristic != null && txCharacteristic != null && terminateCharacteristic != null) {

                        // 💥 ASYNCHRONOUS FIX: Start the notification sequence 💥
                        Log.i("BleManager", "Characteristics found. Starting notification sequence for TX.")

                        // Store the first characteristic to configure
                        pendingNotificationCharacteristic = txCharacteristic

                        // Start the notification/descriptor write process
                        setCharacteristicNotification(gatt, txCharacteristic, true)

                        // NOTE: The connection state will be set to CONNECTED in onDescriptorWrite when the sequence finishes.
                    } else {
                        Log.e("BleManager", "Missing required characteristics.")
                        onConnectionStateChange?.invoke(ConnectionState.ERROR, "Missing TX/RX characteristics.")
                        try { gatt.disconnect() } catch (e: SecurityException) { Log.e("BleManager", "Disconnect failed: ${e.message}") }
                    }
                } else {
                    Log.e("BleManager", "Service ${BluetoothConstants.BLUTXT_SERVICE_UUID} not found.")
                    onConnectionStateChange?.invoke(ConnectionState.ERROR, "Target service not found.")
                    try { gatt.disconnect() } catch (e: SecurityException) { Log.e("BleManager", "Disconnect failed: ${e.message}") }
                }
            } else {
                Log.e("BleManager", "Service discovery failed with status $status")
                onConnectionStateChange?.invoke(ConnectionState.ERROR, "Service discovery failed.")
            }
        }

        // 💥 NEW: Sequencer for Descriptor Writes 💥
        @Suppress("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val characteristic = descriptor.characteristic

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BleManager", "Descriptor write successful for characteristic: ${characteristic.uuid.toShortString()}")

                if (characteristic.uuid == BluetoothConstants.TX_CHARACTERISTIC_UUID) {
                    // TX is done. Now start the TERMINATE characteristic configuration.
                    Log.i("BleManager", "TX notification enabled. Starting TERMINATE notification sequence.")
                    val terminateCharacteristic = gatt.getService(BluetoothConstants.BLUTXT_SERVICE_UUID)
                        .getCharacteristic(BluetoothConstants.TERMINATE_CHARACTERISTIC_UUID)

                    pendingNotificationCharacteristic = terminateCharacteristic
                    setCharacteristicNotification(gatt, terminateCharacteristic, true)

                } else if (characteristic.uuid == BluetoothConstants.TERMINATE_CHARACTERISTIC_UUID) {
                    // TERMINATE is done. The sequence is complete.
                    Log.i("BleManager", "TERMINATE notification enabled. Connection complete.")
                    pendingNotificationCharacteristic = null
                    onConnectionStateChange?.invoke(ConnectionState.CONNECTED, "Connection established. Ready to chat.")
                }
            } else {
                Log.e("BleManager", "Descriptor write failed with status $status for ${characteristic.uuid.toShortString()}")
                pendingNotificationCharacteristic = null
                // Fail the connection if critical subscription fails
                onConnectionStateChange?.invoke(ConnectionState.ERROR, "Failed to subscribe to required characteristics.")
                closeGatt()
            }
        }

        // 💥 STEP 3: Implement newline-based Reassembly Logic 💥
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return

            when (characteristic.uuid) {
                BluetoothConstants.TX_CHARACTERISTIC_UUID -> {
                    val chunk = data.toString(StandardCharsets.UTF_8)

                    // 1. Append the new chunk to the existing buffer
                    messageBuffer += chunk

                    // 2. Split the buffer by the newline delimiter
                    val messages = messageBuffer.split(MESSAGE_DELIMITER)

                    // 3. Process all complete messages (all except the last element)
                    for (i in 0 until messages.size - 1) {
                        val completeMessage = messages[i].trim()
                        if (completeMessage.isNotEmpty()) {
                            onMessageReceived?.invoke(ChatMessage("Them", completeMessage))
                            Log.d("BleManager", "✅ Complete message received: $completeMessage")
                        }
                    }

                    // 4. Update the buffer with the last (potentially incomplete) element
                    val lastPart = messages.last()
                    if (messageBuffer.endsWith(MESSAGE_DELIMITER)) {
                        // If the full buffer ended with a delimiter, the last component is empty, so we clear the buffer.
                        messageBuffer = ""
                        Log.d("BleManager", "Buffer cleared.")
                    } else {
                        // Otherwise, the last part is the start of the next message.
                        messageBuffer = lastPart
                        Log.d("BleManager", "Partial message saved: ${messageBuffer.length} bytes.")
                    }
                }

                BluetoothConstants.TERMINATE_CHARACTERISTIC_UUID -> {
                    val receivedSignal = data.toString(StandardCharsets.UTF_8).trim()

                    // Log the receipt of the signal (for diagnostic confirmation)
                    Log.w("BleManager", "Received explicit termination signal: '$receivedSignal'. Initiating cleanup.")

                    // 1. Immediately update the state to DISCONNECTED (The Mac initiated it)
                    // This is the clean disconnect state that overrides the Status 19 error
                    onConnectionStateChange?.invoke(ConnectionState.DISCONNECTED, "Session terminated by other user.")

                    // 2. Perform the single, comprehensive cleanup
                    //    Crucially, this sets 'bluetoothGatt = null', which prevents the Status 19 error from running its logic.
                    closeGatt()
                }
            }
        }
    }

    fun sendDisconnectSignal() {
        val gatt = bluetoothGatt ?: return
        val terminateChar = gatt.getService(BluetoothConstants.BLUTXT_SERVICE_UUID)
            ?.getCharacteristic(BluetoothConstants.TERMINATE_CHARACTERISTIC_UUID)
            ?: return

        terminateChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        terminateChar.value = "DISCONNECT\n".toByteArray(Charsets.UTF_8)

        try {
            gatt.writeCharacteristic(terminateChar)
            Log.d("BleManager", "Disconnect signal sent")
        } catch (e: SecurityException) {
            Log.e("BleManager", "Failed to send disconnect signal: ${e.message}")
        }
    }





    @Suppress("MissingPermission")
    private fun setCharacteristicNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        try {
            gatt.setCharacteristicNotification(characteristic, enable)

            val descriptor = characteristic.getDescriptor(BluetoothConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                val value = if (enable) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                descriptor.value = value
                // 💥 CRITICAL: This is an asynchronous operation, the result is handled in onDescriptorWrite 💥
                gatt.writeDescriptor(descriptor)
            }
        } catch (e: SecurityException) {
            Log.e("BleManager", "SecurityException in setCharacteristicNotification: ${e.message}")
            pendingNotificationCharacteristic = null // Clear pending state on failure
        }
    }

    private val scanCallback = object : ScanCallback() {
        // ... (existing scan logic) ...
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val iconType = getDeviceTypeSafe(context, result)
            val iconVector = getDeviceIconSafe(context, result)
            val deviceName = getDeviceNameSafe(context, result)  // only one declaration

            val deviceAddress: String = try {
                @Suppress("MissingPermission")
                result.device.address
            } catch (e: SecurityException) { "00:00:00:00:00:00" }

            // 🎯 NEW LOGIC START
            if (!seenDevices.contains(deviceAddress)) {
                seenDevices.add(deviceAddress)
                Log.d("BleManager", "New device found: $deviceName")

                // 🔒 Only send proximity alert if app is NOT in the foreground
                if (!BackgroundMessageService.isAppForeground) {
                    NotificationHelper.sendNearbyUserNotification(context, deviceName)
                    Log.d("BleManager", "Background proximity alert triggered for $deviceName")
                } else {
                    Log.d("BleManager", "App in foreground — skipping proximity alert.")
                }
            }

            val device = BleDevice(deviceName, deviceAddress, result.rssi, iconVector)
            onDeviceFound?.invoke(device)
        }


        override fun onScanFailed(errorCode: Int) {
            Log.e("BleManager", "Scan failed with error code: $errorCode")
            stopScan()
            onConnectionStateChange?.invoke(ConnectionState.ERROR, "Scan failed (Error $errorCode).")
        }
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true
    fun isHardwareSupported(): Boolean = bluetoothAdapter != null

    @Suppress("MissingPermission")
    fun startScan() {
        if (!isHardwareSupported() || !isBluetoothEnabled() || isScanActive) return

        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(android.os.ParcelUuid(BluetoothConstants.BLUTXT_SERVICE_UUID))
                .build()
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            bluetoothLeScanner?.startScan(listOf(filter), scanSettings, scanCallback)
            isScanActive = true
            Log.d("BleManager", "BLE Scan Initiated, filtering for ${BluetoothConstants.BLUTXT_SERVICE_UUID.toShortString()}")
        } catch (e: SecurityException) {
            Log.e("BleManager", "SecurityException during scan start: ${e.message}")
            onConnectionStateChange?.invoke(ConnectionState.ERROR, "Permissions required for scan.")
        }
    }

    @Suppress("MissingPermission")
    fun stopScan() {
        if (isScanActive) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.e("BleManager", "SecurityException during scan stop: ${e.message}")
            }
            isScanActive = false
            Log.d("BleManager", "BLE Scan Stopped")
        }
    }

    @Suppress("MissingPermission")
    fun connectDevice(device: BleDevice) {
        stopScan()
        onConnectionStateChange?.invoke(ConnectionState.CONNECTING, "Connecting to ${device.name}...")

        try {
            val btDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            bluetoothGatt = btDevice?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e("BleManager", "SecurityException during connectGatt: ${e.message}")
            onConnectionStateChange?.invoke(ConnectionState.ERROR, "Connection failed: Permission denied.")
        } catch (e: Exception) {
            Log.e("BleManager", "Failed to connect: ${e.message}")
            onConnectionStateChange?.invoke(ConnectionState.ERROR, "Connection failed at start.")
        }
    }

    @Suppress("MissingPermission")
    fun sendMessage(message: String): Boolean {
        val characteristic = rxCharacteristic
        val gatt = bluetoothGatt
        if (characteristic == null || gatt == null) return false

        // Append the required newline delimiter used by the iOS peer.
        val fullMessage = message + MESSAGE_DELIMITER

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = fullMessage.toByteArray(Charsets.UTF_8)

        return try {
            gatt.writeCharacteristic(characteristic)
        } catch (e: SecurityException) {
            Log.e("BleManager", "SecurityException during writeCharacteristic: ${e.message}")
            false
        }
    }

    @Suppress("MissingPermission")
    fun terminateSession(): Boolean {
        val characteristic = rxCharacteristic // Assuming the RX characteristic is used for writing to the peripheral
        val gatt = bluetoothGatt
        if (characteristic == null || gatt == null) return false

        // Since you don't have a TERMINATE WRITE characteristic, the best we can do is
        // gracefully close the connection and log it as a local termination.
        Log.i("BleManager", "Locally terminating session and disconnecting.")
        onConnectionStateChange?.invoke(ConnectionState.DISCONNECTED, "Session terminated locally.")
        closeGatt()
        return true
    }
    fun closeGatt() {
        try {
            @Suppress("MissingPermission")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            Log.e("BleManager", "SecurityException during closeGatt cleanup: ${e.message}")
        }
        bluetoothGatt = null
        rxCharacteristic = null
        messageBuffer = "" // Reset buffer on disconnect
    }
}

// ----------------------------------------------------------------------------------
// 6. MainActivity and Composable (UI)
// ----------------------------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluTxtTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BleScannerScreen(modifier = Modifier.padding(innerPadding))
                    NotificationHelper.createNotificationChannel(this)
                }
            }
        }
    }

    // 💥 NEW: Set isAppForeground = true when the app becomes visible 💥
    override fun onStart() {
        super.onStart()
        // Accessing the real companion object from the separate BackgroundMessageService.kt
        BackgroundMessageService.Companion.isAppForeground = true
        Log.d("AppLifecycle", "MainActivity onStart: isAppForeground = true")
    }

    // 💥 NEW: Set isAppForeground = false when the app is no longer visible 💥
    override fun onStop() {
        super.onStop()
        // Accessing the real companion object from the separate BackgroundMessageService.kt
        BackgroundMessageService.Companion.isAppForeground = false
        Log.d("AppLifecycle", "MainActivity onStop: isAppForeground = false")
    }
}

@Composable
fun BleScannerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bleManager = remember {
        BleManagerProvider.instance ?: BleManager(context).also {
            BleManagerProvider.instance = it
        }
    }

    var scanStatus by remember { mutableStateOf("Initializing...") }
    // State that controls which screen is visible
    var connectionState by remember { mutableStateOf(ConnectionState.IDLE) }

    val devicesMap = remember { mutableStateMapOf<String, BleDevice>() }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var messageInput by remember { mutableStateOf("") }

    // 💥 NEW: Service Intent for starting/stopping the Foreground Service
    val serviceIntent = remember { Intent(context, BackgroundMessageService::class.java) }


    // 💥 NEW: Logic to START/STOP the BackgroundMessageService based on connection state 💥
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            // Service is required for background communication
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d("ServiceManager", "Started BackgroundMessageService.")
        } else if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
            // Stop the service when the connection is lost/ended
            context.stopService(serviceIntent)
            Log.d("ServiceManager", "Stopped BackgroundMessageService due to disconnection/error.")
        }
    }


    LaunchedEffect(bleManager) {
        if (!bleManager.isHardwareSupported()) {
            scanStatus = "Error: Bluetooth Hardware not supported."
        }

        bleManager.onConnectionStateChange = { state, statusMessage ->
            connectionState = state
            scanStatus = statusMessage
            if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
                devicesMap.clear()
            }
        }
        bleManager.onDeviceFound = { device ->
            devicesMap[device.address] = device
        }
        bleManager.onMessageReceived = { message ->
            chatMessages.add(message) // Keep updating the UI

            // Send the message to the background service for notifications
            val intent = Intent(context, BackgroundMessageService::class.java).apply {
                action = ACTION_NEW_MESSAGE
                putExtra(EXTRA_MESSAGE_CONTENT, message.message)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

    }

    val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.all { it.value }
        scanStatus = if (granted) {
            if (bleManager.isBluetoothEnabled()) "Permissions granted. Ready to scan." else "Permissions granted. Please enable Bluetooth."
        } else {
            "Permissions denied. Cannot scan."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i("Permissions", "POST_NOTIFICATIONS granted. Notifications enabled.")
        } else {
            Log.w("Permissions", "POST_NOTIFICATIONS denied. No incoming message alerts will be shown.")
        }
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bleManager.isBluetoothEnabled()) {
            scanStatus = "Bluetooth enabled. Tap 'Start Scan' to begin."
        } else {
            scanStatus = "Bluetooth must be enabled to scan."
        }
    }

    LaunchedEffect(Unit) {
        if (bleManager.isHardwareSupported()) {
            val allGranted = requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            // --- Existing Bluetooth/Location Permission Logic ---
            if (!allGranted) {
                permissionsLauncher.launch(requiredPermissions)
            } else if (!bleManager.isBluetoothEnabled()) {
                scanStatus = "Permissions granted. Please enable Bluetooth."
            } else {
                scanStatus = "Ready to scan."
            }

            // --- ADD NEW NOTIFICATION PERMISSION CHECK HERE ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Tiramisu is API 33
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED) {

                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // --- Actions ---
    val startBleScan = {
        val hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            permissionsLauncher.launch(requiredPermissions)
        } else if (!bleManager.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            devicesMap.clear()
            bleManager.startScan()
            scanStatus = "Scanning for service ${BluetoothConstants.BLUTXT_SERVICE_UUID.toShortString()}..."
        }
    }

    val stopBleScan = {
        bleManager.stopScan()
        scanStatus = "Scan stopped. Found ${devicesMap.size} devices."
    }

    val sendMessageAction: () -> Unit = {
        if (messageInput.isNotBlank()) {
            if (bleManager.sendMessage(messageInput)) {
                chatMessages.add(ChatMessage("Me", messageInput))
                messageInput = ""
            } else {
                scanStatus = "Error: Failed to send message (Check connection/characteristic/MTU)."
            }
        }
    }

    val disconnectAction: () -> Unit = {
        // 1. Stop the GATT connection
        bleManager.closeGatt()

        // 2. Stop the Foreground Service
        context.stopService(serviceIntent)
        Log.d("ServiceManager", "Stopped BackgroundMessageService manually.")

        // 3. Reset connection state
        connectionState = ConnectionState.DISCONNECTED

        // 4. Restart scanning immediately (fresh scan)
        devicesMap.clear()
        bleManager.startScan()
        scanStatus = "Reconnected to main menu. Scanning for devices..."
    }


    // --- UI Structure ---
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "BluTxt Messenger",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "Status: $scanStatus",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // --- Core UI Content (Takes remaining space) ---
        if (connectionState != ConnectionState.CONNECTED) {
            ConnectionScreen(
                connectionState = connectionState,
                devicesMap = devicesMap,
                onStartScan = startBleScan,
                onStopScan = stopBleScan,
                onConnect = bleManager::connectDevice,
                onDisconnect = disconnectAction,
                modifier = Modifier.weight(1f)
            )
        } else {
            ChatScreen(
                messages = chatMessages,
                messageInput = messageInput,
                onMessageInput = { messageInput = it },
                onSendMessage = sendMessageAction,
                onDisconnect = disconnectAction,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ----------------------------------------------------------------------------------
// 7. Composable Sub-Components
// ----------------------------------------------------------------------------------

@Composable
fun DeviceCard(
    device: BleDevice,
    isConnecting: Boolean,
    onConnect: (BleDevice) -> Unit
) {
    val rssiColor = when {
        device.rssi > -60 -> Color(0xFF4CAF50)
        device.rssi > -80 -> Color(0xFFFFEB3B)
        else -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { onConnect(device) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        enabled = !isConnecting
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ Use the new BLE-advertisement-based icon
            Icon(
                device.icon,
                contentDescription = "${device.name} Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Address: ${device.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.Call,
                contentDescription = "Signal Strength",
                tint = rssiColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium.copy(color = rssiColor)
            )
        }
    }
}



@Composable
fun ConnectionScreen(
    connectionState: ConnectionState,
    devicesMap: Map<String, BleDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isScanning = connectionState == ConnectionState.SCANNING
    val isConnecting = connectionState == ConnectionState.CONNECTING
    // Use IDLE/DISCONNECTED/ERROR to determine if scan button should be available
    val isIdleOrDisconnected = connectionState == ConnectionState.IDLE ||
            connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR


    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStartScan,
                modifier = Modifier.weight(1f),
                enabled = !isScanning && isIdleOrDisconnected && !isConnecting
            ) {
                Text("Start Scan")
            }
            Button(
                // This button handles both 'Stop Scan' and 'Cancel/Disconnect' depending on state
                onClick = if (isScanning) onStopScan else onDisconnect,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = isScanning || isConnecting
            ) {
                Text(if (isScanning) "Stop Scan" else "Cancel/Disconnect")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()

        Text(
            "Found Devices (${devicesMap.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )


        val deviceList = devicesMap.values.sortedByDescending { it.rssi }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 8.dp)
        ) {

            if (deviceList.isEmpty()) {
                item {
                    val placeholderMessage = when {
                        isScanning -> "Scanning for devices..."
                        isConnecting -> "Waiting for connection to complete..."
                        else -> "Tap 'Start Scan' to search for BluTxt users."
                    }
                    Text(
                        placeholderMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(deviceList, key = { it.address }) { device ->
                DeviceCard(
                    device = device,
                    isConnecting = isConnecting,
                    onConnect = onConnect
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    messageInput: String,
    onMessageInput: (String) -> Unit,
    onSendMessage: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // DYNAMIC PADDING LOGIC
    val isKeyboardVisible = WindowInsets.isImeVisible

    // Set a clearance value: 72.dp when keyboard is up, 8.dp when down.
    val topMessageClearance = if (isKeyboardVisible) 72.dp else 8.dp


    // Auto-scroll when a new message is added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Add clickable modifier to dismiss keyboard when clicking the background
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            },
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Connected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Button(
                onClick = onDisconnect, // This now triggers the return to the scanning screen
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text("Disconnect")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            // Apply the dynamic top padding here
            contentPadding = PaddingValues(bottom = 8.dp, top = topMessageClearance)
        ) {
            items(messages.reversed(), key = { it.timestamp }) { message ->
                MessageBubble(message = message)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = onMessageInput,
                label = { Text("Message") },
                modifier = Modifier.weight(1f),
                maxLines = 3
            )
            Button(
                onClick = onSendMessage,
                enabled = messageInput.isNotBlank(),
                contentPadding = PaddingValues(12.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isMe = message.sender == "Me"
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isClicked by remember { mutableStateOf(false) }

    val defaultColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val flashColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    val animatedColor by animateColorAsState(
        targetValue = if (isClicked) flashColor else defaultColor,
        animationSpec = tween(durationMillis = 150),
        label = "BubbleColorAnimation"
    )

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormat.format(message.timestamp)

    val copyToClipboard: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("BluTxt Message", message.message)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()

        coroutineScope.launch {
            isClicked = true
            delay(100)
            isClicked = false
        }
    }

    // Column wrapping timestamp + bubble, width wraps content
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth() // width = bubble width
                .clickable(
                    onClick = copyToClipboard,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ),
            horizontalAlignment = Alignment.End // timestamp aligned to bubble's end
        ) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp

                ),
                modifier = Modifier.padding(bottom = 2.dp)
            )

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = animatedColor,
                    contentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text = message.message,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}



// ----------------------------------------------------------------------------------
// 8. Extensions
// ----------------------------------------------------------------------------------

fun UUID.toShortString(): String {
    return this.toString().substring(4, 8).uppercase()
}
