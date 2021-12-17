package com.iomt.virtual

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.BluetoothProfile.GATT
import android.bluetooth.le.*
import android.icu.util.TimeUnit
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

        gattServer = bluetoothManager.openGattServer(this, gattServerCallback).also {
            val heartRateService = BluetoothGattService(heartRateServiceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val heartRateCharacteristic = BluetoothGattCharacteristic(
                heartRateCharacteristicUUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            heartRateService.addCharacteristic(heartRateCharacteristic)
            it.addService(heartRateService)
        }

        send_button.setOnClickListener {
            isSending = !isSending
            if (isSending) {
                checkRequirements()
                send_button.text = getString(R.string.stop_sending)
                sendData()
            } else {
                send_button.text = getString(R.string.start_sending)
            }
        }

        delay_bar.setOnSeekBarChangeListener (
            object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                    delay_label.text = p1.toString()
                    delay = p1.toLong()
                }

                override fun onStartTrackingTouch(p0: SeekBar?) { }

                override fun onStopTrackingTouch(p0: SeekBar?) { }
            }
        )
        delay = ((delay_bar.max - delay_bar.min) / 2).toLong()
        delay_bar.progress = (delay_bar.max - delay_bar.min) / 2

        startAdvertising()
    }

    private val heartRateServiceUUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val heartRateCharacteristicUUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

    private var isSending: Boolean = false
    private var connectedDevice: BluetoothDevice? = null
    private var delay: Long = 0

    private var bytesToSend = byteArrayOf()

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser

    private val gattServerCallback: BluetoothGattServerCallback = object: BluetoothGattServerCallback() {
        private val debugTag: String = "GattServer"
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.i(debugTag, "onConnectionStateChange $status -> $newState")
            when(newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    device_name.text = device?.name ?: getString(R.string.unknown)
                    mac_address.text = device?.address ?: getString(R.string.unknown)
                    connectedDevice = device
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    device_name.text = getString(R.string.none)
                    mac_address.text = getString(R.string.none)
                    connectedDevice = null
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            Log.d(debugTag, "Gatt server service was added.")
            super.onServiceAdded(status, service)
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d(debugTag, "READ called onCharacteristicReadRequest ${characteristic?.uuid ?: "UNDEFINED"}")
            if (characteristic?.uuid == heartRateCharacteristicUUID) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, bytesToSend)
            }
        }
    }

    private val advertiseCallback: AdvertiseCallback = object: AdvertiseCallback() {
        private val debugTag = "Advertiser"
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            // super.onStartSuccess(settingsInEffect)
            Log.i(debugTag, "Peripheral Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            // super.onStartFailure(errorCode)
            Log.e(debugTag, "Peripheral Advertise Failed: $errorCode")
        }
    }

    private lateinit var gattServer: BluetoothGattServer

    // todo
    private fun checkRequirements(): Boolean {
        if (!bluetoothAdapter.isEnabled) {
            val enableByIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableByIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
            finish()
            return false
        }

        else if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No Bluetooth Low Energy Support.", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }

        else if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Toast.makeText(this, "No Advertising Support.", Toast.LENGTH_SHORT).show()
            finish()
            return false
        } else {
            return true
        }
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings
            .Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        //TxPowerLevel?

        val data = AdvertiseData
            .Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(heartRateServiceUUID))
            .build()

        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun sendData() {
        val debugTag = "DataSender"
        if (connectedDevice == null) {
            Log.w(debugTag, "No device is connected.")
            Toast.makeText(this, "No device is connected.", Toast.LENGTH_SHORT).show()
            return
        }
        var i = 0
        Log.d(debugTag, "Attempting to get characteristic.")
        val readCharacteristic = gattServer.getService(heartRateServiceUUID).getCharacteristic(heartRateCharacteristicUUID)
        Log.d(debugTag, "Characteristic got.")
        object: Thread() {
            override fun run() {
                while(isSending) {
                    readCharacteristic.value = byteArrayOf(i.toByte())
                    bytesToSend = byteArrayOf(i.toByte())
                    Log.d(debugTag, "Sending notification ${readCharacteristic.value}")
                    val isNotified = gattServer.notifyCharacteristicChanged(connectedDevice, readCharacteristic, false)
                    Log.d(debugTag, if (isNotified) { "Notification sent." } else { "Notification is not sent." })
                    ++i
                    runBlocking { delay(delay) }
                }
            }
        }.start()
    }

    /**
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }
    */

    private fun requestLocationPermission() {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return
        }
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setMessage(
            "Starting from Android 6.0, the system requires to be granted location access in order to scan for BLE devices."
        )
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                requestPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        val alert = dialogBuilder.create()
        alert.setTitle("Location permission required")
        alert.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    TODO("Not yet implemented.")
                }
            }
        }
    }
}