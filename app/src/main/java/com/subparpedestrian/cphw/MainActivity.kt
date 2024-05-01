package com.subparpedestrian.cphw

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ParcelUuid
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.subparpedestrian.cphw.ble.ConnectionEventListener
import com.subparpedestrian.cphw.ble.ConnectionManager
import com.subparpedestrian.cphw.ble.findCharacteristic
import com.subparpedestrian.cphw.ble.toHexString
import com.subparpedestrian.cphw.ui.theme.CPHWTheme
import timber.log.Timber
import java.util.UUID

private const val PERMISSION_REQUEST_CODE = 1

private const val SERIAL_NUMBER_UUID = "52756265-6e43-6167-6e69-654350485105"
private const val RIDE_MODE_UUID = "52756265-6e43-6167-6e69-65435048500A"


class MainActivity : ComponentActivity() {
    /*******************************************
     * Properties
     *******************************************/

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setScanMode(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
        .build()

    private val scanFilter = ScanFilter.Builder().setServiceUuid(
        ParcelUuid.fromString("52756265-6e43-6167-6e69-654350485000")
    ).build()

    private var isScanning = false

    private val bluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Timber.i("Bluetooth is enabled, good to go")
        } else {
            Timber.e("User dismissed or denied Bluetooth prompt")
            promptEnableBluetooth()
        }
    }


    private var turbo = listOf(
        0x7f,
        0x55,
        0x00,
        0x7d,
        0x00,
        0x00,
        0x00,
        0x00,
        0x80,
        0x40,
        0x40,
        0xff,
        0xd5,
        0x00,
        0x00,
        0x00,
        0x00,
        0x01
    )
    private var standard = listOf(
        0x40,
        0x40,
        0x1e,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x7f,
        0x7f,
        0x7f,
        0x7f,
        0x7f,
        0x00,
        0x00,
        0x00,
        0x00,
        0x01
    )
    private var eco = listOf(
        0x20,
        0x20,
        0x0a,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x7f,
        0x7f,
        0x7f,
        0x7f,
        0x7f,
        0x00,
        0x00,
        0x00,
        0x00,
        0x01
    )

    private lateinit var activeGatt: BluetoothGatt
    private val connectionState = mutableStateOf(false)

    /****
     * composable
     */

    @Composable
    private fun ConnectionText() {
        val c by connectionState
        var text = "disconnected"
        if (c) {
            text = "connected"
        }
        Text(text)
    }

    /*******************************************
     * Activity function overrides
     *******************************************/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
//        }

        setContent {
            CPHWTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                )
                {
                    Column {
                        NormalButton(getString(R.string.connect)) { startBleScan() }
                        NormalButton(getString(R.string.eco)) { setRideMode(eco) }
                        NormalButton(getString(R.string.standard)) { setRideMode(standard) }
                        NormalButton(getString(R.string.turbo)) { setRideMode(turbo) }
                        ConnectionText()
                    }
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        ConnectionManager.registerListener(connectionEventListener)
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) {
            stopBleScan()
        }
        ConnectionManager.unregisterListener(connectionEventListener)
    }

    /*******************************************
     * Private functions
     *******************************************/

    private fun promptEnableBluetooth() {
        if (hasRequiredBluetoothPermissions() && !bluetoothAdapter.isEnabled) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                bluetoothEnablingResult.launch(this)
            }
        }
    }

    @SuppressLint("MissingPermission, NotifyDataSetChanged") // Check performed inside extension fun
    private fun startBleScan() {
        if (!hasRequiredBluetoothPermissions()) {
            requestRelevantBluetoothPermissions(PERMISSION_REQUEST_CODE)
        } else {
//            scanResults.clear()
//            scanResultAdapter.notifyDataSetChanged()
            val filters: MutableList<ScanFilter> = arrayListOf()
            filters.add(scanFilter)

            bleScanner.startScan(filters, scanSettings, scanCallback)
            isScanning = true
        }
    }

    @SuppressLint("MissingPermission") // Check performed inside extension fun
    private fun stopBleScan() {
        if (hasRequiredBluetoothPermissions()) {
            bleScanner.stopScan(scanCallback)
            isScanning = false
        }
    }

    private fun getValueFromFile(fileName: String, searchKey: String): String? {
        var value: String? = null
        assets.open(fileName).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split(",")
                if (parts.size == 2 && parts[0].trim() == searchKey) {
                    value = parts[1].trim()
                    return@forEach
                }
            }
        }
        return value
    }

    private fun setRideMode(mode: List<Int>) {
        val characteristic = activeGatt.findCharacteristic(UUID.fromString(RIDE_MODE_UUID))
        if (characteristic != null) {
            var modeData = ByteArray(mode.size) { pos -> mode[pos].toByte() }
            modeData += crc16(modeData)
            Timber.i("Sending ride mode data: {${modeData.toHexString()}")
            ConnectionManager.writeCharacteristic(activeGatt.device, characteristic, modeData)
        }
    }

    /*******************************************
     * Callback bodies
     *******************************************/

    // If we're getting a scan result, we already have the relevant permission(s)
    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopBleScan()
            with(result.device) {
                Timber.w("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                Timber.w("Connecting to $address")
                ConnectionManager.connect(this, this@MainActivity)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = { gatt ->
                activeGatt = gatt
                connectionState.value = true
                val characteristic = gatt.findCharacteristic(
                    UUID.fromString(SERIAL_NUMBER_UUID)
                )
                if (characteristic != null) {
                    ConnectionManager.readCharacteristic(gatt.device, characteristic)
                }
            }
            @SuppressLint("MissingPermission")
            onDisconnect = {
                connectionState.value = false
                val deviceName = if (hasRequiredBluetoothPermissions()) {
                    it.name
                } else {
                    "device"
                }
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.disconnected)
                        .setMessage(
                            getString(
                                R.string.disconnected_or_unable_to_connect_to_device,
                                deviceName
                            )
                        )
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }

            onCharacteristicRead = { device, characteristic, value ->
                Timber.i("Read from ${characteristic.uuid}: ${value.decodeToString()}")
                if (characteristic.uuid.toString() == SERIAL_NUMBER_UUID) {
                    val passcode =
                        getValueFromFile(
                            "passcodes.csv",
                            value.sliceArray(0 until 14).decodeToString()
                        )
                    if (passcode == null) {
                        Timber.w("No passcode found.")
                    } else {
                        val passcodeCharacteristic = BluetoothGattCharacteristic(
                            UUID.fromString("52756265-6e43-6167-6e69-654350485101"),
                            BluetoothGattCharacteristic.PROPERTY_WRITE,
                            BluetoothGattCharacteristic.PERMISSION_WRITE
                        )
                        ConnectionManager.writeCharacteristic(
                            device,
                            passcodeCharacteristic,
                            passcode.hexToByteArray()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NormalButton(text: String, onButtonClick: () -> Unit) {
    Button(onClick = onButtonClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Preview
@Composable
fun Preview() {
    CPHWTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        )
        {
            Column {
                NormalButton("connect", fun() {})
                NormalButton("eco", fun() {})
                NormalButton("standard", fun() {})
                NormalButton("turbo", fun() {})
                Text("Disconnected")
            }
        }
    }
}